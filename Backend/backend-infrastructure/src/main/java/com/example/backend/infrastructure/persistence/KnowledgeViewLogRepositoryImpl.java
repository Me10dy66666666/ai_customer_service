package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.knowledge.model.KnowledgeViewLog;
import com.example.backend.domain.knowledge.repository.KnowledgeViewLogRepository;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeViewLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class KnowledgeViewLogRepositoryImpl implements KnowledgeViewLogRepository {

    private final KnowledgeViewLogMapper mapper;

    @Override
    public void save(KnowledgeViewLog log) {
        com.example.backend.infrastructure.persistence.entity.KnowledgeViewLogEntity entity =
                new com.example.backend.infrastructure.persistence.entity.KnowledgeViewLogEntity();
        entity.setDocumentId(log.getDocumentId());
        entity.setViewerId(log.getViewerId());
        entity.setViewerRole(log.getViewerRole());
        mapper.insert(entity);
    }

    @Override
    public long countByDateRange(LocalDate start, LocalDate end) {
        return mapper.countByDateRange(start.toString(), end.toString());
    }

    @Override
    public List<Map<String, Object>> topDocuments(int limit, LocalDate start, LocalDate end) {
        return mapper.topDocuments(limit, start.toString(), end.toString());
    }

    @Override
    public List<Map<String, Object>> monthlyTrend(LocalDate start, LocalDate end) {
        return mapper.monthlyTrend(start.toString(), end.toString());
    }

    @Override
    public List<Long> findUnviewedDocumentIds(LocalDate since) {
        return mapper.findUnviewedDocumentIds(since.toString());
    }
}
