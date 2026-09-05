package com.example.backend.interfaces.controller;

import com.example.backend.application.service.OrderApplicationService;
import com.example.backend.application.service.WorkOrderApplicationService;
import com.example.backend.common.Result;
import com.example.backend.common.service.RedisService;
import com.example.backend.domain.order.model.HistoricalOrder;
import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.infrastructure.persistence.entity.User;
import com.example.backend.infrastructure.persistence.mapper.UserMapper;
import com.example.backend.infrastructure.security.JwtUtils;
import com.example.backend.infrastructure.observability.AgentRuntimeMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolGatewayControllerTest {

    private final JwtUtils jwtUtils = new JwtUtils(
            "test-only-agent-capability-signing-secret-2026", 86_400_000L);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OrderApplicationService orderService = mock(OrderApplicationService.class);
    private final WorkOrderApplicationService workOrderService = mock(WorkOrderApplicationService.class);
    private final RedisService redisService = mock(RedisService.class);
    private final AgentRuntimeMetrics agentRuntimeMetrics = mock(AgentRuntimeMetrics.class);
    private final AgentToolGatewayController controller = new AgentToolGatewayController(
            jwtUtils, userMapper, orderService, workOrderService, redisService, agentRuntimeMetrics);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuedCapabilityIsBoundToCurrentUserSessionAndAllowedScopes() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer-a", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        AgentToolGatewayController.CapabilityRequest request =
                new AgentToolGatewayController.CapabilityRequest();
        request.setSessionId("session-a");
        request.setScopes(Set.of("order:read:self", "admin:anything"));

        Result<Map<String, Object>> result = controller.issueCapability(request);

        String token = (String) result.getData().get("capabilityToken");
        JwtUtils.AgentCapability capability = jwtUtils
                .parseAgentCapabilityToken(token, "order:read:self").orElseThrow();
        assertThat(capability.username()).isEqualTo("customer-a");
        assertThat(capability.sessionId()).isEqualTo("session-a");
        assertThat(capability.scopes()).containsExactly("order:read:self");
        assertThat(jwtUtils.parseAgentCapabilityToken(token, "admin:anything")).isEmpty();
    }

    @Test
    void orderToolDerivesUserFromCapabilityInsteadOfRequestArguments() {
        User user = activeUser(77L, "customer-a");
        when(userMapper.findByUsername("customer-a")).thenReturn(user);
        when(orderService.getOrdersByUserId(77L)).thenReturn(List.of(new HistoricalOrder()));
        MockHttpServletRequest request = capabilityRequest(
                jwtUtils.generateAgentCapabilityToken(
                        "customer-a", "session-a", Set.of("order:read:self")));

        Result<List<HistoricalOrder>> result = controller.getOwnOrders(request);

        assertThat(result.getData()).hasSize(1);
        verify(orderService).getOrdersByUserId(77L);
    }

    @Test
    void workOrderToolOnlyStoresAUserBoundConfirmationProposal() {
        User user = activeUser(77L, "customer-a");
        when(userMapper.findByUsername("customer-a")).thenReturn(user);
        MockHttpServletRequest request = capabilityRequest(
                jwtUtils.generateAgentCapabilityToken(
                        "customer-a", "session-a", Set.of("work_order:propose:self")));
        AgentToolGatewayController.WorkOrderProposalRequest proposal =
                new AgentToolGatewayController.WorkOrderProposalRequest();
        proposal.setTitle("退款咨询");
        proposal.setDescription("需要人工确认");

        Result<Map<String, Object>> result = controller.proposeWorkOrder(request, proposal);

        assertThat(result.getData().get("requiresConfirmation")).isEqualTo(true);
        verify(redisService).set(
                org.mockito.ArgumentMatchers.startsWith("agent:work-order-proposal:"),
                org.mockito.ArgumentMatchers.argThat(value -> value instanceof Map<?, ?> map
                        && java.util.Objects.equals(77L, map.get("userId"))
                        && java.util.Objects.equals("customer-a", map.get("username"))),
                eq(10L), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    void confirmationRequiresTheAuthenticatedProposalOwnerAndConsumesOnce() {
        User user = activeUser(77L, "customer-a");
        when(userMapper.findByUsername("customer-a")).thenReturn(user);
        when(redisService.get("agent:work-order-proposal:proposal-1")).thenReturn(Map.of(
                "userId", 77L,
                "sessionId", "session-a",
                "title", "退款咨询",
                "description", "需要人工确认",
                "type", "after_sales",
                "priority", "medium"));
        WorkOrder created = WorkOrder.create(77L, "退款咨询", "需要人工确认", "after_sales", "medium");
        created.setId(101L);
        when(redisService.getAndDelete("agent:work-order-proposal:proposal-1")).thenReturn(Map.of(
                "userId", 77L,
                "sessionId", "session-a",
                "title", "退款咨询",
                "description", "需要人工确认",
                "type", "after_sales",
                "priority", "medium"));
        when(workOrderService.createCustomerConfirmedWorkOrder(
                77L, "session-a", "退款咨询", "需要人工确认", "after_sales", "medium"))
                .thenReturn(created);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer-a", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        Result<Map<String, Object>> result = controller.confirmWorkOrder("proposal-1");

        assertThat(result.getData()).containsEntry("workOrderId", 101L);
        verify(workOrderService).createCustomerConfirmedWorkOrder(
                77L, "session-a", "退款咨询", "需要人工确认", "after_sales", "medium");
    }

    private MockHttpServletRequest capabilityRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Agent-Session-Id", "session-a");
        return request;
    }

    private User activeUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(1);
        return user;
    }
}
