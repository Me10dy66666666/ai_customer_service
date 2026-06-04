package com.example.backend.interfaces.controller;

import com.example.backend.application.service.AdminStatsApplicationService;
import com.example.backend.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {
    private final AdminStatsApplicationService adminStatsApplicationService;

    @GetMapping("/daily")
    public Result<Map<String, Object>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return Result.success(adminStatsApplicationService.getDailyStats(startDate));
    }

    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminStatsApplicationService.getTrendStats(startDate, endDate));
    }

    @GetMapping("/work-orders")
    public Result<Map<String, Object>> workOrders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminStatsApplicationService.getWorkOrderStats(startDate, endDate));
    }

    @GetMapping("/conversion")
    public Result<Map<String, Object>> conversion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminStatsApplicationService.getConversionStats(startDate, endDate));
    }

    @GetMapping("/knowledge-base-effect")
    public Result<Map<String, Object>> knowledgeBaseEffect(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminStatsApplicationService.getKnowledgeBaseEffectStats(startDate, endDate));
    }

    @GetMapping("/ai-resolution-rate")
    public Result<Map<String, Object>> aiResolutionRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminStatsApplicationService.getAiResolutionStats(startDate, endDate));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] csv = adminStatsApplicationService.exportCsv(startDate, endDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("report-" + startDate + "-" + endDate + ".csv").build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/sla/overview")
    public Result<Map<String, Object>> slaOverview(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return Result.success(adminStatsApplicationService.getSlaOverview(start, end));
    }

    @GetMapping("/sla/trend-by-biz-tag")
    public Result<Map<String, Object>> slaTrendByBizTag(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return Result.success(adminStatsApplicationService.getSlaTrendByBizTag(start, end));
    }

    @GetMapping("/sla/agent-ranking")
    public Result<Map<String, Object>> slaAgentRanking(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return Result.success(adminStatsApplicationService.getSlaAgentRanking(start, end));
    }
}
