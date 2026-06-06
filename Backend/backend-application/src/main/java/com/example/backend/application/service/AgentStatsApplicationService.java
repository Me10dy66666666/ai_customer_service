package com.example.backend.application.service;

import com.example.backend.infrastructure.persistence.entity.SlaPauseLog;
import com.example.backend.infrastructure.persistence.entity.WorkOrder;
import com.example.backend.infrastructure.persistence.mapper.ChatMessageMapper;
import com.example.backend.infrastructure.persistence.mapper.ConsultationLogMapper;
import com.example.backend.infrastructure.persistence.mapper.SlaPauseLogMapper;
import com.example.backend.infrastructure.persistence.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStatsApplicationService {

    private static final String KEY_AVG_EFFECTIVE_RESPONSE_SECONDS = "avgEffectiveResponseSeconds";
    private static final String KEY_AVG_EFFECTIVE_RESOLUTION_MINUTES = "avgEffectiveResolutionMinutes";
    private static final String KEY_TOTAL_COUNT = "totalCount";
    private static final String KEY_AGENT_ID = "agentId";
    private static final String KEY_TOTAL_WORK_ORDERS = "totalWorkOrders";

    private final ChatMessageMapper chatMessageMapper;
    private final ConsultationLogMapper consultationLogMapper;
    private final WorkOrderMapper workOrderMapper;
    private final SlaPauseLogMapper slaPauseLogMapper;

    public Map<String, Object> getMyDailyStats(Long agentId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        long sessionsHandled = chatMessageMapper.countDistinctSessionsByAgentAndDate(agentId, start, end);
        Double avgResponseSeconds = workOrderMapper.avgFirstResponseSecondsByAgent(agentId, start, end);
        Double avgSatisfaction = chatMessageMapper.avgSatisfactionByAgent(agentId, start, end);

        int responseCompliant = workOrderMapper.countResponseCompliantByAgent(agentId, start, end);
        int responseBreached = workOrderMapper.countResponseBreachedByAgent(agentId, start, end);
        int responseTotal = responseCompliant + responseBreached;
        Double responseSlaComplianceRate = responseTotal > 0
                ? Math.round(responseCompliant * 10000.0 / responseTotal) / 100.0
                : 0.0;

        int resolutionCompliant = workOrderMapper.countResolutionCompliantByAgent(agentId, start, end);
        int resolutionBreached = workOrderMapper.countResolutionBreachedByAgent(agentId, start, end);
        int resolutionTotal = resolutionCompliant + resolutionBreached;
        Double resolutionSlaComplianceRate = resolutionTotal > 0
                ? Math.round(resolutionCompliant * 10000.0 / resolutionTotal) / 100.0
                : 0.0;

        Double avgEffectiveResponseSeconds = workOrderMapper.avgEffectiveResponseByAgent(agentId, start, end);
        Double avgEffectiveResolutionSeconds = workOrderMapper.avgEffectiveResolutionByAgent(agentId, start, end);
        Double avgEffectiveResolutionMinutes = avgEffectiveResolutionSeconds != null
                ? Math.round(avgEffectiveResolutionSeconds / 60.0 * 100.0) / 100.0
                : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionsHandled", sessionsHandled);
        result.put("avgResponseSeconds", avgResponseSeconds != null ? Math.max(0.0, Math.round(avgResponseSeconds * 10.0) / 10.0) : null);
        result.put("avgSatisfaction", avgSatisfaction != null ? Math.round(avgSatisfaction * 100.0) / 100.0 : null);
        result.put("responseSlaComplianceRate", responseSlaComplianceRate);
        result.put("resolutionSlaComplianceRate", resolutionSlaComplianceRate);
        result.put(KEY_AVG_EFFECTIVE_RESPONSE_SECONDS, avgEffectiveResponseSeconds != null ? Math.round(avgEffectiveResponseSeconds * 100.0) / 100.0 : null);
        result.put(KEY_AVG_EFFECTIVE_RESOLUTION_MINUTES, avgEffectiveResolutionMinutes);
        return result;
    }

    public Map<String, Object> getMySatisfactionDist(Long agentId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = chatMessageMapper.countSatisfactionByAgent(agentId, start, end);
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            dist.put(String.valueOf(i), 0);
        }
        long total = 0;
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.getOrDefault("satisfactionLevel", ""));
            int count = toInt(row.get(KEY_TOTAL_COUNT));
            dist.put(key, count);
            total += count;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distribution", dist);
        result.put("total", total);
        return result;
    }

    public Map<String, Object> getMyTrend(Long agentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = chatMessageMapper.countAgentChatTrendByDate(agentId, start, end);
        List<String> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            dates.add(String.valueOf(row.getOrDefault("statDate", "")));
            counts.add(toInt(row.get(KEY_TOTAL_COUNT)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("counts", counts);
        return result;
    }

    public Map<String, Object> getTeamRanking(Long currentAgentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = chatMessageMapper.countAllAgentSessionsInMonth(start, end);
        List<Map<String, Object>> ranking = new ArrayList<>();
        int myRank = -1;
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Long agentId = toLong(row.get(KEY_AGENT_ID));
            int count = toInt(row.get("sessionCount"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(KEY_AGENT_ID, agentId);
            entry.put("sessionCount", count);
            entry.put("rank", rank);
            ranking.add(entry);
            if (agentId != null && agentId.equals(currentAgentId)) {
                myRank = rank;
            }
            rank++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", ranking);
        result.put("myRank", myRank);
        return result;
    }

    public Map<String, Object> getMyMonthlyTotal(Long agentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        long totalSessions = chatMessageMapper.countDistinctSessionsByAgentAndDate(agentId, start, end);
        Double avgSatisfaction = chatMessageMapper.avgSatisfactionByAgent(agentId, start, end);
        Double avgResponseSeconds = workOrderMapper.avgFirstResponseSecondsByAgent(agentId, start, end);

        int responseCompliant = workOrderMapper.countResponseCompliantByAgent(agentId, start, end);
        int responseBreached = workOrderMapper.countResponseBreachedByAgent(agentId, start, end);
        int responseTotal = responseCompliant + responseBreached;
        Double responseSlaComplianceRate = responseTotal > 0
                ? Math.round(responseCompliant * 10000.0 / responseTotal) / 100.0
                : 0.0;

        int resolutionCompliant = workOrderMapper.countResolutionCompliantByAgent(agentId, start, end);
        int resolutionBreached = workOrderMapper.countResolutionBreachedByAgent(agentId, start, end);
        int resolutionTotal = resolutionCompliant + resolutionBreached;
        Double resolutionSlaComplianceRate = resolutionTotal > 0
                ? Math.round(resolutionCompliant * 10000.0 / resolutionTotal) / 100.0
                : 0.0;

        Double avgEffectiveResponseSeconds = workOrderMapper.avgEffectiveResponseByAgent(agentId, start, end);
        Double avgEffectiveResolutionSeconds = workOrderMapper.avgEffectiveResolutionByAgent(agentId, start, end);
        Double avgEffectiveResolutionMinutes = avgEffectiveResolutionSeconds != null
                ? Math.round(avgEffectiveResolutionSeconds / 60.0 * 100.0) / 100.0
                : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSessions", totalSessions);
        result.put("avgSatisfaction", avgSatisfaction != null ? Math.round(avgSatisfaction * 100.0) / 100.0 : null);
        result.put("avgResponseSeconds", avgResponseSeconds != null ? Math.max(0.0, Math.round(avgResponseSeconds * 10.0) / 10.0) : null);
        result.put("responseSlaComplianceRate", responseSlaComplianceRate);     
        result.put("resolutionSlaComplianceRate", resolutionSlaComplianceRate);
        result.put(KEY_AVG_EFFECTIVE_RESPONSE_SECONDS, avgEffectiveResponseSeconds != null ? Math.round(avgEffectiveResponseSeconds * 100.0) / 100.0 : null);
        result.put(KEY_AVG_EFFECTIVE_RESOLUTION_MINUTES, avgEffectiveResolutionMinutes);
        return result;
    }

    public Map<String, Object> getMySlaOverview(Long agentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        int totalWorkOrders = workOrderMapper.countTotalSlaWorkOrdersByAgent(agentId, start, end);
        int responseCompliant = workOrderMapper.countResponseCompliantByAgent(agentId, start, end);
        int responseBreached = workOrderMapper.countResponseBreachedByAgent(agentId, start, end);
        int resolutionCompliant = workOrderMapper.countResolutionCompliantByAgent(agentId, start, end);
        int resolutionBreached = workOrderMapper.countResolutionBreachedByAgent(agentId, start, end);

        int responseTotal = responseCompliant + responseBreached;
        Double responseComplianceRate = responseTotal > 0
                ? Math.round(responseCompliant * 10000.0 / responseTotal) / 100.0
                : 0.0;

        int resolutionTotal = resolutionCompliant + resolutionBreached;
        Double resolutionComplianceRate = resolutionTotal > 0
                ? Math.round(resolutionCompliant * 10000.0 / resolutionTotal) / 100.0
                : 0.0;

        Double avgEffectiveResponseSeconds = workOrderMapper.avgEffectiveResponseByAgent(agentId, start, end);
        Double avgEffectiveResolutionSeconds = workOrderMapper.avgEffectiveResolutionByAgent(agentId, start, end);
        Double avgEffectiveResolutionMinutes = avgEffectiveResolutionSeconds != null
                ? Math.round(avgEffectiveResolutionSeconds / 60.0 * 100.0) / 100.0
                : null;

        int totalBreached = responseBreached + resolutionBreached;
        Double overtimeRatio = totalWorkOrders > 0
                ? Math.round(totalBreached * 10000.0 / totalWorkOrders) / 100.0
                : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_TOTAL_WORK_ORDERS, totalWorkOrders);
        result.put("responseComplianceRate", responseComplianceRate);
        result.put("resolutionComplianceRate", resolutionComplianceRate);
        result.put(KEY_AVG_EFFECTIVE_RESPONSE_SECONDS, avgEffectiveResponseSeconds != null ? Math.round(avgEffectiveResponseSeconds * 100.0) / 100.0 : null);
        result.put(KEY_AVG_EFFECTIVE_RESOLUTION_MINUTES, avgEffectiveResolutionMinutes);
        result.put("responseBreachedCount", responseBreached);
        result.put("resolutionBreachedCount", resolutionBreached);
        result.put("overtimeRatio", overtimeRatio);
        return result;
    }

    public Map<String, Object> getMySlaTrend(Long agentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = workOrderMapper.countSlaTrendByDate(agentId, start, end);
        List<String> dates = new ArrayList<>();
        List<Integer> compliantCounts = new ArrayList<>();
        List<Integer> totalCounts = new ArrayList<>();
        List<Double> rates = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            dates.add(String.valueOf(row.getOrDefault("statDate", "")));
            int compliant = toInt(row.get("compliantCount"));
            int total = toInt(row.get(KEY_TOTAL_COUNT));
            compliantCounts.add(compliant);
            totalCounts.add(total);
            double rate = total > 0 ? Math.round(compliant * 10000.0 / total) / 100.0 : 0.0;
            rates.add(rate);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("compliantCounts", compliantCounts);
        result.put("totalCounts", totalCounts);
        result.put("rates", rates);
        return result;
    }

    public Map<String, Object> getTeamSlaRanking(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = workOrderMapper.countSlaAgentRanking(start, end);
        List<Map<String, Object>> ranking = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Long agentId = toLong(row.get(KEY_AGENT_ID));
            int totalWorkOrders = toInt(row.get(KEY_TOTAL_WORK_ORDERS));
            int slaCompliantCount = toInt(row.get("slaCompliantCount"));
            Double slaComplianceRate = totalWorkOrders > 0
                    ? Math.round(slaCompliantCount * 10000.0 / totalWorkOrders) / 100.0
                    : 0.0;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(KEY_AGENT_ID, agentId);
            entry.put(KEY_TOTAL_WORK_ORDERS, totalWorkOrders);
            entry.put("slaCompliantCount", slaCompliantCount);
            entry.put("slaComplianceRate", slaComplianceRate);
            entry.put("rank", rank);
            ranking.add(entry);
            rank++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", ranking);
        return result;
    }

    public Map<String, Object> getSlaDetail(Long workOrderId) {
        WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (workOrder == null) {
            return result;
        }

        result.put("workOrderId", workOrder.getId());
        result.put("title", workOrder.getTitle());
        result.put("status", workOrder.getStatus());
        result.put("responseDeadline", workOrder.getResponseDeadline());
        result.put("respondedAt", workOrder.getRespondedAt());
        result.put("responseCompliant", workOrder.getRespondedAt() != null
                && workOrder.getResponseDeadline() != null
                && !workOrder.getRespondedAt().isAfter(workOrder.getResponseDeadline()));
        result.put("slaDeadline", workOrder.getSlaDeadline());
        result.put("completedAt", workOrder.getUpdateTime());
        result.put("resolutionCompliant", isResolutionCompliant(workOrder));
        result.put("effectiveResponseSeconds", workOrder.getEffectiveResponseSeconds());
        result.put("effectiveResolutionSeconds", workOrder.getEffectiveResolutionSeconds());

        List<SlaPauseLog> pauseLogs = loadPauseLogs(workOrderId);
        long totalPausedSeconds = pauseLogs.stream()
                .mapToLong(p -> p.getPausedEffectiveSeconds() != null ? p.getPausedEffectiveSeconds() : 0L)
                .sum();
        result.put("totalPauseCount", pauseLogs.size());
        result.put("totalPausedSeconds", totalPausedSeconds);
        result.put("slaPaused", workOrder.getSlaPaused());

        return result;
    }

    public Map<String, Object> getTeamAverage(Long currentAgentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // 查询活跃客服数量
        long agentCount = chatMessageMapper.countDistinctAgentIds(start, end);

        // 仅1名客服时，团队平均 = 该客服个人数据
        if (agentCount <= 1 && currentAgentId != null) {
            Map<String, Object> myData = getMyMonthlyTotal(currentAgentId, startDate, endDate);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("teamTotalSessions", myData.get("totalSessions"));
            result.put("teamAvgSatisfaction", myData.get("avgSatisfaction"));
            result.put("teamSlaRate", myData.get("responseSlaComplianceRate"));
            result.put("teamAvgFirstResponseSeconds", myData.get("avgResponseSeconds"));
            result.put("teamAvgEffectiveResponseSeconds", myData.get("avgEffectiveResponseSeconds"));
            return result;
        }

        long teamTotalSessions = consultationLogMapper.countTotalDistinctSessionsInMonth(start, end);

        Double teamAvgSatisfaction = chatMessageMapper.avgSatisfactionByDate(start, end);

        Map<String, Object> slaOverview = workOrderMapper.countSlaOverview(start, end);
        int totalWorkOrders = slaOverview != null ? toInt(slaOverview.get("totalWorkOrders")) : 0;
        int responseCompliant = slaOverview != null ? toInt(slaOverview.get("responseCompliant")) : 0;
        Double teamSlaRate = totalWorkOrders > 0
                ? Math.round(responseCompliant * 10000.0 / totalWorkOrders) / 100.0
                : 0.0;

        Double teamAvgFirstResponseSeconds = workOrderMapper.avgFirstResponseSecondsByDate(start, end);
        Double teamAvgEffectiveResponseSeconds = workOrderMapper.avgEffectiveResponseByDate(start, end);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("teamTotalSessions", teamTotalSessions);
        result.put("teamAvgSatisfaction", teamAvgSatisfaction != null
                ? Math.round(teamAvgSatisfaction * 100.0) / 100.0 : null);      
        result.put("teamSlaRate", teamSlaRate);
        result.put("teamAvgFirstResponseSeconds", teamAvgFirstResponseSeconds != null
                ? Math.round(teamAvgFirstResponseSeconds * 10.0) / 10.0 : null);
        result.put("teamAvgEffectiveResponseSeconds", teamAvgEffectiveResponseSeconds != null
                ? Math.round(teamAvgEffectiveResponseSeconds * 100.0) / 100.0 : null);
        return result;
    }

    public Map<String, Object> getMyWorkOrderStats(Long agentId, LocalDate startDate, LocalDate endDate) {
        try {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);

            Map<String, Object> stats = workOrderMapper.countWorkOrdersByAgent(agentId, start, end);
            if (stats == null) {
                stats = new LinkedHashMap<>();
            }

            long total = toInt(stats.getOrDefault("totalCount", 0));
            long completed = toInt(stats.getOrDefault("completedCount", 0));
            double resolutionRate = total > 0 ? Math.round(completed * 10000.0 / total) / 100.0 : 0.0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalCount", total);
            result.put("completedCount", completed);
            result.put("pendingCount", stats.getOrDefault("pendingCount", 0));
            result.put("processingCount", stats.getOrDefault("processingCount", 0));
            result.put("cancelledCount", stats.getOrDefault("cancelledCount", 0));
            result.put("resolutionRate", resolutionRate);
            return result;
        } catch (Exception e) {
            log.error("getMyWorkOrderStats failed for agentId={}, startDate={}, endDate={}", agentId, startDate, endDate, e);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("totalCount", 0);
            errorResult.put("completedCount", 0);
            errorResult.put("pendingCount", 0);
            errorResult.put("processingCount", 0);
            errorResult.put("cancelledCount", 0);
            errorResult.put("resolutionRate", 0.0);
            errorResult.put("_error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取某客服的工单满意度分布。
     *
     * @param agentId 客服ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param workOrderType 工单类型筛选：all/pre_sales/after_sales
     */
    public Map<String, Object> getMyWorkOrderSatisfaction(Long agentId, LocalDate startDate, LocalDate endDate, String workOrderType) {
        try {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);

            List<Map<String, Object>> rows = workOrderMapper.countWorkOrderRatingByAgent(agentId, start, end, workOrderType);
            Map<String, Integer> dist = new LinkedHashMap<>();
            for (int i = 1; i <= 5; i++) {
                dist.put(String.valueOf(i), 0);
            }
            long total = 0;
            for (Map<String, Object> row : rows) {
                String key = String.valueOf(row.getOrDefault("ratingLevel", ""));
                int count = row.get("totalCount") instanceof Number n ? n.intValue() : 0;
                dist.put(key, count);
                total += count;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("distribution", dist);
            result.put("total", total);
            return result;
        } catch (Exception e) {
            log.error("getMyWorkOrderSatisfaction failed for agentId={}, startDate={}, endDate={}, workOrderType={}", agentId, startDate, endDate, workOrderType, e);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            Map<String, Integer> emptyDist = new LinkedHashMap<>();
            for (int i = 1; i <= 5; i++) {
                emptyDist.put(String.valueOf(i), 0);
            }
            errorResult.put("distribution", emptyDist);
            errorResult.put("total", 0);
            errorResult.put("_error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return errorResult;
        }
    }

    private boolean isResolutionCompliant(WorkOrder workOrder) {
        if (!"resolved".equals(workOrder.getStatus()) && !"closed".equals(workOrder.getStatus())) {
            return false;
        }
        if (workOrder.getSlaDeadline() == null || workOrder.getUpdateTime() == null) {
            return true;
        }
        return !workOrder.getUpdateTime().isAfter(workOrder.getSlaDeadline());
    }

    private List<SlaPauseLog> loadPauseLogs(Long workOrderId) {
        SlaPauseLog latest = slaPauseLogMapper.findLatestByWorkOrderId(workOrderId);
        return latest != null ? List.of(latest) : Collections.emptyList();
    }

    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private Long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}
