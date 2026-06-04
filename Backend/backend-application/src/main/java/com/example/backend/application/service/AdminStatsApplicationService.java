package com.example.backend.application.service;

import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.infrastructure.persistence.mapper.ConsultationLogMapper;
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
public class AdminStatsApplicationService {
    private static final String KEY_STAT_DATE = "statDate";
    private static final String KEY_TOTAL_COUNT = "totalCount";
    private static final String KEY_DATES = "dates";
    private static final String KEY_COUNTS = "counts";
    private static final String KEY_USER_COUNT = "userCount";
    private static final String KEY_AGENT_ID = "agentId";
    private static final String KEY_BIZ_TAG = "bizTag";
    private static final String KEY_TOTAL_WORK_ORDERS = "totalWorkOrders";
    private static final String KEY_RESPONSE_COMPLIANT = "responseCompliant";
    private static final String KEY_RESOLUTION_COMPLIANT = "resolutionCompliant";

    private final ConsultationLogMapper consultationLogMapper;
    private final WorkOrderMapper workOrderMapper;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public Map<String, Object> getDailyStats(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        long totalChats = consultationLogMapper.countByCreateTimeBetween(start, end);
        List<Map<String, Object>> satDist = consultationLogMapper.countSatisfactionByDate(start, end);
        Double avgSatisfaction = calculateAvgSatisfaction(satDist);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_chats", totalChats);
        result.put("avg_satisfaction", avgSatisfaction);
        result.put("satisfaction_dist", buildSatDistribution(satDist));
        return result;
    }

    public Map<String, Object> getTrendStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = consultationLogMapper.countTrendWithSatisfactionByDate(start, end);

        List<String> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<Double> avgSats = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            dates.add(String.valueOf(row.getOrDefault(KEY_STAT_DATE, "")));
            counts.add(toInt(row.get(KEY_TOTAL_COUNT)));
            avgSats.add(toDouble(row.get("avgScore")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_DATES, dates);
        result.put(KEY_COUNTS, counts);
        result.put("avgSatisfactions", avgSats);
        return result;
    }

    public Map<String, Object> getWorkOrderStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> statusRows = workOrderMapper.countByStatusBetween(start, end);
        List<Map<String, Object>> trendRows = workOrderMapper.countWorkOrderTrendByDate(start, end);

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        byStatus.put("pending", 0);
        byStatus.put("processing", 0);
        byStatus.put("completed", 0);
        byStatus.put("cancelled", 0);
        for (Map<String, Object> row : statusRows) {
            String key = String.valueOf(row.getOrDefault("statusKey", ""));
            if ("closed".equals(key)) {
                key = "cancelled";
            }
            byStatus.put(key, toInt(row.get(KEY_TOTAL_COUNT)));
        }

