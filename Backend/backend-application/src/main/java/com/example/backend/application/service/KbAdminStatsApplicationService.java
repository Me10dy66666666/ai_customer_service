package com.example.backend.application.service;

import com.example.backend.infrastructure.persistence.mapper.KnowledgeDocumentMapper;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeSearchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbAdminStatsApplicationService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSearchLogMapper knowledgeSearchLogMapper;

    public Map<String, Object> getMyReviewStats(String reviewedBy, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        long reviewedCount = knowledgeDocumentMapper.countReviewedByAndDate(reviewedBy, start, end);
        long approvedCount = knowledgeDocumentMapper.countApprovedByAndDate(reviewedBy, start, end);
        long rejectedCount = knowledgeDocumentMapper.countRejectedByAndDate(reviewedBy, start, end);
        long pendingCount = knowledgeDocumentMapper.countByStatus("PENDING_REVIEW");
        Double avgDurationSeconds = knowledgeDocumentMapper.avgReviewDurationByReviewer(reviewedBy, start, end);

        double passRate = reviewedCount > 0 ? Math.round(approvedCount * 10000.0 / reviewedCount) / 100.0 : 100.0;
        double avgDurationMin = avgDurationSeconds != null ? Math.round(avgDurationSeconds * 10.0 / 60.0) / 10.0 : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewedCount", reviewedCount);
        result.put("approvedCount", approvedCount);
        result.put("rejectedCount", rejectedCount);
        result.put("passRate", passRate);
        result.put("pendingBacklog", pendingCount);
        result.put("avgReviewMinutes", avgDurationMin);
        return result;
    }

    public Map<String, Object> getMyReviewTrend(String reviewedBy, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = knowledgeDocumentMapper.countReviewTrendByDate(reviewedBy, start, end);
        List<String> dates = new ArrayList<>();
        List<Integer> approvedCounts = new ArrayList<>();
        List<Integer> rejectedCounts = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            dates.add(String.valueOf(row.getOrDefault("statDate", "")));
            approvedCounts.add(toInt(row.get("approvedCount")));
            rejectedCounts.add(toInt(row.get("rejectedCount")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("approvedCounts", approvedCounts);
        result.put("rejectedCounts", rejectedCounts);
        return result;
    }

    public Map<String, Object> getKbHealthMetrics() {
        long totalDocs = knowledgeDocumentMapper.countAll();
        long pendingReview = knowledgeDocumentMapper.countByStatus("PENDING_REVIEW");
        long publishedCount = knowledgeDocumentMapper.countByStatus("PUBLISHED");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String start = sevenDaysAgo.format(formatter);
        String end = now.format(formatter);

        long recentSearchCount = knowledgeSearchLogMapper.countByDateRange(start, end);
        long hitCount = knowledgeSearchLogMapper.countHitByDateRange(start, end);
        double hitRate = recentSearchCount > 0 ? Math.round(hitCount * 1000.0 / recentSearchCount) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDocuments", totalDocs);
        result.put("pendingReview", pendingReview);
        result.put("publishedCount", publishedCount);
        result.put("recentSearchCount", recentSearchCount);
        result.put("hitRate", hitRate);
        return result;
    }

    public Map<String, Object> getMyMonthlyTotal(String reviewedBy, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        long totalReviewed = knowledgeDocumentMapper.countReviewedByAndDate(reviewedBy, start, end);
        Double avgDurationSeconds = knowledgeDocumentMapper.avgReviewDurationByReviewer(reviewedBy, start, end);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReviewed", totalReviewed);
        result.put("avgReviewMinutes", avgDurationSeconds != null ? Math.round(avgDurationSeconds * 10.0 / 60.0) / 10.0 : 0);
        return result;
    }

    public Map<String, Object> getDocStatusDistribution() {
        String[] statuses = {"PENDING_OCR", "PENDING_REVIEW", "PUBLISHING", "PUBLISHED", "ARCHIVED"};
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (String status : statuses) {
            distribution.put(status, knowledgeDocumentMapper.countByStatus(status));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distribution", distribution);
        return result;
    }

    public Map<String, Object> getKbHealthTrend(LocalDate startDate, LocalDate endDate) {
        String start = startDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = endDate.atTime(LocalTime.MAX).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Map<String, Object>> rows = knowledgeSearchLogMapper.dailyHitRateTrend(start, end);
        List<String> dates = new ArrayList<>();
        List<Double> hitRates = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            dates.add(String.valueOf(row.getOrDefault("statDate", "")));
            long total = ((Number) row.getOrDefault("totalCount", 0L)).longValue();
            long hit = ((Number) row.getOrDefault("hitCount", 0L)).longValue();
            double rate = total > 0 ? Math.round(hit * 1000.0 / total) / 10.0 : 0.0;
            hitRates.add(rate);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("hitRates", hitRates);
        return result;
    }

    public Map<String, Object> getRecentReviews(int limit) {
        List<Map<String, Object>> rows = knowledgeDocumentMapper.findRecentReviewed(limit);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("documentId", row.get("documentId"));
            record.put("title", row.getOrDefault("title", ""));
            record.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : "");
            record.put("reviewedAt", row.get("reviewedAt") != null ? row.get("reviewedAt").toString() : "");
            Long durationSeconds = row.get("reviewDurationSeconds") != null ? ((Number) row.get("reviewDurationSeconds")).longValue() : 0L;
            record.put("reviewDurationMinutes", Math.round(durationSeconds * 10.0 / 60.0) / 10.0);
            String status = String.valueOf(row.getOrDefault("reviewResult", ""));
            record.put("reviewResult", "PUBLISHED".equals(status) ? "通过" : "驳回");
            record.put("reviewedBy", row.getOrDefault("reviewedBy", ""));
            records.add(record);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        return result;
    }

    public Map<String, Object> getKbEffectTrend(LocalDate startDate, LocalDate endDate) {
        String start = startDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = endDate.atTime(LocalTime.MAX).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Map<String, Object>> rows = knowledgeSearchLogMapper.dailyEffectTrend(start, end);
        List<String> dates = new ArrayList<>();
        List<Integer> searchCounts = new ArrayList<>();
        List<Integer> hitCounts = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            dates.add(String.valueOf(row.getOrDefault("statDate", "")));
            searchCounts.add(((Number) row.getOrDefault("searchCount", 0)).intValue());
            hitCounts.add(((Number) row.getOrDefault("hitCount", 0)).intValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("searchCounts", searchCounts);
        result.put("hitCounts", hitCounts);
        return result;
    }

    public Map<String, Object> getHotSearchWords(LocalDate startDate, LocalDate endDate, int limit) {
        String start = startDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = endDate.atTime(LocalTime.MAX).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Map<String, Object>> rows = knowledgeSearchLogMapper.topKeywords(limit, start, end);
        List<Map<String, Object>> words = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> wordItem = new LinkedHashMap<>();
            wordItem.put("keyword", row.getOrDefault("keyword", ""));
            wordItem.put("searchCount", ((Number) row.getOrDefault("search_count", 0)).intValue());
            words.add(wordItem);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("words", words);
        return result;
    }

    public Map<String, Object> getZeroResultWords(int limit) {
        List<Map<String, Object>> rows = knowledgeSearchLogMapper.zeroResultKeywords(limit);
        List<Map<String, Object>> words = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> wordItem = new LinkedHashMap<>();
            wordItem.put("keyword", row.getOrDefault("keyword", ""));
            wordItem.put("searchCount", ((Number) row.getOrDefault("search_count", 0)).intValue());
            words.add(wordItem);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("words", words);
        return result;
    }

    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
