package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.knowledge.model.KnowledgeRevisionLog;
import com.example.backend.domain.knowledge.repository.KnowledgeRevisionLogRepository;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeRevisionLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class KnowledgeRevisionLogRepositoryImpl implements KnowledgeRevisionLogRepository {

    private final KnowledgeRevisionLogMapper mapper;

    @Override
    public void save(KnowledgeRevisionLog log) {
        mapper.insert(toEntity(log));
    }

    @Override
    public List<KnowledgeRevisionLog> findByDocumentIdOrderByChangedAtDesc(Long documentId) {
        return mapper.findByDocumentIdOrderByChangedAtDesc(documentId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public Optional<KnowledgeRevisionLog> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        mapper.deleteByDocumentId(documentId);
    }

    private KnowledgeRevisionLog toDomain(com.example.backend.infrastructure.persistence.entity.KnowledgeRevisionLogEntity po) {
        KnowledgeRevisionLog log = new KnowledgeRevisionLog();
        log.setId(po.getId());
        log.setDocumentId(po.getDocumentId());
        log.setChangeType(po.getChangeType());
        log.setChangedFields(po.getChangedFields());
        log.setOldValue(po.getOldValue());
        log.setNewValue(po.getNewValue());
        log.setChangedBy(po.getChangedBy());
        log.setChangedAt(po.getChangedAt());
        return log;
    }

    private com.example.backend.infrastructure.persistence.entity.KnowledgeRevisionLogEntity toEntity(KnowledgeRevisionLog log) {
        com.example.backend.infrastructure.persistence.entity.KnowledgeRevisionLogEntity po =
                new com.example.backend.infrastructure.persistence.entity.KnowledgeRevisionLogEntity();
        po.setDocumentId(log.getDocumentId());
        po.setChangeType(log.getChangeType());
        po.setChangedFields(log.getChangedFields());
        po.setOldValue(log.getOldValue());
        po.setNewValue(log.getNewValue());
        po.setChangedBy(log.getChangedBy());
        return po;
    }
}
