package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.KnowledgeCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeCategoryMapper {
    List<KnowledgeCategoryEntity> findAll();
    int insert(KnowledgeCategoryEntity entity);
    int deleteByName(@Param("name") String name);
    KnowledgeCategoryEntity findByName(@Param("name") String name);
    List<Map<String, Object>> categoryStats();
}
