package com.example.backend.interfaces.controller;

import com.example.backend.application.service.KnowledgeStatsApplicationService;
import com.example.backend.common.Result;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/stats")
@RequiredArgsConstructor
public class KnowledgeStatsController {

    private final KnowledgeStatsApplicationService statsService;

    @GetMapping("/dashboard")
    @RequireRole({"ADMIN"})
    public Result<Map<String, Object>> dashboard(@RequestParam(required = false) String start,
                                                  @RequestParam(required = false) String end) {
        LocalDate endDate = end != null ? LocalDate.parse(end) : LocalDate.now();
        LocalDate startDate = start != null ? LocalDate.parse(start) : endDate.minusMonths(1);
        return Result.success(statsService.getDashboard(startDate, endDate));
    }
}
