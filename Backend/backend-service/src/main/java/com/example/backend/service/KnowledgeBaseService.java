package com.example.backend.service;

import com.example.backend.client.DifyClient;
import com.example.backend.common.BusinessException;
import com.example.backend.common.exception.ExternalServiceException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.entity.Document;
import com.example.backend.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final DocumentMapper documentMapper;
    private final DifyClient difyClient;

    @Value("${dify.api.dataset-id:}")
    private String difyDatasetId;

    private final String uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toString() + File.separator;

    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public Map<String, Object> uploadDocument(MultipartFile file, String category, boolean force) throws IOException {
        ensureUploadDirectory();

        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (extension.isEmpty()) {
            throw new BusinessException(400, "File extension is required");
        }

        String finalFilename = resolveFilenameConflict(originalFilename, force);
        String storedFilename = UUID.randomUUID().toString() + extension;
        Path path = Paths.get(uploadDir, storedFilename);
        Files.write(path, file.getBytes());

        String content = parseDocument(path.toFile(), extension);
        int estimatedChunkCount = Math.max(content.length() / 500 + 1, 1);

        String difyDocId;
        try {
            difyDocId = difyClient.uploadFile(path.toFile(), finalFilename, difyDatasetId);
        } catch (Exception e) {
            logger.error("Failed to upload to Dify: {}", e.getMessage());
            throw new ExternalServiceException("Failed to upload document to AI knowledge base", e);
        }

        Document doc = new Document();
        doc.setTitle(finalFilename);
        doc.setFilePath(path.toString());
        doc.setCategory(category);
        doc.setFileType(extension.replace(".", "").toUpperCase());
        doc.setStatus(1);
        doc.setContent(content);
        doc.setDifyDocumentId(difyDocId);
        documentMapper.insert(doc);

        Map<String, Object> data = new HashMap<>();
        data.put("docId", doc.getId());
        data.put("chunkCount", estimatedChunkCount);
        data.put("provider", "dify");
        data.put("difyDocumentId", doc.getDifyDocumentId());
        return data;
    }

    private void ensureUploadDirectory() throws IOException {
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Failed to create upload directory: " + uploadDir);
        }
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "upload.txt";
        }
        return filename.trim();
    }

    private String getExtension(String filename) {
        int index = filename.lastIndexOf(".");
        return index >= 0 ? filename.substring(index) : "";
    }

    private String resolveFilenameConflict(String originalFilename, boolean force) {
        if (!documentMapper.existsByTitle(originalFilename)) {
            return originalFilename;
        }
        if (!force) {
            throw new BusinessException(409, "File already exists");
        }

        int dotIndex = originalFilename.lastIndexOf(".");
        String baseName = dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
        String extension = dotIndex > 0 ? originalFilename.substring(dotIndex) : "";

        int count = 1;
        String candidate = baseName + "(" + count + ")" + extension;
        while (documentMapper.existsByTitle(candidate)) {
            count++;
            candidate = baseName + "(" + count + ")" + extension;
        }
        return candidate;
    }

    private String parseDocument(File file, String extension) {
        try {
            if (".pdf".equalsIgnoreCase(extension)) {
                try (PDDocument document = PDDocument.load(file)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                }
            } else if (".docx".equalsIgnoreCase(extension)) {
                try (FileInputStream fis = new FileInputStream(file);
                     XWPFDocument document = new XWPFDocument(fis);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            } else if (".txt".equalsIgnoreCase(extension) || ".md".equalsIgnoreCase(extension)) {
                return new String(Files.readAllBytes(file.toPath()));
            }
        } catch (Exception e) {
            logger.warn("Error parsing file: {}", e.getMessage());
            return "Error parsing file: " + e.getMessage();
        }
        return "Unsupported file format";
    }

    public List<Document> listDocuments() {
        try {
            syncFromDify();
        } catch (Exception e) {
            logger.warn("Skip Dify sync before listing documents: {}", e.getMessage());
        }
        return getAllDocuments();
    }

    @Cacheable(value = "knowledgeBase", key = "'all_documents'")
    public List<Document> getAllDocuments() {
        return documentMapper.findAllByOrderByCreateTimeDesc();
    }

    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void syncFromDify() {
        if (difyDatasetId == null || difyDatasetId.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, Object> dataset = difyClient.getDataset(difyDatasetId);
            int documentCount = toInt(dataset.get("document_count"), 0);

            int limit = 100;
            int totalPages = (int) Math.ceil((double) documentCount / limit);
            if (totalPages == 0 && documentCount > 0) {
                totalPages = 1;
            }

            for (int page = 1; page <= totalPages; page++) {
                List<Map<String, Object>> difyDocs = difyClient.listDocuments(difyDatasetId, page, limit);
                for (Map<String, Object> dDoc : difyDocs) {
                    String difyId = toStringValue(dDoc.get("id"));
                    String name = toStringValue(dDoc.get("name"));

                    Document existing = documentMapper.findByDifyDocumentId(difyId);
                    boolean enabled = !dDoc.containsKey("enabled") || Boolean.TRUE.equals(dDoc.get("enabled"));

                    if (existing == null) {
                        Document newDoc = new Document();
                        newDoc.setTitle(name);
                        newDoc.setDifyDocumentId(difyId);
                        newDoc.setStatus(enabled ? 1 : 0);
                        newDoc.setCategory("synced");
                        newDoc.setFileType("UNKNOWN");
                        newDoc.setContent("");
                        documentMapper.insert(newDoc);
                    } else if (existing.getStatus() == null || existing.getStatus() != (enabled ? 1 : 0)) {
                        existing.setStatus(enabled ? 1 : 0);
                        documentMapper.update(existing);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error syncing from Dify", e);
            throw new ExternalServiceException("Failed to sync from Dify", e);
        }
    }

    public void deleteDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new ResourceNotFoundException("Document not found with id: " + id);
        }

        deleteFromDifyIfPresent(doc.getDifyDocumentId());

        if (doc.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(doc.getFilePath()));
            } catch (IOException e) {
                logger.error("Failed to delete local file: {}", e.getMessage());
                throw new RuntimeException("Failed to delete local file: " + e.getMessage(), e);
            }
        }

        try {
            documentMapper.deleteById(doc.getId());
        } catch (Exception e) {
            logger.error("Failed to delete from DB: {}", e.getMessage());
            throw new RuntimeException("Failed to delete DB record: " + e.getMessage(), e);
        }
    }

    private void deleteFromDifyIfPresent(String difyDocumentId) {
        if (difyDocumentId == null) {
            return;
        }
        try {
            difyClient.deleteDocument(difyDatasetId, difyDocumentId);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                logger.warn("Document {} not found in Dify (404), continue.", difyDocumentId);
                return;
            }
            logger.error("Failed to delete from Dify: {}", e.getMessage());
            throw new ExternalServiceException("Failed to delete from Dify knowledge base: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDocumentStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "Invalid status value. Must be 0 (disabled) or 1 (enabled)");
        }

        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new ResourceNotFoundException("Document not found with id: " + id);
        }

        Integer oldStatus = doc.getStatus();
        
        if (doc.getDifyDocumentId() != null) {
            try {
                boolean enable = status == 1;
                difyClient.updateDocumentStatus(difyDatasetId, doc.getDifyDocumentId(), enable);
            } catch (Exception e) {
                logger.error("Failed to update status in Dify: {}", e.getMessage());
                throw new ExternalServiceException("Failed to update status in Dify: " + e.getMessage(), e);
            }
        }

        doc.setStatus(status);
        try {
            documentMapper.update(doc);
        } catch (Exception e) {
            logger.error("Failed to update status in database: {}", e.getMessage());
            if (doc.getDifyDocumentId() != null && oldStatus != null) {
                try {
                    difyClient.updateDocumentStatus(difyDatasetId, doc.getDifyDocumentId(), oldStatus == 1);
                } catch (Exception rollbackEx) {
                    logger.error("Failed to rollback status in Dify after DB update failure: {}", rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Failed to update status in database: " + e.getMessage(), e);
        }
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
