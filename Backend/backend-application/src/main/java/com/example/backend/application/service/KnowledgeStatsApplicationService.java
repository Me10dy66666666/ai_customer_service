package com.example.backend.application.service;

import com.example.backend.domain.knowledge.model.KnowledgeSearchLog;
import com.example.backend.domain.knowledge.model.KnowledgeViewLog;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.repository.KnowledgeSearchLogRepository;
import com.example.backend.domain.knowledge.repository.KnowledgeViewLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeStatsApplicationService {

    private final KnowledgeViewLogRepository viewLogRepository;
    private final KnowledgeSearchLogRepository searchLogRepository;
    private final KnowledgeDocumentRepository documentRepository;

    public void trackView(Long documentId, Long viewerId, String viewerRole) {
        CompletableFuture.runAsync(() -> {
            try {
                KnowledgeViewLog log = new KnowledgeViewLog();
                log.setDocumentId(documentId);
                log.setViewerId(viewerId);
                log.setViewerRole(viewerRole);
                viewLogRepository.save(log);
            } catch (Exception e) {
                log.warn("Failed to track view: {}", e.getMessage());
            }
        });
    }

    public void trackSearch(String keyword, long resultCount, Long searcherId) {
        CompletableFuture.runAsync(() -> {
            try {
                KnowledgeSearchLog log = new KnowledgeSearchLog();
                log.setKeyword(keyword);
                log.setResultCount((int) resultCount);
                log.setSearcherId(searcherId);
                searchLogRepository.save(log);
            } catch (Exception e) {
                log.warn("Failed to track search: {}", e.getMessage());
            }
        });
    }

    public Map<String, Object> getDashboard(LocalDate start, LocalDate end) {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        long publishedCount = documentRepository.countByStatus("PUBLISHED");
        long archivedCount = documentRepository.countByStatus("ARCHIVED");
        long syncedDifyCount = documentRepository.countByDifySyncStatus("SYNCED");
        long totalViews = viewLogRepository.countByDateRange(start, end);

        dashboard.put("published_count", publishedCount);
        dashboard.put("archived_count", archivedCount);
        dashboard.put("synced_dify_count", syncedDifyCount);
        dashboard.put("total_views", totalViews);

        List<Map<String, Object>> topDocs = viewLogRepository.topDocuments(10, start, end);
        dashboard.put("top_documents", topDocs);

        List<Long> unviewedIds = viewLogRepository.findUnviewedDocumentIds(
                LocalDate.now().minusDays(30));
        if (!unviewedIds.isEmpty()) {
            List<Map<String, Object>> unviewedDocs = new ArrayList<>();
            for (Long id : unviewedIds) {
                documentRepository.findById(id).ifPresent(doc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", doc.getId());
                    m.put("title", doc.getTitle());
                    unviewedDocs.add(m);
                });
            }
            dashboard.put("unviewed_documents", unviewedDocs);
        } else {
            dashboard.put("unviewed_documents", List.of());
        }

        List<Map<String, Object>> hotKeywords = searchLogRepository.topKeywords(10, start, end);
        dashboard.put("hot_keywords", hotKeywords);

        List<Map<String, Object>> zeroResultWords = searchLogRepository.zeroResultKeywords(10);
        dashboard.put("zero_result_keywords", zeroResultWords);

        List<Map<String, Object>> trend = viewLogRepository.monthlyTrend(
                start.minusMonths(12), end);
        dashboard.put("monthly_trend", trend);

        Map<String, Long> categoryDistribution = new LinkedHashMap<>();
        List<String> categories = List.of("product", "policy", "guide", "faq", "other");
        for (String cat : categories) {
            categoryDistribution.put(cat, documentRepository.countByStatus("PUBLISHED"));
        }
        dashboard.put("category_distribution", categoryDistribution);

        return dashboard;
    }
}
