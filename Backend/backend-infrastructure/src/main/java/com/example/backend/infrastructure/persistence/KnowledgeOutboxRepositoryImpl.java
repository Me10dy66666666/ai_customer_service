package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.knowledge.model.KnowledgeOutbox;
import com.example.backend.domain.knowledge.repository.KnowledgeOutboxRepository;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeOutboxMapper;
import com.example.backend.infrastructure.persistence.entity.KnowledgeOutboxEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class KnowledgeOutboxRepositoryImpl implements KnowledgeOutboxRepository {

    private final KnowledgeOutboxMapper mapper;

    @Override
    public void save(KnowledgeOutbox outbox) {
        if (outbox.getEventId() == null || outbox.getEventId().isBlank()) {
            String identity = outbox.getDocumentId() + "|" + outbox.getEventType() + "|" + outbox.getPayload();
            outbox.setEventId(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString());
        }
        KnowledgeOutboxEntity entity = toEntity(outbox);
        mapper.insert(entity);
        outbox.setId(entity.getId());
    }

    @Override
    public List<KnowledgeOutbox> findPendingBefore(LocalDateTime beforeTime) {
        return mapper.findPendingBefore(beforeTime).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public boolean updateToProcessing(Long id) {
        return mapper.updateToProcessing(id) > 0;
    }

    @Override
    public void markCompleted(Long id) {
        mapper.markCompleted(id);
    }

    @Override
    public void scheduleRetry(Long id, LocalDateTime nextRetryAt, String errorMessage) {
        mapper.scheduleRetry(id, nextRetryAt, errorMessage);
    }

    @Override
    public void markFailed(Long id, String errorMessage) {
        mapper.markFailed(id, errorMessage);
    }

    @Override
    public boolean replayFailed(Long id, LocalDateTime nextRetryAt) {
        return mapper.replayFailed(id, nextRetryAt) > 0;
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        mapper.deleteByDocumentId(documentId);
    }

    private KnowledgeOutbox toDomain(com.example.backend.infrastructure.persistence.entity.KnowledgeOutboxEntity po) {
        KnowledgeOutbox outbox = new KnowledgeOutbox();
        outbox.setId(po.getId());
        outbox.setEventId(po.getEventId());
        outbox.setDocumentId(po.getDocumentId());
        outbox.setEventType(po.getEventType());
        outbox.setPayload(po.getPayload());
        outbox.setStatus(po.getStatus());
        outbox.setRetryCount(po.getRetryCount());
        outbox.setMaxRetry(po.getMaxRetry());
        outbox.setLastError(po.getLastError());
        outbox.setCreatedAt(po.getCreatedAt());
        outbox.setNextRetryAt(po.getNextRetryAt());
        outbox.setLockedAt(po.getLockedAt());
        outbox.setCompletedAt(po.getCompletedAt());
        return outbox;
    }

    private com.example.backend.infrastructure.persistence.entity.KnowledgeOutboxEntity toEntity(KnowledgeOutbox outbox) {
        com.example.backend.infrastructure.persistence.entity.KnowledgeOutboxEntity po =
                new com.example.backend.infrastructure.persistence.entity.KnowledgeOutboxEntity();
        po.setDocumentId(outbox.getDocumentId());
        po.setEventId(outbox.getEventId());
        po.setEventType(outbox.getEventType());
        po.setPayload(outbox.getPayload());
        po.setStatus(outbox.getStatus());
        po.setRetryCount(outbox.getRetryCount());
        po.setMaxRetry(outbox.getMaxRetry());
        po.setLastError(outbox.getLastError());
        po.setNextRetryAt(outbox.getNextRetryAt());
        po.setLockedAt(outbox.getLockedAt());
        po.setCompletedAt(outbox.getCompletedAt());
        return po;
    }
}