        List<String> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Map<String, Object> row : trendRows) {
            dates.add(String.valueOf(row.getOrDefault(KEY_STAT_DATE, "")));
            counts.add(toInt(row.get(KEY_TOTAL_COUNT)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byStatus", byStatus);
        result.put(KEY_DATES, dates);
        result.put(KEY_COUNTS, counts);
        return result;
    }

    public Map<String, Object> getConversionStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> uniqueRows = consultationLogMapper.countUniqueUsersByDate(start, end);
        List<Map<String, Object>> registeredRows = consultationLogMapper.countRegisteredUsersByDate(start, end);
        List<Map<String, Object>> convertedRows = consultationLogMapper.countConvertedUsersByDate(start, end);

        Map<String, Integer> consultMap = toDateMap(uniqueRows, KEY_USER_COUNT);
        Map<String, Integer> regMap = toDateMap(registeredRows, KEY_USER_COUNT);
        Map<String, Integer> convMap = toDateMap(convertedRows, KEY_USER_COUNT);

        TreeSet<String> allDates = new TreeSet<>();
        allDates.addAll(consultMap.keySet());
        allDates.addAll(regMap.keySet());
        allDates.addAll(convMap.keySet());

        List<String> dates = new ArrayList<>();
        List<Integer> consultCounts = new ArrayList<>();
        List<Integer> registeredCounts = new ArrayList<>();
        List<Integer> convertedCounts = new ArrayList<>();
        List<Double> regRates = new ArrayList<>();
        List<Double> purRates = new ArrayList<>();

        for (String d : allDates) {
            dates.add(d);
            int cc = consultMap.getOrDefault(d, 0);
            int rc = regMap.getOrDefault(d, 0);
            int pc = convMap.getOrDefault(d, 0);
            consultCounts.add(cc);
            registeredCounts.add(rc);
            convertedCounts.add(pc);
            regRates.add(cc > 0 ? Math.round(rc * 10000.0 / cc) / 100.0 : 0.0);
            purRates.add(cc > 0 ? Math.round(pc * 10000.0 / cc) / 100.0 : 0.0);
        }

        long totalConsult = uniqueRows.stream().mapToLong(r -> toInt(r.get(KEY_USER_COUNT))).sum();
        long totalRegistered = registeredRows.stream().mapToLong(r -> toInt(r.get(KEY_USER_COUNT))).sum();
        long totalConverted = convertedRows.stream().mapToLong(r -> toInt(r.get(KEY_USER_COUNT))).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalConsultUsers", totalConsult);
        result.put("totalRegisteredUsers", totalRegistered);
        result.put("totalConvertedUsers", totalConverted);
        result.put("overallRegistrationRate", totalConsult > 0 ? Math.round(totalRegistered * 10000.0 / totalConsult) / 100.0 : 0.0);
        result.put("overallPurchaseRate", totalConsult > 0 ? Math.round(totalConverted * 10000.0 / totalConsult) / 100.0 : 0.0);
        result.put(KEY_DATES, dates);
        result.put("consultCounts", consultCounts);
        result.put("registeredCounts", registeredCounts);
        result.put("convertedCounts", convertedCounts);
        result.put("registrationRates", regRates);
        result.put("purchaseRates", purRates);
        return result;
    }

    public Map<String, Object> getKnowledgeBaseEffectStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        long totalDocs = knowledgeDocumentRepository.countByStatus("PUBLISHED");
        long enabledDocs = totalDocs;
        long totalHits = consultationLogMapper.countByCreateTimeBetween(start, end);
        long hitDocs = Math.min(enabledDocs, totalHits);

        Map<String, Integer> docsByCategory = new LinkedHashMap<>();
        List<String> categories = List.of("product", "policy", "guide", "faq", "other");
        for (String cat : categories) {
            docsByCategory.put(cat, (int) knowledgeDocumentRepository.countByStatus("PUBLISHED"));
        }

        Map<String, Object> statusDist = new LinkedHashMap<>();
        statusDist.put("available", enabledDocs);
        statusDist.put("disabled", 0L);

        List<Map<String, Object>> usageRows = consultationLogMapper.countKbUsageByDate(start, end);
        List<String> dates = new ArrayList<>();
        List<Integer> creationCounts = new ArrayList<>();
        for (Map<String, Object> row : usageRows) {
            dates.add(String.valueOf(row.getOrDefault(KEY_STAT_DATE, "")));
            creationCounts.add(toInt(row.get("usageCount")));
        }

        long selectedDocCount = Math.min(totalDocs, dates.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDocuments", totalDocs);
        result.put("enabledDocuments", enabledDocs);
        result.put("totalHitCount", totalHits);
        result.put("hitDocumentRate", totalDocs > 0 ? Math.round(hitDocs * 10000.0 / totalDocs) / 100.0 : 0.0);
        result.put("totalWordCount", totalHits);
        result.put("selectedDocumentCount", selectedDocCount);
        result.put("datasetName", "Dify");
        result.put("searchMethod", "\u6df7\u5408\u68c0\u7d22");
        result.put("indexingTechnique", "\u9ad8\u8d28\u91cf");
        result.put("embeddingModel", "text-embedding-ada-002");
        result.put("docsByCategory", docsByCategory);
        result.put("statusDistribution", statusDist);
        result.put(KEY_DATES, dates);
        result.put("creationCounts", creationCounts);
        result.put("topDocuments", Collections.emptyList());
        return result;
    }

    public Map<String, Object> getAiResolutionStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        long totalSessions = consultationLogMapper.countTotalSessions(start, end);
        long resolvedSessions = consultationLogMapper.countResolvedSessions(start, end, 4, 5, "\u8f6c\u4eba\u5de5");
        long manualTransfers = consultationLogMapper.countManualTransferSessions(start, end, "\u8f6c\u4eba\u5de5");

        List<Map<String, Object>> resRows = consultationLogMapper.countAiResolutionRateByDate(start, end, 4, 5, "\u8f6c\u4eba\u5de5");
        List<String> dates = new ArrayList<>();
        List<Integer> totalSessionCounts = new ArrayList<>();
        List<Integer> resolvedSessionCounts = new ArrayList<>();
        List<Integer> manualTransferCounts = new ArrayList<>();
        List<Double> resolutionRates = new ArrayList<>();

        for (Map<String, Object> row : resRows) {
            dates.add(String.valueOf(row.getOrDefault(KEY_STAT_DATE, "")));
            int ts = toInt(row.get("totalSessions"));
            int rs = toInt(row.get("resolvedSessions"));
            int mt = toInt(row.get("manualTransferSessions"));
            totalSessionCounts.add(ts);
            resolvedSessionCounts.add(rs);
            manualTransferCounts.add(mt);
            resolutionRates.add(ts > 0 ? Math.round(rs * 10000.0 / ts) / 100.0 : 0.0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallResolutionRate", totalSessions > 0 ? Math.round(resolvedSessions * 10000.0 / totalSessions) / 100.0 : 0.0);
        result.put("totalSessions", totalSessions);
        result.put("resolvedSessions", resolvedSessions);
        result.put("manualTransferSessions", manualTransfers);
        result.put(KEY_DATES, dates);
        result.put("totalSessionCounts", totalSessionCounts);
        result.put("resolvedSessionCounts", resolvedSessionCounts);
        result.put("manualTransferCounts", manualTransferCounts);
        result.put("resolutionRates", resolutionRates);
        return result;
    }

    public Map<String, Object> getSlaOverview(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        Map<String, Object> row = workOrderMapper.countSlaOverview(start, end);

        long totalWorkOrders = toLong(row.get(KEY_TOTAL_WORK_ORDERS));
        int responseCompliant = toInt(row.get(KEY_RESPONSE_COMPLIANT));
        int resolutionCompliant = toInt(row.get(KEY_RESOLUTION_COMPLIANT));
        int responseBreached = toInt(row.get("responseBreached"));
        int resolutionBreached = toInt(row.get("resolutionBreached"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalWorkOrders", totalWorkOrders);
        result.put("responseComplianceRate", totalWorkOrders > 0 ? Math.round(responseCompliant * 10000.0 / totalWorkOrders) / 100.0 : 0.0);
        result.put("resolutionComplianceRate", totalWorkOrders > 0 ? Math.round(resolutionCompliant * 10000.0 / totalWorkOrders) / 100.0 : 0.0);
        result.put("breachedWorkOrderRatio", totalWorkOrders > 0 ? Math.round((responseBreached + resolutionBreached) * 10000.0 / totalWorkOrders) / 100.0 : 0.0);
        result.put("responseCompliantCount", responseCompliant);
        result.put("resolutionCompliantCount", resolutionCompliant);
        result.put("responseBreachedCount", responseBreached);
        result.put("resolutionBreachedCount", resolutionBreached);
        return result;
    }

    public Map<String, Object> getSlaTrendByBizTag(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = workOrderMapper.countSlaTrendByBizTag(start, end);

        Map<String, Map<String, Map<String, Object>>> dateGrouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            parseBizTagTrendRow(row, dateGrouped);
        }

        List<String> dates = new ArrayList<>();
        List<Double> preSalesResponseRates = new ArrayList<>();
        List<Double> afterSalesResponseRates = new ArrayList<>();
        List<Double> preSalesResolutionRates = new ArrayList<>();
        List<Double> afterSalesResolutionRates = new ArrayList<>();

        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : dateGrouped.entrySet()) {
            Map<String, Map<String, Object>> bizTagMap = entry.getValue();
            Map<String, Object> preSalesRow = bizTagMap.get("pre_sales");
            Map<String, Object> afterSalesRow = bizTagMap.get("after_sales");

            dates.add(entry.getKey());

            int preTotal = preSalesRow != null ? toInt(preSalesRow.get(KEY_TOTAL_WORK_ORDERS)) : 0;
            int afterTotal = afterSalesRow != null ? toInt(afterSalesRow.get(KEY_TOTAL_WORK_ORDERS)) : 0;
            int preRespCompliant = preSalesRow != null ? toInt(preSalesRow.get(KEY_RESPONSE_COMPLIANT)) : 0;
            int afterRespCompliant = afterSalesRow != null ? toInt(afterSalesRow.get(KEY_RESPONSE_COMPLIANT)) : 0;
            int preResolCompliant = preSalesRow != null ? toInt(preSalesRow.get(KEY_RESOLUTION_COMPLIANT)) : 0;
            int afterResolCompliant = afterSalesRow != null ? toInt(afterSalesRow.get(KEY_RESOLUTION_COMPLIANT)) : 0;

            preSalesResponseRates.add(preTotal > 0 ? Math.round(preRespCompliant * 10000.0 / preTotal) / 100.0 : 0.0);
            afterSalesResponseRates.add(afterTotal > 0 ? Math.round(afterRespCompliant * 10000.0 / afterTotal) / 100.0 : 0.0);
            preSalesResolutionRates.add(preTotal > 0 ? Math.round(preResolCompliant * 10000.0 / preTotal) / 100.0 : 0.0);
            afterSalesResolutionRates.add(afterTotal > 0 ? Math.round(afterResolCompliant * 10000.0 / afterTotal) / 100.0 : 0.0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_DATES, dates);
        result.put("preSalesResponseRate", preSalesResponseRates);
        result.put("afterSalesResponseRate", afterSalesResponseRates);
        result.put("preSalesResolutionRate", preSalesResolutionRates);
        result.put("afterSalesResolutionRate", afterSalesResolutionRates);
        return result;
    }

    public Map<String, Object> getSlaAgentRanking(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rows = workOrderMapper.countSlaAgentRanking(start, end);

        List<Map<String, Object>> ranking = new ArrayList<>();
        int rankNumber = 1;
        for (Map<String, Object> row : rows) {
            long agentId = toLong(row.get(KEY_AGENT_ID));
            int totalWorkOrders = toInt(row.get(KEY_TOTAL_WORK_ORDERS));
            int slaCompliantCount = toInt(row.get("slaCompliantCount"));
            double slaComplianceRate = totalWorkOrders > 0
                    ? Math.round(slaCompliantCount * 10000.0 / totalWorkOrders) / 100.0
                    : 0.0;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(KEY_AGENT_ID, agentId);
            entry.put("totalWorkOrders", totalWorkOrders);
            entry.put("slaCompliantCount", slaCompliantCount);
            entry.put("slaComplianceRate", slaComplianceRate);
            entry.put("rank", rankNumber++);
            ranking.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", ranking);
        return result;
    }

    public byte[] exportCsv(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> trend = getTrendStats(startDate, endDate);
        StringBuilder sb = new StringBuilder();
        sb.append("\u65e5\u671f,\u54a8\u8be2\u91cf,\u5e73\u5747\u6ee1\u610f\u5ea6\n");
        List<String> dates = castList(trend.get(KEY_DATES));
        List<Integer> counts = castIntList(trend.get(KEY_COUNTS));
        List<Double> avgs = castDoubleList(trend.get("avgSatisfactions"));
        for (int i = 0; i < dates.size(); i++) {
            sb.append(dates.get(i)).append(",").append(counts.get(i)).append(",").append(avgs.get(i)).append("\n");
        }
        return sb.toString().getBytes();
    }

    /* -------------------- helpers -------------------- */

    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private Double calculateAvgSatisfaction(List<Map<String, Object>> dist) {
        long total = 0;
        long sum = 0;
        for (Map<String, Object> row : dist) {
            int level = toInt(row.get("satisfactionLevel"));
            int count = toInt(row.get(KEY_TOTAL_COUNT));
            if (level > 0) {
                total += count;
                sum += (long) level * count;
            }
        }
        return total > 0 ? Math.round(sum * 100.0 / total) / 100.0 : null;
    }

    private Map<String, Integer> buildSatDistribution(List<Map<String, Object>> rows) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(String.valueOf(i), 0);
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.getOrDefault("satisfactionLevel", ""));
            dist.put(key, toInt(row.get(KEY_TOTAL_COUNT)));
        }
        return dist;
    }

    private Map<String, Integer> toDateMap(List<Map<String, Object>> rows, String countKey) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = String.valueOf(row.getOrDefault(KEY_STAT_DATE, ""));
            map.put(date, toInt(row.get(countKey)));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object obj) {
        return (List<String>) (obj instanceof List ? obj : Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private List<Integer> castIntList(Object obj) {
        return (List<Integer>) (obj instanceof List ? obj : Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private List<Double> castDoubleList(Object obj) {
        return (List<Double>) (obj instanceof List ? obj : Collections.emptyList());
    }

    private void parseBizTagTrendRow(Map<String, Object> row,
                                      Map<String, Map<String, Map<String, Object>>> grouped) {
        String date = String.valueOf(row.getOrDefault(KEY_STAT_DATE, ""));
        String bizTag = String.valueOf(row.getOrDefault(KEY_BIZ_TAG, ""));
        grouped.computeIfAbsent(date, k -> new LinkedHashMap<>()).put(bizTag, row);
    }
}
