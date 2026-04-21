package com.example.backend.service;

import com.example.backend.mapper.ConsultationLogMapper;
import com.example.backend.mapper.WorkOrderMapper;
import com.example.backend.mapper.HistoricalOrderMapper;
import com.example.backend.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final ConsultationLogMapper consultationLogMapper;
    private final WorkOrderMapper workOrderMapper;
    private final HistoricalOrderMapper orderMapper;
    private final DocumentMapper documentMapper;

    public Map<String, Object> getDailyStats(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 1. 今日咨询总量
        long totalChats = consultationLogMapper.countByCreateTimeBetween(startOfDay, endOfDay);

        // 2. 满意度分布
        List<Map<String, Object>> satisfactionCounts = consultationLogMapper.countSatisfactionByDate(startOfDay, endOfDay);
        Map<String, Long> satisfactionMap = new HashMap<>();
        // 初始化
        satisfactionMap.put("非常满意", 0L);
        satisfactionMap.put("满意", 0L);
        satisfactionMap.put("一般", 0L);
        satisfactionMap.put("不满意", 0L);
        satisfactionMap.put("非常不满意", 0L);
        
        long totalScore = 0;
        long scoreCount = 0;

        for (Map<String, Object> row : satisfactionCounts) {
            Integer score = row.get("satisfactionLevel") == null ? null : ((Number) row.get("satisfactionLevel")).intValue();
            Long count = row.get("totalCount") == null ? 0L : ((Number) row.get("totalCount")).longValue();
            if (score != null) {
                String label = getSatisfactionLabel(score);
                satisfactionMap.put(label, satisfactionMap.getOrDefault(label, 0L) + count);
                
                // 数据库：1-非常满意，2-满意，3-一般，4-不满意
                totalScore += score * count;
                scoreCount += count;
            }
        }

        double avgSatisfaction = scoreCount > 0 ? (double) totalScore / scoreCount : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("total_chats", totalChats);
        result.put("avg_satisfaction", String.format("%.1f", avgSatisfaction));
        result.put("satisfaction_dist", satisfactionMap);

        return result;
    }

    public Map<String, Object> getTrendStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> trendData = consultationLogMapper.countTrendWithSatisfactionByDate(start, end);
        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<String> avgSatisfactions = new ArrayList<>();

        // 补全日期范围内所有日期（无数据则为 0 和 "-"）
        List<LocalDate> allDates = startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());
        Map<LocalDate, Map<String, Object>> mapByDate = new HashMap<>();
        for (Map<String, Object> row : trendData) {
            Object dateValue = row.get("statDate");
            LocalDate d = dateValue instanceof java.sql.Date ? ((java.sql.Date) dateValue).toLocalDate() : LocalDate.parse(dateValue.toString());
            mapByDate.put(d, row);
        }
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (LocalDate d : allDates) {
            dates.add(d.format(fmt));
            Map<String, Object> row = mapByDate.get(d);
            if (row != null) {
                long count = row.get("totalCount") == null ? 0L : ((Number) row.get("totalCount")).longValue();
                counts.add(count);
                Object avg = row.get("avgScore");
                if (avg != null && count > 0) {
                    double v = ((Number) avg).doubleValue();
                    avgSatisfactions.add(String.format("%.1f", v));
                } else {
                    avgSatisfactions.add("-");
                }
            } else {
                counts.add(0L);
                avgSatisfactions.add("-");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("counts", counts);
        result.put("avgSatisfactions", avgSatisfactions);
        return result;
    }

    /**
     * 工单分析统计：按状态汇总、按日趋势
     */
    public Map<String, Object> getWorkOrderStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> byStatus = workOrderMapper.countByStatusBetween(start, end);
        List<Map<String, Object>> byDate = workOrderMapper.countWorkOrderTrendByDate(start, end);

        Map<String, Long> statusMap = new LinkedHashMap<>();
        statusMap.put("pending", 0L);
        statusMap.put("processing", 0L);
        statusMap.put("completed", 0L);
        statusMap.put("cancelled", 0L);
        for (Map<String, Object> row : byStatus) {
            String status = String.valueOf(row.get("statusKey"));
            Long cnt = row.get("totalCount") == null ? 0L : ((Number) row.get("totalCount")).longValue();
            statusMap.put(status, cnt);
        }

        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (Map<String, Object> row : byDate) {
            Object dateValue = row.get("statDate");
            LocalDate d = dateValue instanceof java.sql.Date ? ((java.sql.Date) dateValue).toLocalDate() : LocalDate.parse(dateValue.toString());
            dates.add(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
            counts.add(row.get("totalCount") == null ? 0L : ((Number) row.get("totalCount")).longValue());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("byStatus", statusMap);
        result.put("dates", dates);
        result.put("counts", counts);
        return result;
    }

    /**
     * 导出咨询统计报告（CSV 格式的每日汇总）
     */
    public String exportConsultationReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        List<Map<String, Object>> data = consultationLogMapper.countTrendWithSatisfactionByDate(start, end);
        StringBuilder csv = new StringBuilder();
        csv.append("日期,咨询量,平均满意度\n");
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (Map<String, Object> row : data) {
            Object dateValue = row.get("statDate");
            LocalDate d = dateValue instanceof java.sql.Date ? ((java.sql.Date) dateValue).toLocalDate() : LocalDate.parse(dateValue.toString());
            long count = row.get("totalCount") == null ? 0L : ((Number) row.get("totalCount")).longValue();
            String avg = "-";
            if (row.get("avgScore") != null && count > 0) {
                avg = String.format("%.1f", ((Number) row.get("avgScore")).doubleValue());
            }
            csv.append(d.format(fmt)).append(",").append(count).append(",").append(avg).append("\n");
        }
        return csv.toString();
    }

    /**
     * 转化率分析：统计咨询后下单的用户比例
     */
    public Map<String, Object> getConversionStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // 获取所有咨询用户（去重）
        List<Map<String, Object>> consultUsers = consultationLogMapper.countUniqueUsersByDate(start, end);
        Map<LocalDate, Long> consultUserMap = new HashMap<>();
        long totalConsultUsers = 0;
        for (Map<String, Object> row : consultUsers) {
            Object dateValue = row.get("statDate");
            LocalDate d = dateValue instanceof java.sql.Date ? ((java.sql.Date) dateValue).toLocalDate() : LocalDate.parse(dateValue.toString());
            consultUserMap.put(d, row.get("userCount") == null ? 0L : ((Number) row.get("userCount")).longValue());
            totalConsultUsers += consultUserMap.get(d);
        }

        // 获取咨询后下单的用户（咨询时间在前，下单时间在后）
        List<Map<String, Object>> convertedUsers = consultationLogMapper.countConvertedUsersByDate(start, end);
        Map<LocalDate, Long> convertedUserMap = new HashMap<>();
        long totalConvertedUsers = 0;
        for (Map<String, Object> row : convertedUsers) {
            Object dateValue = row.get("statDate");
            LocalDate d = dateValue instanceof java.sql.Date ? ((java.sql.Date) dateValue).toLocalDate() : LocalDate.parse(dateValue.toString());
            convertedUserMap.put(d, row.get("userCount") == null ? 0L : ((Number) row.get("userCount")).longValue());
            totalConvertedUsers += convertedUserMap.get(d);
        }

        // 计算整体转化率
        double overallConversionRate = totalConsultUsers > 0 ? (double) totalConvertedUsers / totalConsultUsers * 100 : 0;

        // 按日期计算每日转化率
        List<String> dates = new ArrayList<>();
        List<Long> consultCounts = new ArrayList<>();
        List<Long> convertedCounts = new ArrayList<>();
        List<String> conversionRates = new ArrayList<>();

        List<LocalDate> allDates = startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (LocalDate d : allDates) {
            dates.add(d.format(fmt));
            long consult = consultUserMap.getOrDefault(d, 0L);
            long converted = convertedUserMap.getOrDefault(d, 0L);
            consultCounts.add(consult);
            convertedCounts.add(converted);
            double rate = consult > 0 ? (double) converted / consult * 100 : 0;
            conversionRates.add(String.format("%.2f", rate));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("overallConversionRate", String.format("%.2f", overallConversionRate));
        result.put("totalConsultUsers", totalConsultUsers);
        result.put("totalConvertedUsers", totalConvertedUsers);
        result.put("dates", dates);
        result.put("consultCounts", consultCounts);
        result.put("convertedCounts", convertedCounts);
        result.put("conversionRates", conversionRates);

        return result;
    }

    /**
     * 知识库效果分析：统计知识库文档的使用情况和效果
     */
    public Map<String, Object> getKnowledgeBaseEffectStats(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // 获取知识库文档总数
        long totalDocuments = documentMapper.countAll();
        long enabledDocuments = documentMapper.countByStatus(1);

        // 获取咨询中使用知识库的情况
        List<Map<String, Object>> kbUsage = consultationLogMapper.countKbUsageByDate(start, end);
        Map<LocalDate, Long> kbUsageMap = new HashMap<>();
        long totalKbUsages = 0;
        for (Map<String, Object> row : kbUsage) {
            Object dateValue = row.get("statDate");
            LocalDate d = dateValue instanceof java.sql.Date ? ((java.sql.Date) dateValue).toLocalDate() : LocalDate.parse(dateValue.toString());
            kbUsageMap.put(d, row.get("usageCount") == null ? 0L : ((Number) row.get("usageCount")).longValue());
            totalKbUsages += kbUsageMap.get(d);
        }

        // 获取咨询总数
        long totalConsultations = consultationLogMapper.countByCreateTimeBetween(start, end);
        
        // 计算知识库使用率（使用知识库的咨询占总咨询的比例）
        double kbUsageRate = totalConsultations > 0 ? (double) totalKbUsages / totalConsultations * 100 : 0;

        // 按日期统计知识库使用情况
        List<String> dates = new ArrayList<>();
        List<Long> usageCounts = new ArrayList<>();

        List<LocalDate> allDates = startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (LocalDate d : allDates) {
            dates.add(d.format(fmt));
            usageCounts.add(kbUsageMap.getOrDefault(d, 0L));
        }

        // 获取按类别统计的文档数量
        List<Map<String, Object>> docsByCategory = documentMapper.countByCategory();
        Map<String, Long> categoryMap = new LinkedHashMap<>();
        for (Map<String, Object> row : docsByCategory) {
            String category = String.valueOf(row.get("category"));
            Long count = row.get("count") == null ? 0L : ((Number) row.get("count")).longValue();
            categoryMap.put(category, count);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalDocuments", totalDocuments);
        result.put("enabledDocuments", enabledDocuments);
        result.put("totalKbUsages", totalKbUsages);
        result.put("totalConsultations", totalConsultations);
        result.put("kbUsageRate", String.format("%.2f", kbUsageRate));
        result.put("dates", dates);
        result.put("usageCounts", usageCounts);
        result.put("docsByCategory", categoryMap);

        return result;
    }

    private String getSatisfactionLabel(int score) {
        // 与数据库一致：1-非常满意，2-满意，3-一般，4-不满意
        switch (score) {
            case 1: return "非常满意";
            case 2: return "满意";
            case 3: return "一般";
            case 4: return "不满意";
            case 5: return "非常不满意";
            default: return "未知";
        }
    }
}
