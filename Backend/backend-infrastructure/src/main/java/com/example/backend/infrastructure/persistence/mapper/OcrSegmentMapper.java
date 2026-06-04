package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.OcrSegmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OcrSegmentMapper {
    int insert(OcrSegmentEntity entity);
    int insertBatch(@Param("segments") List<OcrSegmentEntity> segments);
    List<OcrSegmentEntity> findByDocumentIdOrderBySegmentIndex(@Param("documentId") Long documentId);
    List<OcrSegmentEntity> findByDocumentIdAndStatus(@Param("documentId") Long documentId, @Param("status") String status);
    int updateStatus(@Param("id") Long id, @Param("documentId") Long documentId, @Param("status") String status, @Param("reviewedText") String reviewedText);
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
