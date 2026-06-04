package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChatMessageMapper {
    int insert(ChatMessage message);

    List<ChatMessage> findBySessionIdOrderByMessageSeqAsc(@Param("sessionId") String sessionId);

    Integer selectMaxMessageSeqBySessionId(@Param("sessionId") String sessionId);

    long countDistinctSessionsByAgentAndDate(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Double avgFirstResponseSecondsByAgentAndDate(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Double avgFirstResponseSecondsByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> countAgentChatTrendByDate(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countSlaCompliantSessions(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("slaThresholdSeconds") int slaThresholdSeconds);

    /** 更新会话中最后一条 AGENT 消息的服务评价 */
    int updateSatisfactionOnLatestAgentMsg(@Param("sessionId") String sessionId, @Param("satisfaction") int satisfaction);

    /** 按客服统计 chat_messages 满意度分布 */
    List<Map<String, Object>> countSatisfactionByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 客服平均满意度（从 chat_messages 取） */
    Double avgSatisfactionByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 全局平均满意度（不按客服筛选） */
    Double avgSatisfactionByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
