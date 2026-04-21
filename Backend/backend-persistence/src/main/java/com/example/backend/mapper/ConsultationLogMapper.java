package com.example.backend.mapper;

import com.example.backend.entity.ConsultationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConsultationLogMapper {
    int insert(ConsultationLog log);
    int update(ConsultationLog log);
    int deleteById(Long id);
    ConsultationLog selectById(Long id);
    List<ConsultationLog> selectAll();

    List<ConsultationLog> findBySessionIdOrderByCreateTimeAsc(String sessionId);
    List<ConsultationLog> findByUserIdOrderByCreateTimeDesc(Long userId);
    List<ConsultationLog> findByUserIdOrderByCreateTimeAsc(Long userId);
    List<ConsultationLog> findByUserId(Long userId);
    ConsultationLog findFirstBySessionIdOrderByCreateTimeDesc(String sessionId);
    ConsultationLog findFirstBySessionIdAndDifyConversationIdIsNotNullOrderByCreateTimeDesc(String sessionId);
    long countByCreateTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> countSatisfactionByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countTrendByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countTrendWithSatisfactionByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    List<Map<String, Object>> countUniqueUsersByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countConvertedUsersByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countKbUsageByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
