package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.knowledge.model.KnowledgeSearchLog;
import com.example.backend.domain.knowledge.repository.KnowledgeSearchLogRepository;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeSearchLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class KnowledgeSearchLogRepositoryImpl implements KnowledgeSearchLogRepository {

    private final KnowledgeSearchLogMapper mapper;

    @Override
    public void save(KnowledgeSearchLog log) {
        com.example.backend.infrastructure.persistence.entity.KnowledgeSearchLogEntity entity =
                new com.example.backend.infrastructure.persistence.entity.KnowledgeSearchLogEntity();
        entity.setKeyword(log.getKeyword());
        entity.setResultCount(log.getResultCount());
        entity.setSearcherId(log.getSearcherId());
        mapper.insert(entity);
    }

    @Override
    public List<Map<String, Object>> topKeywords(int limit, LocalDate start, LocalDate end) {
        return mapper.topKeywords(limit, start.toString(), end.toString());
    }

    @Override
    public List<Map<String, Object>> zeroResultKeywords(int limit) {
        return mapper.zeroResultKeywords(limit);
    }
}
