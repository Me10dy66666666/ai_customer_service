package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.KnowledgeRevisionLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KnowledgeRevisionLogMapper {
    int insert(KnowledgeRevisionLogEntity entity);
    List<KnowledgeRevisionLogEntity> findByDocumentIdOrderByChangedAtDesc(@Param("documentId") Long documentId);
    KnowledgeRevisionLogEntity selectById(@Param("id") Long id);
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
