package com.example.backend.domain.knowledge.repository;

import com.example.backend.domain.knowledge.model.KnowledgeRevisionLog;

import java.util.List;

public interface KnowledgeRevisionLogRepository {
    void save(KnowledgeRevisionLog log);
    List<KnowledgeRevisionLog> findByDocumentIdOrderByChangedAtDesc(Long documentId);
    java.util.Optional<KnowledgeRevisionLog> findById(Long id);
    void deleteByDocumentId(Long documentId);
}
