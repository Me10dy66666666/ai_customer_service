package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.ConsultationLog;
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
    List<Map<String, Object>> countRegisteredUsersByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countKbUsageByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    long countTotalSessions(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    long countResolvedSessions(@Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end,
                               @Param("satisfiedLevel") Integer satisfiedLevel,
                               @Param("verySatisfiedLevel") Integer verySatisfiedLevel,
                               @Param("transferIntent") String transferIntent);
    long countManualTransferSessions(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end,
                                     @Param("transferIntent") String transferIntent);
    long countWorkOrdersInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countAiResolutionRateByDate(@Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end,
                                                          @Param("satisfiedLevel") Integer satisfiedLevel,
                                                          @Param("verySatisfiedLevel") Integer verySatisfiedLevel,
                                                          @Param("transferIntent") String transferIntent);
    int batchAssignUser(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    long countSessionsByAgentAndDate(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Double avgSatisfactionByAgentAndDate(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Double avgSatisfactionByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countTotalDistinctSessionsInMonth(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> countSatisfactionDistByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> countAgentSessionsTrendByDate(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> countAllAgentSessionsInMonth(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countDistinctAgentIds(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
