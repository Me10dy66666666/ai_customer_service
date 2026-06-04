package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.KnowledgeViewLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeViewLogMapper {
    int insert(KnowledgeViewLogEntity entity);
    long countByDateRange(@Param("start") String start, @Param("end") String end);
    List<Map<String, Object>> topDocuments(@Param("limit") int limit,
                                            @Param("start") String start,
                                            @Param("end") String end);
    List<Map<String, Object>> monthlyTrend(@Param("start") String start,
                                            @Param("end") String end);
    List<Long> findUnviewedDocumentIds(@Param("since") String since);
}
