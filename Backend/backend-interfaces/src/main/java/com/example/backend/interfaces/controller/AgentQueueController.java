package com.example.backend.interfaces.controller;

import com.example.backend.application.dto.AgentSessionContext;
import com.example.backend.application.service.AgentSessionApplicationService;
import com.example.backend.application.service.SessionDispatchService;
import com.example.backend.common.Result;
import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客服待接入队列 REST 接口。
 * 用于前端页面刷新/重连后冷启动拉取全量活跃会话，
 * 配合 WebSocket agent_queue_notify 增量推送实现队列持久化视图。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "AGENT"})
public class AgentQueueController {

    private final SessionStatePort sessionStatePort;
    private final AgentSessionApplicationService agentSessionApplicationService;
    private final SessionDispatchService sessionDispatchService;

    /**
     * 获取当前全部 WAITING 等待中的会话详情列表。
     * 每条记录包含 sessionId、userId、intent、position、estimatedWait。
     *
     * @param agentId 可选客服ID，传入时只返回派发给该客服 + 未派发的公共池会话
     * @return 等待队列全量快照（按 FIFO 入队时间升序排列）
     */
    @GetMapping("/queue/pending")
    public Result<List<Map<String, Object>>> getPendingQueue(@RequestParam(required = false) Long agentId) {
        List<Map<String, Object>> waitingSessions = sessionStatePort.getAllWaitingSessionDetails();
        if (agentId != null && waitingSessions != null) {
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> session : waitingSessions) {
                String sid = (String) session.get("sessionId");
                if (sid == null) continue;
                Long dispatchedAgent = sessionDispatchService.getDispatchedAgent(sid);
                if (dispatchedAgent == null || dispatchedAgent.equals(agentId)) {
                    filtered.add(session);
                }
            }
            waitingSessions = filtered;
        }
        return Result.success(waitingSessions);
    }

    /**
     * 获取客服当前所有活跃会话的完整上下文。
     * 包含 WAITING 等待队列会话 + 该客服已认领的 HUMAN 会话，
     * 每条附带 AI 对话记录、优先级/标签/摘要（从已生成的 SYSTEM 消息中提取）。
     *
     * @param agentId 客服ID
     * @return 活跃会话完整上下文列表（WAITING 按入队时间升序，HUMAN 追加其后）
     */
    @GetMapping("/sessions/active")
    public Result<List<AgentSessionContext>> getActiveSessions(@RequestParam Long agentId) {
        return Result.success(agentSessionApplicationService.getActiveSessions(agentId));
    }
}
