package com.example.backend.application.service;

import com.example.backend.common.BusinessException;
import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.model.KnowledgeRevisionLog;
import com.example.backend.domain.knowledge.model.OcrSegment;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.repository.KnowledgeRevisionLogRepository;
import com.example.backend.domain.knowledge.repository.OcrSegmentRepository;
import com.example.backend.domain.shared.ocr.OcrPort;
import com.example.backend.domain.shared.ocr.OcrResult;

import com.example.backend.infrastructure.ocr.OcrProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import java.math.BigInteger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class OcrProcessService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".txt", ".md", ".docx", ".xlsx", ".pdf",
            ".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tiff", ".webp");

    private static final int PDF_TEXT_MIN_CHARS = 100;
    private static final int XLSX_MAX_SHEETS = 20;
    private static final int XLSX_MAX_ROWS = 5000;
    private static final int XLSX_MAX_COLS = 50;

    private final KnowledgeDocumentRepository documentRepository;
    private final OcrSegmentRepository ocrSegmentRepository;
    private final KnowledgeRevisionLogRepository revisionLogRepository;
    private final OcrPort ocrPort;
    private final OcrProperties ocrProperties;
    private final ObjectMapper objectMapper;
    private final DocumentPreviewService previewService;
    private final String uploadDir;

    public OcrProcessService(
            KnowledgeDocumentRepository documentRepository,
            OcrSegmentRepository ocrSegmentRepository,
            KnowledgeRevisionLogRepository revisionLogRepository,
            OcrPort ocrPort,
            OcrProperties ocrProperties,
            ObjectMapper objectMapper,
            DocumentPreviewService previewService) {
        this.documentRepository = documentRepository;
        this.ocrSegmentRepository = ocrSegmentRepository;
        this.revisionLogRepository = revisionLogRepository;
        this.ocrPort = ocrPort;
        this.ocrProperties = ocrProperties;
        this.objectMapper = objectMapper;
        this.previewService = previewService;
        this.uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toString() + File.separator;
    }

    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public Map<String, Object> uploadAndOcr(MultipartFile file, String category) throws IOException {
        ensureUploadDirectory();
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);

        if (extension.isEmpty()) {
            throw new BusinessException(400, "文件扩展名不能为空");
        }
        if (!SUPPORTED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(400, "不支持的文件格式: " + extension
                    + "，支持的格式: " + String.join(", ", SUPPORTED_EXTENSIONS));
        }

        File savedFile = saveUploadedFile(file, extension);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(originalFilename);
        document.setFileType(extension.replace(".", "").toUpperCase());
        document.setOriginalFileUrl(savedFile.getAbsolutePath());
        document.setCategory(category);
        document.setEnabled(true);
        document.setStatus(KnowledgeDocument.STATUS_PENDING_OCR);
        document.setVersion(1);
        document.setIsLatest(true);
        // 点击"上传并OCR识别"按钮即视为审核流程开始
        document.setReviewStartedAt(LocalDateTime.now());

        document = documentRepository.save(document);

        // 生成 PNG 预览图（不阻塞 OCR 流程）
        try {
            generatePreviewImage(document.getId(), savedFile, extension);
        } catch (Exception e) {
            log.warn("Preview image generation failed for {}: {}", originalFilename, e.getMessage());
        }

        List<OcrSegment> segments;
        try {
            if (".docx".equalsIgnoreCase(extension)) {
                segments = extractDocxToSegments(document.getId(), savedFile);
            } else if (isTextExtractable(extension)) {
                String extractedText = extractText(savedFile, extension);
                segments = List.of(buildTextSegment(document.getId(), 1, extractedText));
            } else if (isPdfFile(extension)) {
                segments = ocrPdfFile(document.getId(), savedFile);
            } else {
                byte[] imageBytes = Files.readAllBytes(savedFile.toPath());
                segments = ocrImage(document.getId(), 1, imageBytes);
                segments = inferOcrLayout(segments);
            }
        } catch (Exception e) {
            log.error("OCR processing failed for {}: {}", originalFilename, e.getMessage(), e);
            OcrSegment fallbackSegment = buildTextSegment(document.getId(), 1,
                    "[OCR 识别失败: " + e.getMessage() + "]");
            segments = List.of(fallbackSegment);
        }

        ocrSegmentRepository.saveBatch(segments);

        document.setStatus(KnowledgeDocument.STATUS_PENDING_REVIEW);
        document.setOcrRawJson(objectMapper.writeValueAsString(
                segments.stream().map(s -> Map.of(
                        "index", s.getSegmentIndex(),
                        "text", s.getOcrText(),
                        "confidence", s.getConfidence(),
                        "status", s.getStatus()
                )).collect(java.util.stream.Collectors.toList())
        ));
        documentRepository.save(document);

        writeRevisionLog(document.getId(), KnowledgeRevisionLog.TYPE_CREATE, "upload",
                null, document.getTitle(), "system");

        Map<String, Object> result = new HashMap<>();
        result.put("documentId", document.getId());
        result.put("title", document.getTitle());
        result.put("status", document.getStatus());
        result.put("segments", segments);
        result.put("totalSegments", segments.size());
        return result;
    }

    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public List<Map<String, Object>> uploadBatch(List<MultipartFile> files, String category) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                Map<String, Object> singleResult = uploadAndOcr(file, category);
                results.add(singleResult);
            } catch (Exception e) {
                log.error("Batch upload failed for file {}: {}", file.getOriginalFilename(), e.getMessage());
                Map<String, Object> errorEntry = new HashMap<>();
                errorEntry.put("filename", file.getOriginalFilename());
                errorEntry.put("error", e.getMessage());
                results.add(errorEntry);
            }
        }
        return results;
    }

    public void writeRevisionLog(Long documentId, String changeType, String changedFields,
                                  String oldValue, String newValue, String changedBy) {
        KnowledgeRevisionLog log = new KnowledgeRevisionLog();
        log.setDocumentId(documentId);
        log.setChangeType(changeType);
        log.setChangedFields(buildChangedFieldsJson(changedFields));
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setChangedBy(changedBy);
        revisionLogRepository.save(log);
    }

    private String buildChangedFieldsJson(String changedFields) {
        if (changedFields == null || changedFields.isBlank()) return "[]";
        return "[\"" + String.join("\",\"", changedFields.split(",")) + "\"]";
    }

    private List<OcrSegment> ocrImage(Long documentId, int startIndex, byte[] imageBytes) {
        OcrResult result = ocrPort.recognize(imageBytes, Map.of("language", ocrProperties.getDefaultLanguage()));
        List<OcrSegment> segments = new ArrayList<>();
        double threshold = ocrProperties.getConfidenceThreshold();

        if (result.getBlocks() != null && !result.getBlocks().isEmpty()) {
            int index = startIndex;
            for (OcrResult.OcrBlock block : result.getBlocks()) {
                OcrSegment seg = buildSegment(documentId, index, block.getText(),
                        block.getConfidence(), threshold,
                        block.getX(), block.getY(), block.getWidth(), block.getHeight());
                segments.add(seg);
                index++;
            }
        } else if (result.getText() != null && !result.getText().isBlank()) {
            OcrSegment seg = buildSegment(documentId, startIndex, result.getText(),
                    result.getConfidence(), threshold, 0, 0, 0, 0);
            segments.add(seg);
        }

        if (segments.isEmpty()) {
            log.warn("OCR returned empty result for documentId={}: text is blank and no word blocks found",
                    documentId);
        }
        return segments;
    }

    private List<OcrSegment> ocrPdfFile(Long documentId, File pdfFile) throws IOException {
        List<OcrSegment> allSegments = new ArrayList<>();
        try (PDDocument pdfDoc = Loader.loadPDF(pdfFile)) {
            int pageCount = pdfDoc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            int segmentIndex = 1;

            for (int page = 0; page < pageCount; page++) {
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);
                String pageText = stripper.getText(pdfDoc);

                if (pageText != null && pageText.trim().length() >= PDF_TEXT_MIN_CHARS) {
                    OcrSegment seg = buildTextSegment(documentId, segmentIndex, pageText.trim());
                    allSegments.add(seg);
                    segmentIndex++;
                } else {
                    PDFRenderer renderer = new PDFRenderer(pdfDoc);
                    BufferedImage image = renderer.renderImageWithDPI(page, 200);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", bos);
                    List<OcrSegment> pageSegments = ocrImage(documentId, segmentIndex, bos.toByteArray());
                    allSegments.addAll(pageSegments);
                    segmentIndex += pageSegments.size();
                }
            }
        }
        return allSegments;
    }

    private OcrSegment buildSegment(Long documentId, int index, String text,
                                     double confidence, double threshold,
                                     int x, int y, int w, int h) {
        OcrSegment seg = new OcrSegment();
        seg.setDocumentId(documentId);
        seg.setSegmentIndex(index);
        seg.setOcrText(text);
        seg.setConfidence(confidence);
        seg.setBoundingBox(String.format("{\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d}", x, y, w, h));
        seg.setStatus(OcrSegment.STATUS_PENDING);
        return seg;
    }

    private OcrSegment buildTextSegment(Long documentId, int index, String text) {
        OcrSegment seg = new OcrSegment();
        seg.setDocumentId(documentId);
        seg.setSegmentIndex(index);
        seg.setOcrText(text);
        seg.setConfidence(1.0);
        seg.setStatus(OcrSegment.STATUS_PENDING);
        return seg;
    }

    private File saveUploadedFile(MultipartFile file, String extension) throws IOException {
        String storedFilename = UUID.randomUUID().toString() + extension;
        Path path = Paths.get(uploadDir, storedFilename);
        Files.write(path, file.getBytes());
        return path.toFile();
    }

    private void ensureUploadDirectory() throws IOException {
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Failed to create upload directory: " + uploadDir);
        }
    }

    private String extractText(File file, String ext) {
        try {
            if (".xlsx".equalsIgnoreCase(ext)) {
                return extractXlsxText(file);
            } else if (".txt".equalsIgnoreCase(ext) || ".md".equalsIgnoreCase(ext)) {
                return new String(Files.readAllBytes(file.toPath()));
            }
        } catch (Exception e) {
            log.warn("Text extraction error: {}", e.getMessage());
        }
        return "";
    }

    private String extractXlsxText(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            int sheetCount = Math.min(workbook.getNumberOfSheets(), XLSX_MAX_SHEETS);
            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                sb.append("## ").append(sheetName).append("\n\n");

                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount >= XLSX_MAX_ROWS) {
                        sb.append("*... 超出最大行数 ").append(XLSX_MAX_ROWS).append("，已截断 ...*\n");
                        break;
                    }
                    rowCount++;
                    int maxCol = Math.min(row.getLastCellNum(), XLSX_MAX_COLS);
                    StringBuilder rowStr = new StringBuilder("|");
                    for (int c = 0; c < maxCol; c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        rowStr.append(" ").append(getCellStringValue(cell)).append(" |");
                    }
                    sb.append(rowStr).append("\n");
                    if (rowCount == 1) {
                        sb.append("|");
                        for (int c = 0; c < maxCol; c++) sb.append(" --- |");
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String getCellStringValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().replace("|", "\\|");
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toString();
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) return String.valueOf((long) val);
                return String.valueOf(val);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); } catch (Exception e) { return cell.getCellFormula(); }
            default: return "";
        }
    }

    private List<OcrSegment> extractDocxToSegments(Long documentId, File file) throws IOException {
        List<OcrSegment> segments = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            int segmentIndex = 1;
            for (org.apache.poi.xwpf.usermodel.IBodyElement elem : doc.getBodyElements()) {
                if (elem instanceof XWPFParagraph) {
                    XWPFParagraph para = (XWPFParagraph) elem;
                    String text = para.getText();
                    if (text == null || text.isBlank()) continue;
                    String formattedText = formatParagraphToMarkdown(para, text);
                    segments.add(buildTextSegment(documentId, segmentIndex++, formattedText));
                } else if (elem instanceof XWPFTable) {
                    XWPFTable table = (XWPFTable) elem;
                    String tableMd = formatTableToMarkdown(table);
                    if (!tableMd.isBlank()) {
                        segments.add(buildTextSegment(documentId, segmentIndex++, tableMd));
                    }
                }
            }
        }
        if (segments.isEmpty()) {
            segments.add(buildTextSegment(documentId, 1, ""));
        }
        return segments;
    }

    private String formatParagraphToMarkdown(XWPFParagraph para, String text) {
        String styleId = para.getStyleID();
        int headingLevel = detectHeadingLevel(styleId);
        if (headingLevel > 0) {
            return "#".repeat(headingLevel) + " " + text.trim();
        }
        int indentChars = detectIndentChars(para);
        String prefix = indentChars >= 4 ? "> " : (indentChars >= 2 ? "  " : "");
        StringBuilder formatted = new StringBuilder(prefix);
        List<XWPFRun> runs = para.getRuns();
        for (XWPFRun run : runs) {
            String runText = run.getText(0);
            if (runText == null || runText.isEmpty()) continue;
            boolean bold = run.isBold();
            boolean italic = run.isItalic();
            if (bold && italic) formatted.append("***").append(runText).append("***");
            else if (bold) formatted.append("**").append(runText).append("**");
            else if (italic) formatted.append("*").append(runText).append("*");
            else formatted.append(runText);
        }
        return formatted.toString().trim().isEmpty() ? text : formatted.toString();
    }

    private String formatTableToMarkdown(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            List<XWPFTableCell> cells = row.getTableCells();
            sb.append("|");
            for (XWPFTableCell cell : cells) {
                sb.append(" ").append(cell.getText().replace("\n", " ")).append(" |");
            }
            sb.append("\n");
            if (r == 0) {
                sb.append("|");
                for (int c = 0; c < cells.size(); c++) sb.append(" --- |");
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private int detectHeadingLevel(String styleId) {
        if (styleId == null) return 0;
        String s = styleId.toLowerCase();
        if (s.contains("heading")) {
            for (int i = 1; i <= 6; i++) if (s.contains(String.valueOf(i))) return i;
            return 1;
        }
        if (s.contains("title")) return 1;
        if (s.contains("subtitle")) return 2;
        return 0;
    }

    private int detectIndentChars(XWPFParagraph para) {
        try {
            CTInd ind = para.getCTP().getPPr() != null ? para.getCTP().getPPr().getInd() : null;
            if (ind != null && ind.getLeft() != null) {
                Object leftObj = ind.getLeft();
                int leftVal = leftObj instanceof BigInteger ? ((BigInteger) leftObj).intValue() : 0;
                return leftVal / 360;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private List<OcrSegment> inferOcrLayout(List<OcrSegment> segments) {
        if (segments.isEmpty()) return segments;
        double avgLeft = segments.stream()
                .filter(s -> s.getBoundingBox() != null)
                .mapToDouble(s -> {
                    try {
                        return Integer.parseInt(s.getBoundingBox().replaceAll("[^0-9,]", "").split(",")[0]);
                    } catch (Exception e) { return 0; }
                }).average().orElse(0);
        double indentThreshold = avgLeft * 1.8;
        for (OcrSegment seg : segments) {
            if (seg.getBoundingBox() == null) continue;
            try {
                String[] parts = seg.getBoundingBox().replaceAll("[^0-9,]", "").split(",");
                int x = Integer.parseInt(parts[0]);
                if (x >= indentThreshold) seg.setOcrText("> " + seg.getOcrText());
            } catch (Exception ignored) {}
        }
        return segments;
    }

    private String normalizeFilename(String filename) {
        return filename == null || filename.trim().isEmpty() ? "upload.txt" : filename.trim();
    }

    private String getExtension(String filename) {
        int i = filename.lastIndexOf(".");
        return i >= 0 ? filename.substring(i) : "";
    }

    private boolean isTextExtractable(String ext) {
        return Arrays.asList(".txt", ".md", ".xlsx").contains(ext.toLowerCase());
    }

    private boolean isPdfFile(String ext) {
        return ".pdf".equalsIgnoreCase(ext);
    }

    /**
     * 为文档生成预览文件，保存到 uploads/previews/ 目录。
     * <p>策略：
     * <ul>
     *   <li>PDF 文件: 直接复制原文件到预览目录 (doc-{id}-preview.pdf)，浏览器 iframe 渲染多页</li>
     *   <li>Office 文件 (docx/doc/xlsx/xls/pptx/ppt): 通过 LibreOffice 转 PDF</li>
     *   <li>图片文件 (png/jpg/jpeg/bmp/gif/webp/tiff): 读取原图保存为 PNG</li>
     *   <li>文本文件 (txt/md): 跳过</li>
     * </ul>
     */
    private void generatePreviewImage(Long documentId, File file, String extension) {
        String previewsDirPath = uploadDir + "previews";
        File previewsDir = new File(previewsDirPath);
        if (!previewsDir.exists()) {
            previewsDir.mkdirs();
        }
        String lowerExt = extension.toLowerCase();
        log.info("generatePreviewImage START: docId={}, ext={}, file={}", documentId, lowerExt, file.getAbsolutePath());

        Set<String> imageExts = Set.of(".png", ".jpg", ".jpeg", ".bmp", ".gif", ".webp", ".tiff");
        Set<String> officeExts = Set.of(".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt");

        try {
            if (".pdf".equals(lowerExt)) {
                // PDF: 直接复制为预览文件，浏览器内置PDF查看器支持多页/缩放/导航
                File pdfPreview = new File(previewsDir, "doc-" + documentId + "-preview.pdf");
                Files.copy(file.toPath(), pdfPreview.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("PDF preview copied: {}", pdfPreview.getAbsolutePath());
            } else if (officeExts.contains(lowerExt)) {
                // Office: 使用 DocumentPreviewService 转为 PDF
                String pdfPath = previewService.generatePreviewPdf(documentId, file.getAbsolutePath(), lowerExt);
                if (pdfPath != null) {
                    log.info("Office preview PDF generated: {}", pdfPath);
                }
            } else if (imageExts.contains(lowerExt)) {
                // 图片: 保存为 PNG
                BufferedImage img = ImageIO.read(file);
                if (img == null) {
                    log.warn("Failed to read image file for preview: {}", file.getAbsolutePath());
                    return;
                }
                File pngPreview = new File(previewsDir, "doc-" + documentId + "-preview.png");
                ImageIO.write(img, "png", pngPreview);
                log.info("Image preview generated: {}", pngPreview.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Preview generation failed for document {}: {}", documentId, e.getMessage());
        }
    }

    private BufferedImage convertOfficeToImage(File officeFile, Long documentId) {
        return null; // 不再使用，由 generatePreviewImage 统一处理
    }

}
