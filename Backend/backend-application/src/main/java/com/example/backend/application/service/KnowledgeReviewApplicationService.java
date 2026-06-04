package com.example.backend.application.service;

import com.example.backend.common.BusinessException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.domain.knowledge.event.DocumentPublishedEvent;
import com.example.backend.domain.knowledge.model.*;
import com.example.backend.domain.knowledge.repository.*;
import com.example.backend.domain.knowledge.service.KnowledgeBasePort;
import com.example.backend.domain.shared.event.DomainEventPublisher;
import com.example.backend.domain.shared.messaging.MessageBusPort;
import com.example.backend.infrastructure.messaging.AgentBroadcaster;
import com.example.backend.infrastructure.persistence.mapper.DocumentLinkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class KnowledgeReviewApplicationService {

    @Lazy
    @Autowired
    private KnowledgeReviewApplicationService self;

    private static final String KEY_STATUS = "status";
    private static final String KEY_REVIEWED_TEXT = "reviewedText";
    private static final String KEY_OUTBOX_ID = "outboxId";
    private static final String KEY_DOCUMENT_ID = "documentId";
    private static final String KEY_TITLE = "title";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_UPDATE = "UPDATE";
    private static final String KEY_ADMIN = "admin";

    private final KnowledgeDocumentRepository documentRepository;
    private final OcrSegmentRepository ocrSegmentRepository;
    private final KnowledgeOutboxRepository outboxRepository;
    private final KnowledgeRevisionLogRepository revisionLogRepository;
    private final KnowledgeBasePort knowledgeBasePort;
    private final DomainEventPublisher eventPublisher;
    private final MessageBusPort messageBusPort;
    private final AgentBroadcaster agentBroadcaster;
    private final DocumentLinkMapper documentLinkMapper;
    private final ObjectMapper objectMapper;
    private final com.example.backend.infrastructure.es.EsDocumentIndexService esIndexService;
    private final DocumentPreviewService previewService;
    private final KnowledgeCategoryRepository categoryRepository;
    private final OcrProcessService ocrProcessService;

    @org.springframework.beans.factory.annotation.Value("${dify.knowledge.dataset-id:}")
    private String difyDatasetId;

    public KnowledgeReviewApplicationService(KnowledgeDocumentRepository documentRepository,
                                              OcrSegmentRepository ocrSegmentRepository,
                                              KnowledgeOutboxRepository outboxRepository,
                                              KnowledgeRevisionLogRepository revisionLogRepository,
                                              KnowledgeBasePort knowledgeBasePort,
                                              DomainEventPublisher eventPublisher,
                                              MessageBusPort messageBusPort,
                                              AgentBroadcaster agentBroadcaster,
                                              DocumentLinkMapper documentLinkMapper,
                                              ObjectMapper objectMapper,
                                              com.example.backend.infrastructure.es.EsDocumentIndexService esIndexService,
                                              DocumentPreviewService previewService,
                                              KnowledgeCategoryRepository categoryRepository,
                                              OcrProcessService ocrProcessService) {
        this.documentRepository = documentRepository;
        this.ocrSegmentRepository = ocrSegmentRepository;
        this.outboxRepository = outboxRepository;
        this.revisionLogRepository = revisionLogRepository;
        this.knowledgeBasePort = knowledgeBasePort;
        this.eventPublisher = eventPublisher;
        this.messageBusPort = messageBusPort;
        this.agentBroadcaster = agentBroadcaster;
        this.documentLinkMapper = documentLinkMapper;
        this.objectMapper = objectMapper;
        this.esIndexService = esIndexService;
        this.previewService = previewService;
        this.categoryRepository = categoryRepository;
        this.ocrProcessService = ocrProcessService;
    }

    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public Map<String, Object> uploadAndOcr(MultipartFile file, String category) throws IOException {
        return ocrProcessService.uploadAndOcr(file, category);
    }

    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public List<Map<String, Object>> uploadBatch(List<MultipartFile> files, String category) throws IOException {
        return ocrProcessService.uploadBatch(files, category);
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public Map<String, Object> submitReview(Long documentId, List<Map<String, Object>> reviewedSegments,
                                             String reviewedBy) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        String finalContent = buildFinalContent(reviewedSegments);
        String tocJson = extractToc(finalContent);

        document.setContent(finalContent);
        document.setTocJson(tocJson);
        document.setReviewedBy(reviewedBy);
        document.setReviewedAt(LocalDateTime.now());
        if (document.getEnabled() == null) {
            document.setEnabled(true);
        }
        document.setStatus(KnowledgeDocument.STATUS_PUBLISHED);
        document.setPublishedAt(LocalDateTime.now());
        document.incrementVersion();

        try {
            String previewPdfPath = previewService.generatePreviewPdf(
                    documentId, document.getOriginalFileUrl(), document.getFileType());
            document.setPreviewPdfPath(previewPdfPath);
        } catch (Exception e) {
            log.warn("Preview PDF generation failed for document {}: {}", documentId, e.getMessage());
        }

        documentRepository.save(document);
        esIndexService.indexDocument(document);

        for (Map<String, Object> seg : reviewedSegments) {
            Long segId = toLong(seg.get("id"));
            String status = (String) seg.get(KEY_STATUS);
            String reviewedText = (String) seg.get(KEY_REVIEWED_TEXT);
            ocrSegmentRepository.updateStatus(segId, documentId, status, reviewedText);
        }

        KnowledgeOutbox outbox = new KnowledgeOutbox();
        outbox.setDocumentId(documentId);
        outbox.setEventType(KnowledgeOutbox.EVENT_UPLOAD);
        outbox.setPayload(buildOutboxPayload(document));
        outbox.setStatus(KnowledgeOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetry(5);
        outbox.setCreatedAt(LocalDateTime.now());
        outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(10));
        outboxRepository.save(outbox);

        try {
            Map<String, Object> mqPayload = new LinkedHashMap<>();
            mqPayload.put(KEY_OUTBOX_ID, outbox.getId());
            mqPayload.put(KEY_DOCUMENT_ID, documentId);
            mqPayload.put(KEY_TITLE, document.getTitle());
            mqPayload.put(KEY_CONTENT, document.getContent());
            messageBusPort.send("knowledge.dify", mqPayload);
        } catch (Exception e) {
            log.warn("MQ send failed for document {}: {}, will fallback to outbox", documentId, e.getMessage());
        }

        ocrProcessService.writeRevisionLog(documentId, KnowledgeRevisionLog.TYPE_PUBLISH, "content,toc_json",
                null, "published", reviewedBy);

        eventPublisher.publish(new DocumentPublishedEvent(documentId, document.getTitle(),
                document.getCategory(), document.getDifyDocumentId()));

        agentBroadcaster.broadcast(buildUpdatePayload(documentId, "CREATE"));

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_DOCUMENT_ID, documentId);
        result.put(KEY_STATUS, document.getStatus());
        result.put(KEY_OUTBOX_ID, outbox.getId());
        return result;
    }

    public Map<String, Object> buildUpdatePayload(Long documentId, String action) {
        KnowledgeDocument document = getDocumentDetail(documentId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "knowledge_update");
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("action", action);
        inner.put("document_id", String.valueOf(document.getId()));
        inner.put(KEY_TITLE, document.getTitle());
        inner.put("version", document.getVersion());
        inner.put("timestamp", document.getUpdateTime() != null
                ? document.getUpdateTime().toString() : LocalDateTime.now().toString());
        payload.put("payload", inner);
        return payload;
    }

    public List<Map<String, Object>> getDocumentLinks(Long documentId) {
        return documentLinkMapper.findBySourceDocId(documentId);
    }

    public Map<String, Object> getVersionDiff(Long documentId, Long revisionAId, Long revisionBId) {
        KnowledgeRevisionLog logA = revisionLogRepository.findById(revisionAId).orElse(null);
        KnowledgeRevisionLog logB = revisionLogRepository.findById(revisionBId).orElse(null);

        String textA;
        if (logA != null && logA.getNewValue() != null) {
            textA = logA.getNewValue();
        } else if (logA != null) {
            textA = logA.getOldValue();
        } else {
            textA = "";
        }

        String textB;
        if (logB != null && logB.getNewValue() != null) {
            textB = logB.getNewValue();
        } else if (logB != null) {
            textB = logB.getOldValue();
        } else {
            textB = "";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_DOCUMENT_ID, documentId);
        result.put("revisionA", logA != null ? formatRevisionInfo(logA) : null);
        result.put("revisionB", logB != null ? formatRevisionInfo(logB) : null);
        result.put("contentA", textA);
        result.put("contentB", textB);
        return result;
    }

    private Map<String, Object> formatRevisionInfo(KnowledgeRevisionLog log) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", log.getId());
        info.put("changeType", log.getChangeType());
        info.put("changedBy", log.getChangedBy());
        info.put("changedAt", log.getChangedAt() != null ? log.getChangedAt().toString() : null);
        return info;
    }

    public List<String> listCategories() {
        return categoryRepository.findAllNames();
    }

    public List<Map<String, Object>> getCategoryStats() {
        return categoryRepository.categoryStats();
    }

    public String createCategory(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "分类名不能为空");
        }
        if (categoryRepository.existsByName(name)) {
            throw new BusinessException(400, "分类已存在");
        }
        categoryRepository.insert(name.trim());
        return name.trim();
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void deleteCategory(String categoryName) {
        categoryRepository.deleteByName(categoryName);
        documentRepository.clearCategory(categoryName);
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void updateDocumentCategory(Long documentId, String category) {
        KnowledgeDocument document = getDocumentDetail(documentId);
        String oldCategory = document.getCategory();
        document.setCategory(category);
        documentRepository.save(document);
        esIndexService.indexDocument(document);
        ocrProcessService.writeRevisionLog(documentId, KEY_UPDATE, "category", oldCategory, category, KEY_ADMIN);
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void updateDocumentTags(Long documentId, String tags) {
        KnowledgeDocument document = getDocumentDetail(documentId);
        String oldTags = document.getTags();
        document.setTags(tags);
        documentRepository.save(document);
        esIndexService.indexDocument(document);
        ocrProcessService.writeRevisionLog(documentId, KEY_UPDATE, "tags", oldTags, tags, KEY_ADMIN);
    }

    public KnowledgeDocument getDocumentDetail(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    public Map<String, Object> getReviewData(Long documentId) {
        KnowledgeDocument document = getDocumentDetail(documentId);
        List<OcrSegment> segments = ocrSegmentRepository.findByDocumentIdOrderBySegmentIndex(documentId);

        Map<String, Object> result = new HashMap<>();
        result.put("document", document);
        result.put("segments", segments);
        result.put("totalSegments", segments.size());
        result.put("uncertainCount", segments.stream().filter(OcrSegment::isUncertain).count());
        result.put("confirmedCount", segments.stream().filter(OcrSegment::isConfirmed).count());
        result.put("pendingCount", segments.stream().filter(s -> OcrSegment.STATUS_PENDING.equals(s.getStatus())).count());
        result.put("originalFileUrl", "/api/knowledge/file/" + documentId);
        return result;
    }

    public String getOriginalFilePath(Long documentId) {
        return documentRepository.findById(documentId)
                .map(KnowledgeDocument::getOriginalFileUrl)
                .orElse(null);
    }

    public List<KnowledgeDocument> listDocuments(String status) {
        if (status != null && !status.isEmpty()) {
            return documentRepository.findByStatus(status);
        }
        List<String> activeStatuses = List.of(
                KnowledgeDocument.STATUS_PUBLISHED, KnowledgeDocument.STATUS_PUBLISHING);
        return documentRepository.findByStatusIn(activeStatuses);
    }

    public List<KnowledgeDocument> listPendingReview(String keyword, String category) {
        if ((keyword != null && !keyword.isBlank()) || (category != null && !category.isBlank())) {
            return documentRepository.findPendingReviewFiltered(
                    (keyword != null && !keyword.isBlank()) ? keyword : null,
                    (category != null && !category.isBlank()) ? category : null);
        }
        return documentRepository.findByStatus(KnowledgeDocument.STATUS_PENDING_REVIEW);
    }

    public List<Map<String, Object>> getRevisionHistory(Long documentId) {
        return revisionLogRepository.findByDocumentIdOrderByChangedAtDesc(documentId)
                .stream().map(log -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", log.getId());
                    m.put("changeType", log.getChangeType());
                    m.put("changedFields", log.getChangedFields());
                    m.put("changedBy", log.getChangedBy());
                    m.put("changedAt", log.getChangedAt());
                    return m;
                }).toList();
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void archiveDocument(Long documentId, String reason) {
        KnowledgeDocument document = getDocumentDetail(documentId);
        document.archive(reason);
        documentRepository.save(document);
        ocrProcessService.writeRevisionLog(documentId, KnowledgeRevisionLog.TYPE_ARCHIVE, KEY_STATUS,
                KnowledgeDocument.STATUS_PUBLISHED, KnowledgeDocument.STATUS_ARCHIVED, "system");
        agentBroadcaster.broadcast(buildUpdatePayload(documentId, "ARCHIVE"));
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public int batchArchive(List<Long> documentIds, String reason) {
        int successCount = 0;
        for (Long documentId : documentIds) {
            self.archiveDocument(documentId, reason);
            successCount++;
        }
        return successCount;
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public int batchDelete(List<Long> documentIds) {
        int successCount = 0;
        for (Long documentId : documentIds) {
            self.deleteDocument(documentId);
            successCount++;
        }
        return successCount;
    }

    @Transactional
    public void retryDifySync(Long documentId) {
        KnowledgeDocument document = getDocumentDetail(documentId);
        document.markSyncing();
        documentRepository.save(document);

        KnowledgeOutbox outbox = new KnowledgeOutbox();
        outbox.setDocumentId(documentId);
        outbox.setEventType(KnowledgeOutbox.EVENT_UPLOAD);
        outbox.setPayload(buildOutboxPayload(document));
        outbox.setStatus(KnowledgeOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetry(5);
        outbox.setCreatedAt(LocalDateTime.now());
        outbox.setNextRetryAt(LocalDateTime.now());
        outboxRepository.save(outbox);

        Map<String, Object> mqPayload = new LinkedHashMap<>();
        mqPayload.put(KEY_OUTBOX_ID, outbox.getId());
        mqPayload.put(KEY_DOCUMENT_ID, documentId);
        mqPayload.put(KEY_TITLE, document.getTitle());
        mqPayload.put(KEY_CONTENT, document.getContent());
        messageBusPort.send("knowledge.dify", mqPayload);
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void deleteDocument(Long documentId) {
        KnowledgeDocument document = getDocumentDetail(documentId);

        if (document.getOriginalFileUrl() != null) {
            try {
                Files.deleteIfExists(Paths.get(document.getOriginalFileUrl()));
            } catch (IOException e) {
                log.warn("Failed to delete local file for document {}: {}", documentId, e.getMessage());
            }
        }

        previewService.deletePreviewPdf(documentId);

        if (document.getDifyDocumentId() != null) {
            try {
                knowledgeBasePort.deleteDocument(difyDatasetId, document.getDifyDocumentId());
            } catch (Exception e) {
                log.warn("Failed to delete Dify document for document {}: {}", documentId, e.getMessage());
            }
        }

        ocrSegmentRepository.deleteByDocumentId(documentId);
        revisionLogRepository.deleteByDocumentId(documentId);
        outboxRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
        esIndexService.deleteDocument(documentId);
        log.info("Document {} deleted successfully", documentId);
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void toggleDocumentEnabled(Long documentId, boolean enabled) {
        KnowledgeDocument document = getDocumentDetail(documentId);
        document.setEnabled(enabled);
        documentRepository.save(document);
        esIndexService.indexDocument(document);

        if (document.getDifyDocumentId() != null) {
            try {
                knowledgeBasePort.updateDocumentStatus(difyDatasetId, document.getDifyDocumentId(), enabled);
                log.info("Dify enabled status synced for doc {}: enabled={}", documentId, enabled);
            } catch (Exception e) {
                log.warn("Dify sync failed for doc {}: {}. Local enabled kept, will retry on next toggle.", documentId, e.getMessage());
            }
        }

        ocrProcessService.writeRevisionLog(documentId, KEY_UPDATE, "enabled", String.valueOf(!enabled), String.valueOf(enabled), KEY_ADMIN);
        log.info("Document {} enabled status toggled to {}", documentId, enabled);
    }

    @Transactional
    public int regenerateAllToc() {
        List<KnowledgeDocument> docs = documentRepository.findByStatus("PUBLISHED");
        int count = 0;
        for (KnowledgeDocument doc : docs) {
            String toc = extractToc(doc.getContent());
            if (!toc.equals(doc.getTocJson())) {
                doc.setTocJson(toc);
                documentRepository.save(doc);
                count++;
            }
        }
        return count;
    }

    public int reindexAllToEs() {
        List<String> visibleStatuses = List.of(
                KnowledgeDocument.STATUS_PUBLISHED, KnowledgeDocument.STATUS_PUBLISHING);
        List<KnowledgeDocument> docs = documentRepository.findByStatusIn(visibleStatuses);
        return esIndexService.reindexAll(docs);
    }

    @Transactional
    public Map<String, Object> rebuildAll() {
        int tocUpdatedCount = regenerateAllToc();
        int indexedCount = reindexAllToEs();
        esIndexService.clearCache();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tocUpdatedCount", tocUpdatedCount);
        result.put("indexedCount", indexedCount);
        return result;
    }

    public void clearEsCache() {
        esIndexService.clearCache();
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void rejectDocument(Long documentId) {
        KnowledgeDocument document = getDocumentDetail(documentId);

        if (!KnowledgeDocument.STATUS_PENDING_REVIEW.equals(document.getStatus())
                && !KnowledgeDocument.STATUS_PENDING_OCR.equals(document.getStatus())) {
            throw new BusinessException(400, "仅待审核状态的文档可以被退回");
        }

        if (document.getOriginalFileUrl() != null) {
            try {
                Files.deleteIfExists(Paths.get(document.getOriginalFileUrl()));
            } catch (IOException e) {
                log.warn("Failed to delete local file for document {}: {}", documentId, e.getMessage());
            }
        }

        previewService.deletePreviewPdf(documentId);

        ocrSegmentRepository.deleteByDocumentId(documentId);
        revisionLogRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);

        log.info("Document {} rejected and fully cleaned up", documentId);
    }

    private String buildFinalContent(List<Map<String, Object>> reviewedSegments) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> seg : reviewedSegments) {
            String status = (String) seg.get(KEY_STATUS);
            if (OcrSegment.STATUS_SKIPPED.equals(status)) continue;
            String text;
            if (OcrSegment.STATUS_REVIEWED.equals(status) && seg.containsKey(KEY_REVIEWED_TEXT)) {
                text = (String) seg.get(KEY_REVIEWED_TEXT);
            } else {
                text = (String) seg.getOrDefault("ocrText", "");
            }
            if (text != null && !text.isBlank()) {
                sb.append(text).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private String extractToc(String content) {
        if (content == null || content.isBlank()) return "[]";
        List<Map<String, Object>> toc = new ArrayList<>();
        String[] lines = content.split("\n");
        int anchorIndex = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            int level = 0;
            String title = trimmed;
            if (trimmed.startsWith("### ")) { level = 3; title = trimmed.substring(4); }
            else if (trimmed.startsWith("## ")) { level = 2; title = trimmed.substring(3); }
            else if (trimmed.startsWith("# ")) { level = 1; title = trimmed.substring(2); }
            if (level > 0) {
                anchorIndex++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("level", level);
                item.put(KEY_TITLE, title.trim());
                item.put("anchor", "section-" + anchorIndex);
                toc.add(item);
            }
        }
        try { return objectMapper.writeValueAsString(toc); }
        catch (Exception e) { return "[]"; }
    }

    private String buildOutboxPayload(KnowledgeDocument document) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(KEY_DOCUMENT_ID, document.getId());
            payload.put(KEY_TITLE, document.getTitle());
            payload.put(KEY_CONTENT, document.getContent());
            payload.put("fileType", document.getFileType());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) { return "{}"; }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
