package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.KnowledgeOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KnowledgeOutboxMapper {
    int insert(KnowledgeOutboxEntity entity);
    KnowledgeOutboxEntity selectById(Long id);
    List<KnowledgeOutboxEntity> findPendingBefore(@Param("beforeTime") LocalDateTime beforeTime);
    int updateToProcessing(@Param("id") Long id);
    int markCompleted(@Param("id") Long id);
    int scheduleRetry(@Param("id") Long id, @Param("nextRetryAt") LocalDateTime nextRetryAt,
                      @Param("lastError") String lastError);
    int markFailed(@Param("id") Long id, @Param("lastError") String lastError);
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
