package com.example.backend.interfaces.controller;

import com.example.backend.application.service.AgentManagementApplicationService;
import com.example.backend.common.Result;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@RequireRole({"ADMIN"})
public class UserManagementController {

    private final AgentManagementApplicationService agentManagementService;

    @GetMapping
    public Result<Map<String, Object>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(agentManagementService.searchRegularUsers(keyword, role, status, startDate, endDate, page, size));
    }

    @PutMapping("/{id}/status")
    public Result<Object> toggleUserStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        agentManagementService.updateUserStatus(id, status);
        return Result.success(null);
    }
}
