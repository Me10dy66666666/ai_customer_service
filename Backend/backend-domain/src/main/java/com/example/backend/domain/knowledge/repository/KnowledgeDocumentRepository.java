package com.example.backend.domain.knowledge.repository;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeDocumentRepository {
    KnowledgeDocument save(KnowledgeDocument document);
    Optional<KnowledgeDocument> findById(Long id);
    List<KnowledgeDocument> findAllOrderByCreatedAtDesc();
    List<KnowledgeDocument> findByStatus(String status);
    List<KnowledgeDocument> findByStatusBrief(String status);
    List<KnowledgeDocument> findByStatusIn(List<String> statuses);
    List<KnowledgeDocument> findByCategory(String category);
    long countByCategory(String category);
    int clearCategory(String category);
    Optional<KnowledgeDocument> findByDifyDocumentId(String difyDocumentId);
    long countByStatus(String status);
    long countByDifySyncStatus(String difySyncStatus);
    void deleteById(Long id);
    List<KnowledgeDocument> searchFulltext(String keyword, String category, int offset, int size);
    long countSearchFulltext(String keyword, String category);
    List<KnowledgeDocument> findPendingReviewFiltered(String keyword, String category);
    long countPendingReviewFiltered(String keyword, String category);
    List<Map<String, Object>> categoryStats();
}
