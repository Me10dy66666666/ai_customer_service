package com.example.backend.interfaces.controller;

import com.example.backend.application.service.OrderApplicationService;
import com.example.backend.common.Result;
import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.common.service.RedisService;
import com.example.backend.domain.order.model.HistoricalOrder;
import com.example.backend.infrastructure.persistence.entity.User;
import com.example.backend.infrastructure.persistence.mapper.UserMapper;
import com.example.backend.infrastructure.security.JwtUtils;
import com.example.backend.interfaces.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Narrow policy-enforcement point between an Agent runtime and Java domain services.
 * Model-visible requests never choose the authorization subject.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentToolGatewayController {

    private static final String ORDER_READ_SCOPE = "order:read:self";
    private static final String WORK_ORDER_PROPOSE_SCOPE = "work_order:propose:self";
    private static final Set<String> CUSTOMER_SCOPES = Set.of(
            ORDER_READ_SCOPE, WORK_ORDER_PROPOSE_SCOPE, "knowledge:read");
    private static final String PROPOSAL_PREFIX = "agent:work-order-proposal:";

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final OrderApplicationService orderApplicationService;
    private final RedisService redisService;

    @PostMapping("/capabilities")
    @RequireRole({"USER", "VIP"})
    public Result<Map<String, Object>> issueCapability(@Valid @RequestBody CapabilityRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Authentication is required");
        }
        Set<String> scopes = request.getScopes().stream()
                .filter(CUSTOMER_SCOPES::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (scopes.isEmpty()) {
            throw new ForbiddenException("No allowed Agent scope requested");
        }
        String token = jwtUtils.generateAgentCapabilityToken(
                authentication.getName(), request.getSessionId(), scopes);
        return Result.success(Map.of(
                "capabilityToken", token,
                "expiresInSeconds", 300,
                "scopes", scopes));
    }

    @GetMapping("/tools/orders")
    public Result<List<HistoricalOrder>> getOwnOrders(HttpServletRequest request) {
        GatewayPrincipal principal = requireCapability(request, ORDER_READ_SCOPE);
        return Result.success(orderApplicationService.getOrdersByUserId(principal.userId()));
    }

    @PostMapping("/tools/work-orders/proposals")
    public Result<Map<String, Object>> proposeWorkOrder(
            HttpServletRequest request,
            @Valid @RequestBody WorkOrderProposalRequest proposal) {
        GatewayPrincipal principal = requireCapability(request, WORK_ORDER_PROPOSE_SCOPE);
        String proposalId = UUID.randomUUID().toString();
        String type = proposal.getType() == null ? "after_sales" : proposal.getType();
        String priority = proposal.getPriority() == null ? "medium" : proposal.getPriority();
        Map<String, Object> storedProposal = Map.of(
                "proposalId", proposalId,
                "userId", principal.userId(),
                "username", principal.username(),
                "sessionId", principal.sessionId() == null ? "" : principal.sessionId(),
                "title", proposal.getTitle(),
                "description", proposal.getDescription(),
                "type", type,
                "priority", priority);
        redisService.set(PROPOSAL_PREFIX + proposalId, storedProposal, 10L, TimeUnit.MINUTES);
        return Result.success(Map.of(
                "proposalId", proposalId,
                "title", proposal.getTitle(),
                "priority", priority,
                "requiresConfirmation", true));
    }

    private GatewayPrincipal requireCapability(HttpServletRequest request, String scope) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ForbiddenException("Agent capability is required");
        }
        JwtUtils.AgentCapability capability = jwtUtils
                .parseAgentCapabilityToken(authorization.substring(7), scope)
                .orElseThrow(() -> new ForbiddenException("Invalid or insufficient Agent capability"));
        User user = userMapper.findByUsername(capability.username());
        if (user == null || user.getId() == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ForbiddenException("Capability subject is unavailable");
        }
        return new GatewayPrincipal(user.getId(), user.getUsername(), capability.sessionId());
    }

    private record GatewayPrincipal(Long userId, String username, String sessionId) {}

    @Data
    public static class CapabilityRequest {
        @NotBlank
        private String sessionId;
        @NotNull
        @Size(min = 1, max = 8)
        private Set<String> scopes;
    }

    @Data
    public static class WorkOrderProposalRequest {
        @NotBlank
        @Size(max = 200)
        private String title;
        @NotBlank
        @Size(max = 4000)
        private String description;
        private String type = "after_sales";
        private String priority = "medium";
    }
}
