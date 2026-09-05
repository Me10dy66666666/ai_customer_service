package com.example.backend.domain.knowledge.repository;

import com.example.backend.domain.knowledge.model.KnowledgeOutbox;

import java.time.LocalDateTime;
import java.util.List;

public interface KnowledgeOutboxRepository {
    void save(KnowledgeOutbox outbox);
    List<KnowledgeOutbox> findPendingBefore(LocalDateTime beforeTime);
    boolean updateToProcessing(Long id);
    void markCompleted(Long id);
    void scheduleRetry(Long id, LocalDateTime nextRetryAt, String errorMessage);
    void markFailed(Long id, String errorMessage);
    boolean replayFailed(Long id, LocalDateTime nextRetryAt);
    void deleteByDocumentId(Long documentId);
}
