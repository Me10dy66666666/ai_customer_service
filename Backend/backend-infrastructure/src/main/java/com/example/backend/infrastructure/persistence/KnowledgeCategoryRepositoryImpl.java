package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.knowledge.repository.KnowledgeCategoryRepository;
import com.example.backend.infrastructure.persistence.entity.KnowledgeCategoryEntity;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class KnowledgeCategoryRepositoryImpl implements KnowledgeCategoryRepository {

    private final KnowledgeCategoryMapper mapper;

    @Override
    public List<String> findAllNames() {
        return mapper.findAll().stream()
                .map(KnowledgeCategoryEntity::getName)
                .collect(Collectors.toList());
    }

    @Override
    public void insert(String name) {
        KnowledgeCategoryEntity entity = new KnowledgeCategoryEntity();
        entity.setName(name);
        entity.setSortOrder(0);
        mapper.insert(entity);
    }

    @Override
    public void deleteByName(String name) {
        mapper.deleteByName(name);
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.findByName(name) != null;
    }

    @Override
    public List<Map<String, Object>> categoryStats() {
        return mapper.categoryStats();
    }
}
