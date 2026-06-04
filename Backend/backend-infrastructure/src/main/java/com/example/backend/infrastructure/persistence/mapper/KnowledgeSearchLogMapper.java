package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.KnowledgeSearchLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeSearchLogMapper {
    int insert(KnowledgeSearchLogEntity entity);
    List<Map<String, Object>> topKeywords(@Param("limit") int limit,
                                           @Param("start") String start,
                                           @Param("end") String end);
    List<Map<String, Object>> zeroResultKeywords(@Param("limit") int limit);
    List<Map<String, Object>> dailyHitRateTrend(@Param("start") String start, @Param("end") String end);
    List<Map<String, Object>> dailyEffectTrend(@Param("start") String start, @Param("end") String end);
    long countByDateRange(@Param("start") String start, @Param("end") String end);
    long countHitByDateRange(@Param("start") String start, @Param("end") String end);
}
