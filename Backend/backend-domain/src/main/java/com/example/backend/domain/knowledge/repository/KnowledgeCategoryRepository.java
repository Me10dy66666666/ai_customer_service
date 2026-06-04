package com.example.backend.domain.knowledge.repository;

import java.util.List;
import java.util.Map;

public interface KnowledgeCategoryRepository {
    List<String> findAllNames();
    void insert(String name);
    void deleteByName(String name);
    boolean existsByName(String name);
    List<Map<String, Object>> categoryStats();
}
