package com.example.backend.interfaces.controller;

import com.example.backend.application.dto.AgentDto;
import com.example.backend.application.dto.CreateAgentCommand;
import com.example.backend.application.dto.UpdateAgentCommand;
import com.example.backend.application.service.AgentManagementApplicationService;
import com.example.backend.common.Result;
import com.example.backend.interfaces.security.Auditable;
import com.example.backend.interfaces.security.RequireRole;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
@RequireRole({"ADMIN"})
public class AgentManagementController {

    private final AgentManagementApplicationService agentManagementService;

    @GetMapping
    public Result<Map<String, Object>> listAgents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(agentManagementService.searchAgents(keyword, role, status, startDate, endDate, page, size));
    }

    @GetMapping("/{id}")
    public Result<AgentDto> getAgent(@PathVariable Long id) {
        return Result.success(agentManagementService.getAgent(id));
    }

    @PostMapping
    @Auditable(operation = "CREATE_AGENT", module = "AGENT_MGMT")
    public Result<AgentDto> createAgent(@Valid @RequestBody CreateRequest request) {
        CreateAgentCommand command = new CreateAgentCommand();
        command.setUsername(request.getUsername());
        command.setPassword(request.getPassword());
        command.setNickname(request.getNickname());
        command.setPhone(request.getPhone());
        command.setEmail(request.getEmail());
        command.setRoleName(request.getRoleName());
        command.setSkills(request.getSkills());
        return Result.success(agentManagementService.createAgent(command));
    }

    @PutMapping("/{id}")
    @Auditable(operation = "UPDATE_AGENT", module = "AGENT_MGMT")
    public Result<AgentDto> updateAgent(@PathVariable Long id, @RequestBody UpdateRequest request) {
        UpdateAgentCommand command = new UpdateAgentCommand();
        command.setNickname(request.getNickname());
        command.setPhone(request.getPhone());
        command.setEmail(request.getEmail());
        command.setPassword(request.getPassword());
        command.setStatus(request.getStatus());
        command.setRoleName(request.getRoleName());
        command.setSkills(request.getSkills());
        return Result.success(agentManagementService.updateAgent(id, command));
    }

    @DeleteMapping("/{id}")
    @Auditable(operation = "DELETE_AGENT", module = "AGENT_MGMT")
    public Result<Object> deleteAgent(@PathVariable Long id) {
        agentManagementService.deleteAgent(id);
        return Result.success(null);
    }

    @PostMapping("/batch-delete")
    @Auditable(operation = "BATCH_DELETE_AGENT", module = "AGENT_MGMT")
    public Result<Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("ids");
        if (rawIds == null || rawIds.isEmpty()) return Result.error(400, "ids\u4e0d\u80fd\u4e3a\u7a7a");
        List<Long> ids = rawIds.stream().map((Integer i) -> Long.valueOf(i)).toList();
        agentManagementService.batchDelete(ids);
        return Result.success(null);
    }

    @PostMapping("/batch-status")
    @Auditable(operation = "BATCH_UPDATE_STATUS", module = "AGENT_MGMT")
    public Result<Object> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("ids");
        Integer status = (Integer) body.get("status");
        if (rawIds == null || rawIds.isEmpty()) return Result.error(400, "ids\u4e0d\u80fd\u4e3a\u7a7a");
        List<Long> ids = rawIds.stream().map((Integer i) -> Long.valueOf(i)).toList();
        agentManagementService.batchUpdateStatus(ids, status);
        return Result.success(null);
    }

    @Data
    static class CreateRequest {
        @jakarta.validation.constraints.NotBlank private String username;
        @jakarta.validation.constraints.NotBlank private String password;
        private String nickname;
        private String phone;
        private String email;
        private String roleName;
        private List<String> skills;
    }

    @Data
    static class UpdateRequest {
        private String nickname;
        private String phone;
        private String email;
        private String password;
        private Integer status;
        private String roleName;
        private List<String> skills;
    }
}
