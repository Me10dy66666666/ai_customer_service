package com.example.backend.domain.knowledge.repository;

import com.example.backend.domain.knowledge.model.KnowledgeSearchLog;

public interface KnowledgeSearchLogRepository {
    void save(KnowledgeSearchLog log);
    java.util.List<java.util.Map<String, Object>> topKeywords(int limit, java.time.LocalDate start, java.time.LocalDate end);
    java.util.List<java.util.Map<String, Object>> zeroResultKeywords(int limit);
}
