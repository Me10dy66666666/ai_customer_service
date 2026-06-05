package com.example.backend.interfaces.controller;

import com.example.backend.application.service.KnowledgeReviewApplicationService;
import com.example.backend.application.service.KnowledgeStatsApplicationService;
import com.example.backend.common.BusinessException;
import com.example.backend.common.Result;
import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.service.KnowledgeSearchService;
import com.example.backend.interfaces.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeReviewController {

    private final KnowledgeReviewApplicationService reviewService;
    private final KnowledgeSearchService searchService;
    private final KnowledgeStatsApplicationService statsService;

    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_NOSNIFF = "nosniff";

    @PostMapping("/upload")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> uploadAndOcr(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(defaultValue = "general") String category)
            throws IOException {
        return Result.success(reviewService.uploadAndOcr(file, category));
    }

    @PostMapping("/upload/batch")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<List<Map<String, Object>>> uploadBatch(@RequestParam("files") List<MultipartFile> files,
                                                          @RequestParam(defaultValue = "general") String category)
            throws IOException {
        return Result.success(reviewService.uploadBatch(files, category));
    }

    @GetMapping("/review/{documentId}")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> getReviewData(@PathVariable Long documentId) {
        return Result.success(reviewService.getReviewData(documentId));
    }

    @PostMapping("/review/{documentId}/submit")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> submitReview(@PathVariable Long documentId,
                                                     @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segments = (List<Map<String, Object>>) body.get("segments");
        String reviewedBy = (String) body.getOrDefault("reviewedBy", "admin");
        if (reviewedBy == null || reviewedBy.isBlank()) {
            throw new BusinessException(400, "审核人不能为空");
        }
        return Result.success(reviewService.submitReview(documentId, segments, reviewedBy));
    }

    @GetMapping("/list")
    @RequireRole({"ADMIN", "KB_ADMIN", "AGENT"})
    public Result<List<KnowledgeDocument>> list(@RequestParam(required = false) String status) {
        return Result.success(reviewService.listDocuments(status));
    }

    @GetMapping("/pending-review")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<List<KnowledgeDocument>> listPendingReview(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return Result.success(reviewService.listPendingReview(keyword, category));
    }

    @GetMapping("/detail/{documentId}")
    @RequireRole({"ADMIN", "KB_ADMIN", "AGENT"})
    public Result<KnowledgeDocument> getDetail(@PathVariable Long documentId, HttpServletRequest request) {
        String role = request.getHeader("X-User-Role");
        Long viewerId = extractUserId(request);
        statsService.trackView(documentId, viewerId, role);
        return Result.success(reviewService.getDocumentDetail(documentId));
    }

    @GetMapping("/search")
    @RequireRole({"ADMIN", "KB_ADMIN", "AGENT"})
    public Result<Map<String, Object>> search(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               HttpServletRequest request) {
        List<KnowledgeDocument> docs = searchService.search(keyword, category, page, size);
        docs = docs.stream().filter(d -> d.getId() != null).toList();
        long total = docs.size();
        Long searcherId = extractUserId(request);
        statsService.trackSearch(keyword != null ? keyword : "", total, searcherId);
        return Result.success(Map.of("list", docs, "total", total, "page", page, "size", size));
    }

    @GetMapping("/{documentId}/history")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<List<Map<String, Object>>> getRevisionHistory(@PathVariable Long documentId) {
        return Result.success(reviewService.getRevisionHistory(documentId));
    }

    @PostMapping("/{documentId}/archive")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> archive(@PathVariable Long documentId, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "MANUAL");
        reviewService.archiveDocument(documentId, reason);
        return Result.success(null);
    }

    @PostMapping("/batch-archive")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> batchArchive(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("documentIds");
        if (rawIds == null || rawIds.isEmpty()) {
            throw new com.example.backend.common.BusinessException(400, "documentIds 不能为空");
        }
        List<Long> documentIds = rawIds.stream()
                .map(id -> id instanceof Number n ? n.longValue() : Long.parseLong(id.toString()))
                .toList();
        String reason = (String) body.getOrDefault("reason", "BATCH_MANUAL");
        int successCount = reviewService.batchArchive(documentIds, reason);
        return Result.success(Map.of("successCount", successCount));
    }

    @PostMapping("/batch-delete")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("documentIds");
        if (rawIds == null || rawIds.isEmpty()) {
            throw new com.example.backend.common.BusinessException(400, "documentIds 不能为空");
        }
        List<Long> documentIds = rawIds.stream()
                .map(id -> id instanceof Number n ? n.longValue() : Long.parseLong(id.toString()))
                .toList();
        int successCount = reviewService.batchDelete(documentIds);
        return Result.success(Map.of("successCount", successCount));
    }

    @PostMapping("/{documentId}/retry-sync")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> retryDifySync(@PathVariable Long documentId) {
        reviewService.retryDifySync(documentId);
        return Result.success(null);
    }

    @DeleteMapping("/{documentId}")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> deleteDocument(@PathVariable Long documentId) {
        reviewService.deleteDocument(documentId);
        return Result.success(null);
    }

    @PutMapping("/{documentId}/toggle")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> toggleDocumentEnabled(@PathVariable Long documentId,
                                            @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        reviewService.toggleDocumentEnabled(documentId, enabled);
        return Result.success(null);
    }

    @DeleteMapping("/review/{documentId}/reject")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> rejectDocument(@PathVariable Long documentId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        String reviewedBy = body != null ? (String) body.getOrDefault("reviewedBy", "system") : "system";
        reviewService.rejectDocument(documentId, reviewedBy);
        return Result.success(null);
    }

    @GetMapping("/{documentId}/links")
    @RequireRole({"ADMIN", "KB_ADMIN", "AGENT"})
    public Result<List<Map<String, Object>>> getLinks(@PathVariable Long documentId) {
        return Result.success(reviewService.getDocumentLinks(documentId));
    }

    @GetMapping("/{documentId}/diff")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> getVersionDiff(@PathVariable Long documentId,
                                                       @RequestParam Long revA,
                                                       @RequestParam Long revB) {
        return Result.success(reviewService.getVersionDiff(documentId, revA, revB));
    }

    @GetMapping("/categories")
    @RequireRole({"ADMIN", "KB_ADMIN", "AGENT"})
    public Result<List<String>> listCategories() {
        return Result.success(reviewService.listCategories());
    }

    @PostMapping("/categories")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<String> createCategory(@RequestBody Map<String, String> body) {
        return Result.success(reviewService.createCategory(body.get("name")));
    }

    @GetMapping("/categories/stats")
    @RequireRole({"ADMIN", "KB_ADMIN", "AGENT"})
    public Result<List<Map<String, Object>>> categoryStats() {
        return Result.success(reviewService.getCategoryStats());
    }

    @DeleteMapping("/categories/{categoryName}")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> deleteCategory(@PathVariable String categoryName) {
        reviewService.deleteCategory(categoryName);
        return Result.success(null);
    }

    @PostMapping("/regenerate-toc")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> regenerateAllToc() {
        int count = reviewService.regenerateAllToc();
        return Result.success(Map.of("updatedCount", count));
    }

    @PostMapping("/reindex-es")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Map<String, Object>> reindexAllToEs() {
        Map<String, Object> result = reviewService.rebuildAll();
        return Result.success(result);
    }

    @PutMapping("/{documentId}/category")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> updateCategory(@PathVariable Long documentId,
                                     @RequestBody Map<String, String> body) {
        reviewService.updateDocumentCategory(documentId, body.get("category"));
        return Result.success(null);
    }

    @PutMapping("/{documentId}/tags")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public Result<Object> updateTags(@PathVariable Long documentId,
                                 @RequestBody Map<String, String> body) {
        reviewService.updateDocumentTags(documentId, body.get("tags"));
        return Result.success(null);
    }

    @GetMapping("/file/{documentId}")
    @RequireRole({"ADMIN", "KB_ADMIN"})
    public void getOriginalFile(@PathVariable Long documentId, HttpServletResponse response) throws IOException {
        String previewsDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "previews";

        // 优先返回 PDF 预览（PDF/Office 文件 → 浏览器 iframe 支持多页/缩放/导航/下载）
        java.nio.file.Path pdfPreviewPath = Paths.get(previewsDir, "doc-" + documentId + "-preview.pdf");
        if (Files.exists(pdfPreviewPath)) {
            response.setContentType("application/pdf");
            response.setHeader("Cache-Control", "max-age=3600");
            Files.copy(pdfPreviewPath, response.getOutputStream());
            return;
        }

        // 其次返回 PNG 预览图（图片文件 → img+Canvas OCR 框）
        java.nio.file.Path pngPreviewPath = Paths.get(previewsDir, "doc-" + documentId + "-preview.png");
        if (Files.exists(pngPreviewPath)) {
            response.setContentType("image/png");
            response.setHeader("Cache-Control", "max-age=3600");
            Files.copy(pngPreviewPath, response.getOutputStream());
            return;
        }

        // 回退：返回原始文件
        String filePath = reviewService.getOriginalFilePath(documentId);
        if (filePath == null || !Files.exists(Paths.get(filePath))) {
            response.sendError(404, "Original file not found");
            return;
        }
        java.nio.file.Path path = Paths.get(filePath);
        response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, HEADER_NOSNIFF);
        String contentType = Files.probeContentType(path);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        response.setContentType(contentType);
        Files.copy(path, response.getOutputStream());
    }

    @GetMapping("/preview/{documentId}")
    @RequireRole({"ADMIN", "KB_ADMIN", "AGENT"})
    public void getPreviewFile(@PathVariable Long documentId, HttpServletResponse response) throws IOException {
        com.example.backend.domain.knowledge.model.KnowledgeDocument doc = reviewService.getDocumentDetail(documentId);
        String previewPath = doc.getPreviewPdfPath();

        if (previewPath != null && Files.exists(Paths.get(previewPath))) {
            java.nio.file.Path path = Paths.get(previewPath);
            response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, HEADER_NOSNIFF);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            Files.copy(path, response.getOutputStream());
            return;
        }

        String originalPath = doc.getOriginalFileUrl();
        if (originalPath == null || !Files.exists(Paths.get(originalPath))) {
            response.sendError(404, "No preview available for this document");
            return;
        }

        java.nio.file.Path path = Paths.get(originalPath);
        response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, HEADER_NOSNIFF);
        String contentType = Files.probeContentType(path);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        response.setContentType(contentType);
        Files.copy(path, response.getOutputStream());
    }

    private Long extractUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            try { return Long.parseLong(userIdHeader); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
