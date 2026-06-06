package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkOrderMapper {
    int insert(WorkOrder workOrder);
    int update(WorkOrder workOrder);
    int deleteById(Long id);
    WorkOrder selectById(Long id);
    List<WorkOrder> selectAll();
    List<WorkOrder> findByUserId(Long userId);
    List<WorkOrder> findByStatus(String status);
    List<WorkOrder> findByHandlerId(Long handlerId);
    List<WorkOrder> findUnassigned(@Param("pendingStatus") String pendingStatus);
    int countActiveByHandlerId(@Param("handlerId") Long handlerId,
                               @Param("pendingStatus") String pendingStatus,
                               @Param("processingStatus") String processingStatus);
    int claimWorkOrder(@Param("id") Long id,
                       @Param("handlerId") Long handlerId,
                       @Param("pendingStatus") String pendingStatus,
                       @Param("processingStatus") String processingStatus);
    List<Map<String, Object>> countByStatusBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countWorkOrderTrendByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<WorkOrder> selectAllPaginated(@Param("offset") int offset, @Param("limit") int limit);
    int countAll();
    List<WorkOrder> selectByHandlerOrUnassigned(@Param("handlerId") Long handlerId,
                                                 @Param("pendingStatus") String pendingStatus,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);
    int countByHandlerOrUnassigned(@Param("handlerId") Long handlerId,
                                    @Param("pendingStatus") String pendingStatus);
    List<WorkOrder> findBySessionId(@Param("sessionId") String sessionId);
    int countActiveBySessionId(@Param("sessionId") String sessionId,
                               @Param("pendingStatus") String pendingStatus,
                               @Param("processingStatus") String processingStatus);

    // SLA performance queries
    int countResponseCompliantByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    int countResponseBreachedByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    int countResolutionCompliantByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    int countResolutionBreachedByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Double avgEffectiveResponseByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Double avgEffectiveResolutionByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Double avgEffectiveResponseByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Double avgEffectiveResolutionByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Double avgFirstResponseSecondsByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Double avgFirstResponseSecondsByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    int countTotalSlaWorkOrdersByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countSlaTrendByDate(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Map<String, Object> countSlaOverview(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countSlaAgentRanking(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countSlaTrendByBizTag(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Map<String, Object> countWorkOrdersByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Map<String, Object>> countWorkOrderRatingByAgent(@Param("agentId") Long agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("workOrderType") String workOrderType);
}
