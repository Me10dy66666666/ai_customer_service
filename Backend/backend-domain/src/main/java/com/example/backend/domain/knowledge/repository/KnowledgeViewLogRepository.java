package com.example.backend.domain.knowledge.repository;

import com.example.backend.domain.knowledge.model.KnowledgeViewLog;

public interface KnowledgeViewLogRepository {
    void save(KnowledgeViewLog log);
    long countByDateRange(java.time.LocalDate start, java.time.LocalDate end);
    java.util.List<java.util.Map<String, Object>> topDocuments(int limit, java.time.LocalDate start, java.time.LocalDate end);
    java.util.List<java.util.Map<String, Object>> monthlyTrend(java.time.LocalDate start, java.time.LocalDate end);
    java.util.List<Long> findUnviewedDocumentIds(java.time.LocalDate since);
}
