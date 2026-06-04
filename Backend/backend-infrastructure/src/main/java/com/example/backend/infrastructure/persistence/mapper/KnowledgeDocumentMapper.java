package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeDocumentMapper {
    int insert(KnowledgeDocumentEntity entity);
    int update(KnowledgeDocumentEntity entity);
    KnowledgeDocumentEntity selectById(Long id);
    List<KnowledgeDocumentEntity> findAllOrderByCreatedAtDesc();
    List<KnowledgeDocumentEntity> findByStatus(@Param("status") String status);
    List<KnowledgeDocumentEntity> findByStatusBrief(@Param("status") String status);
    List<KnowledgeDocumentEntity> findByStatusIn(@Param("statuses") List<String> statuses);
    List<KnowledgeDocumentEntity> findByCategory(@Param("category") String category);
    long countByCategory(@Param("category") String category);
    int clearCategory(@Param("category") String category);
    KnowledgeDocumentEntity findByDifyDocumentId(@Param("difyDocumentId") String difyDocumentId);
    long countByStatus(@Param("status") String status);
    int deleteById(Long id);
    List<KnowledgeDocumentEntity> searchFulltext(@Param("keyword") String keyword,
                                                  @Param("category") String category,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);
    long countSearchFulltext(@Param("keyword") String keyword, @Param("category") String category);
    List<KnowledgeDocumentEntity> findPendingReviewFiltered(@Param("keyword") String keyword,
                                                            @Param("category") String category);
    long countPendingReviewFiltered(@Param("keyword") String keyword, @Param("category") String category);
    long countByDifySyncStatus(@Param("difySyncStatus") String difySyncStatus);
    List<java.util.Map<String, Object>> categoryStats();

    long countReviewedByAndDate(@Param("reviewedBy") String reviewedBy, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countApprovedByAndDate(@Param("reviewedBy") String reviewedBy, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countRejectedByAndDate(@Param("reviewedBy") String reviewedBy, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Double avgReviewDurationByReviewer(@Param("reviewedBy") String reviewedBy, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> countReviewTrendByDate(@Param("reviewedBy") String reviewedBy, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> findRecentReviewed(@Param("limit") int limit);

    long countAll();
}
