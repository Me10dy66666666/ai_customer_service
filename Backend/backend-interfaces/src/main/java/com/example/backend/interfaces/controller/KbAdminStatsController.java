package com.example.backend.interfaces.controller;

import com.example.backend.application.service.KbAdminStatsApplicationService;
import com.example.backend.common.Result;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/stats")
@RequiredArgsConstructor
public class KbAdminStatsController {

    private final KbAdminStatsApplicationService kbAdminStatsApplicationService;

    @GetMapping("/reviewer/daily")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> myDailyReview(
            @RequestParam String reviewedBy,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(kbAdminStatsApplicationService.getMyReviewStats(reviewedBy, date));
    }

    @GetMapping("/reviewer/trend")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> myReviewTrend(
            @RequestParam String reviewedBy,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(kbAdminStatsApplicationService.getMyReviewTrend(reviewedBy, startDate, endDate));
    }

    @GetMapping("/reviewer/monthly")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> myMonthlyReview(
            @RequestParam String reviewedBy,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(kbAdminStatsApplicationService.getMyMonthlyTotal(reviewedBy, startDate, endDate));
    }

    @GetMapping("/kb-health")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> kbHealth() {
        return Result.success(kbAdminStatsApplicationService.getKbHealthMetrics());
    }

    @GetMapping("/kb-health-trend")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> kbHealthTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(kbAdminStatsApplicationService.getKbHealthTrend(startDate, endDate));
    }

    @GetMapping("/doc-status-dist")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> docStatusDistribution() {
        return Result.success(kbAdminStatsApplicationService.getDocStatusDistribution());
    }

    @GetMapping("/kb-effect-trend")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> kbEffectTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(kbAdminStatsApplicationService.getKbEffectTrend(startDate, endDate));
    }

    @GetMapping("/hot-search-words")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> hotSearchWords(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(kbAdminStatsApplicationService.getHotSearchWords(startDate, endDate, limit));
    }

    @GetMapping("/recent-reviews")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> recentReviews(
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(kbAdminStatsApplicationService.getRecentReviews(limit));
    }

    @GetMapping("/zero-result-words")
    @RequireRole({"KB_ADMIN", "ADMIN"})
    public Result<Map<String, Object>> zeroResultWords(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(kbAdminStatsApplicationService.getZeroResultWords(limit));
    }
}
