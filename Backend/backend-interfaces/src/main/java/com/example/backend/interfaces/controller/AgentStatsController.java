package com.example.backend.interfaces.controller;

import com.example.backend.application.service.AgentStatsApplicationService;
import com.example.backend.common.Result;
import com.example.backend.interfaces.security.RequireRole;
import com.example.backend.infrastructure.persistence.entity.User;
import com.example.backend.infrastructure.persistence.mapper.UserMapper;
import com.example.backend.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/agent/stats")
@RequiredArgsConstructor
public class AgentStatsController {

    private final AgentStatsApplicationService agentStatsApplicationService;
    private final UserMapper userMapper;

    @GetMapping("/mine/daily")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> myDaily(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(agentStatsApplicationService.getMyDailyStats(effectiveAgentId(agentId), date));
    }

    @GetMapping("/mine/satisfaction")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> mySatisfaction(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(agentStatsApplicationService.getMySatisfactionDist(effectiveAgentId(agentId), date));
    }

    @GetMapping("/mine/trend")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> myTrend(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(agentStatsApplicationService.getMyTrend(effectiveAgentId(agentId), startDate, endDate));
    }

    @GetMapping("/team/ranking")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> teamRanking(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(agentStatsApplicationService.getTeamRanking(effectiveAgentId(agentId), startDate, endDate));
    }

    @GetMapping("/team/average")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> teamAverage(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        return Result.success(agentStatsApplicationService.getTeamAverage(effectiveAgentId(agentId), startDate, endDate));
    }

    @GetMapping("/mine/monthly")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> myMonthly(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(agentStatsApplicationService.getMyMonthlyTotal(effectiveAgentId(agentId), startDate, endDate));
    }

    @GetMapping("/mine/workorder")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> myWorkOrder(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(agentStatsApplicationService.getMyWorkOrderStats(effectiveAgentId(agentId), startDate, endDate));
    }

    @GetMapping("/mine/workorder/satisfaction")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> myWorkOrderSatisfaction(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "all") String workOrderType) {
        return Result.success(agentStatsApplicationService.getMyWorkOrderSatisfaction(effectiveAgentId(agentId), startDate, endDate, workOrderType));
    }

    @GetMapping("/sla/overview")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> slaOverview(
            @RequestParam Long agentId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return Result.success(agentStatsApplicationService.getMySlaOverview(effectiveAgentId(agentId), start, end));
    }

    @GetMapping("/sla/trend")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> slaTrend(
            @RequestParam Long agentId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return Result.success(agentStatsApplicationService.getMySlaTrend(effectiveAgentId(agentId), start, end));
    }

    @GetMapping("/sla/team-ranking")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> slaTeamRanking(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return Result.success(agentStatsApplicationService.getTeamSlaRanking(start, end));
    }

    @GetMapping("/sla/detail")
    @RequireRole({"AGENT", "ADMIN"})
    public Result<Map<String, Object>> slaDetail(@RequestParam Long workOrderId) {
        return Result.success(agentStatsApplicationService.getSlaDetail(workOrderId));
    }

    private Long effectiveAgentId(Long requestedAgentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (admin) {
            return requestedAgentId;
        }
        if (authentication == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        User currentUser = userMapper.findByUsername(authentication.getName());
        if (currentUser == null) {
            throw new UnauthorizedException("Current user does not exist");
        }
        return currentUser.getId();
    }
}
