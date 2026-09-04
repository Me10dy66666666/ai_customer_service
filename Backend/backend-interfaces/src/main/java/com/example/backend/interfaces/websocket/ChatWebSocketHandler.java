package com.example.backend.interfaces.websocket;

import com.example.backend.application.service.AgentSessionService;
import com.example.backend.application.service.ChatApplicationService;
import com.example.backend.application.service.ChatMessageService;
import com.example.backend.common.util.ConvertUtils;
import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.infrastructure.messaging.AgentBroadcaster;
import com.example.backend.infrastructure.messaging.MessageRouter;
import com.example.backend.infrastructure.messaging.RedisStreamAdapter;
import com.example.backend.interfaces.config.WebSocketAuthenticationInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Component
@SuppressWarnings("java:S1192")
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Set<String> AGENT_ACTIONS = Set.of(
            "register", "claim", "agent_message", "transfer_ai", "transfer_to_agent",
            "request_satisfaction", "close", "heartbeat");
    private final ChatApplicationService chatApplicationService;
    private final MessageRouter messageRouter;
    private final SessionStatePort sessionStatePort;
    private final AgentBroadcaster agentBroadcaster;
    private final ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper;
    private AgentSessionService.ChatTransferBridge transferBridge;
    private RedisStreamAdapter redisStreamAdapter;

    private final Map<String, Runnable> userListeners = new ConcurrentHashMap<>();
    private final Map<String, String> wsIdToSessionId = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final Map<WebSocketSession, Long> agentSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Consumer<Map<String, Object>>> agentSubscribers = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, List<Runnable>> agentListeners = new ConcurrentHashMap<>();

    private final Map<WebSocketSession, Queue<String>> sendQueues = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> drainingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final String hostname = resolveHostname();

    public ChatWebSocketHandler(ChatApplicationService chatApplicationService,
                                MessageRouter messageRouter,
                                SessionStatePort sessionStatePort,
                                AgentBroadcaster agentBroadcaster,
                                ChatMessageService chatMessageService,
                                ObjectMapper objectMapper) {
        this.chatApplicationService = chatApplicationService;
        this.messageRouter = messageRouter;
        this.sessionStatePort = sessionStatePort;
        this.agentBroadcaster = agentBroadcaster;
        this.chatMessageService = chatMessageService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setTransferBridge(AgentSessionService.ChatTransferBridge transferBridge) {
        this.transferBridge = transferBridge;
    }

    @Autowired(required = false)
    public void setRedisStreamAdapter(RedisStreamAdapter redisStreamAdapter) {
        this.redisStreamAdapter = redisStreamAdapter;
    }

    private static String resolveHostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (UnknownHostException e) { return "unknown"; }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String action = (String) payload.getOrDefault("action", "");

            if (handleAgentAction(session, payload, action)) return;

            sessionId = (String) payload.get("sessionId");
            if (!Objects.equals(sessionId, authenticatedChatSessionId(session))) {
                sendJson(session, Map.of("type", "error", "content", "会话凭据无效"));
                return;
            }
            Long userId = authenticatedUserId(session);
            Set<String> roles = authenticatedRoles(session);
            String content = (String) payload.get("content");

            startUserListenerIfNeeded(sessionId, session);
            final String capturedSessionId = sessionId;

            if ("register_session".equals(action)) {
                sendJson(session, Map.of("type", "registered", "content", "ok"));
                return;
            }

            if ("transfer_to_human".equals(action) && transferBridge != null) {
                if (messageRouter.isHumanSession(sessionId)) {
                    sendJson(session, Map.of(
                            "type", "agent_joined",
                            "content", "客服已接入，为您服务"
                    ));
                    return;
                }
                if (messageRouter.isWaitingSession(sessionId)) {
                    long position = sessionStatePort.getWaitQueuePosition(sessionId);
                    long estimatedWait = sessionStatePort.getEstimatedWaitTime(sessionId);
                    sendJson(session, Map.of(
                            "type", "waiting",
                            "content", "您已在排队中，前方还有 " + (position - 1) + " 位用户",
                            "position", position,
                            "estimatedWait", estimatedWait
                    ));
                    return;
                }
                String intent = (String) payload.getOrDefault("intent", "");
                transferBridge.transferToHuman(sessionId, userId, intent);
                long position = sessionStatePort.getWaitQueuePosition(sessionId);
                long estimatedWait = sessionStatePort.getEstimatedWaitTime(sessionId);
                sendJson(session, Map.of(
                        "type", "waiting",
                        "content", "您已进入排队，客服将尽快接入...",
                        "position", position,
                        "estimatedWait", estimatedWait
                ));
                chatMessageService.saveSystem(sessionId, "用户请求转人工，等待客服接入");
                return;
            }

            if ("workorder_contact".equals(action)) {
                Long workOrderId = parseLong(payload.get("workOrderId"));
                chatMessageService.saveUser(sessionId, userId, content);
                Map<String, Object> woMsg = new LinkedHashMap<>();
                woMsg.put("type", "workorder_contact");
                woMsg.put("sessionId", sessionId);
                woMsg.put("userId", userId);
                woMsg.put("content", content);
                woMsg.put("workOrderId", workOrderId);
                agentBroadcaster.broadcast(woMsg);
                sendJson(session, Map.of("type", "user_msg_sent"));
                return;
            }

            if ("cancel_waiting".equals(action)) {
                if (messageRouter.isWaitingSession(sessionId)) {
                    messageRouter.cancelWaiting(sessionId);
                    sendJson(session, Map.of(
                            "type", "cancelled_waiting",
                            "content", "已取消排队，返回AI服务"
                    ));
                } else {
                    sendJson(session, Map.of(
                            "type", "error",
                            "content", "当前不在排队状态"
                    ));
                }
                return;
            }

            if ("end_human".equals(action)) {
                if (messageRouter.isHumanSession(sessionId)) {
                    Long assignedAgentId = messageRouter.getAssignedAgent(sessionId);
                    messageRouter.transferBackToAi(sessionId);
                    sendJson(session, Map.of(
                            "type", "back_to_ai",
                            "content", "人工服务已结束，已切换回AI服务"
                    ));
                    sendJson(session, Map.of(
                            "type", "satisfaction_required",
                            "content", "请对本次服务进行评价"
                    ));
                    chatMessageService.saveSystem(sessionId, "人工服务已结束，已切换回AI服务");
                    if (assignedAgentId != null) {
                        for (var entry : agentSessions.entrySet()) {
                            if (assignedAgentId.equals(entry.getValue()) && entry.getKey().isOpen()) {
                                sendJson(entry.getKey(), Map.of(
                                        "type", "service_ended",
                                        "sessionId", sessionId,
                                        "content", "用户已结束服务"
                                ));
                                break;
                            }
                        }
                    }
                } else {
                    sendJson(session, Map.of(
                            "type", "error",
                            "content", "当前不在人工服务状态"
                    ));
                }
                return;
            }

            if (messageRouter.isHumanSession(sessionId)) {
                chatMessageService.saveUser(sessionId, userId, content);
                Long agentId = messageRouter.getAssignedAgent(sessionId);
                Map<String, Object> userMsg = new LinkedHashMap<>();
                userMsg.put("type", "user_msg");
                userMsg.put("sessionId", sessionId);
                userMsg.put("userId", userId);
                userMsg.put("content", content);
                boolean sent = false;
                for (var entry : agentSessions.entrySet()) {
                    if (agentId.equals(entry.getValue()) && entry.getKey().isOpen()) {
                        sendJson(entry.getKey(), userMsg);
                        sent = true;
                        break;
                    }
                }
                if (!sent && redisStreamAdapter != null && agentId != null) {
                    redisStreamAdapter.publishToStreamAsync(
                            RedisStreamAdapter.AGENT_STREAM_PREFIX + agentId, userMsg);
                }
                sendJson(session, Map.of("type", "user_msg_sent"));
                return;
            }

            if (messageRouter.isWaitingSession(sessionId)) {
                long position = sessionStatePort.getWaitQueuePosition(sessionId);
                long estimatedWait = sessionStatePort.getEstimatedWaitTime(sessionId);
                sendJson(session, Map.of(
                        "type", "waiting",
                        "content", "您正在排队中，前方还有 " + (position - 1) + " 位用户，预计等待 " + estimatedWait + " 秒",
                        "position", position,
                        "estimatedWait", estimatedWait
                ));
                return;
            }

            if (sessionStatePort.isAiBlocked(sessionId)) {
                if (messageRouter.isHumanSession(sessionId)) {
                    chatMessageService.saveUser(sessionId, userId, content);
                    Long agentId = messageRouter.getAssignedAgent(sessionId);
                    Map<String, Object> userMsg = new LinkedHashMap<>();
                    userMsg.put("type", "user_msg");
                    userMsg.put("sessionId", sessionId);
                    userMsg.put("userId", userId);
                    userMsg.put("content", content);
                    boolean sent = false;
                    for (var entry : agentSessions.entrySet()) {
                        if (agentId.equals(entry.getValue()) && entry.getKey().isOpen()) {
                            sendJson(entry.getKey(), userMsg);
                            sent = true;
                            break;
                        }
                    }
                    if (!sent && redisStreamAdapter != null && agentId != null) {
                        redisStreamAdapter.publishToStreamAsync(
                                RedisStreamAdapter.AGENT_STREAM_PREFIX + agentId, userMsg);
                    }
                    sendJson(session, Map.of("type", "user_msg_sent"));
                    sendJson(session, Map.of(
                            "type", "agent_joined",
                            "sessionId", sessionId,
                            "agentId", agentId != null ? agentId : 0));
                    return;
                }
                log.warn("AI blocked for session {}, message rerouted to human channel", sessionId);
                sendJson(session, Map.of(
                        "type", "blocked",
                        "content", "您正在与客服沟通中，AI 已暂停响应"));
                return;
            }

            sendConnected(session);

            CompletableFuture.runAsync(() ->
                chatApplicationService.processStreamingMessage(capturedSessionId, userId, roles, content, chunk ->
                    sendJson(session, Map.of("type", "chunk", "content", chunk))
                )
            ).thenRun(() ->
                sendJson(session, Map.of("type", "done", "content", ""))
            ).exceptionally(ex -> {
                log.error("AI streaming failed for session {}", capturedSessionId, ex);
                sendJson(session, Map.of("type", "error", "content", "系统繁忙，请稍后重试"));
                return null;
            });
        } catch (Exception e) {
            log.error("WebSocket message error for session {}", sessionId, e);
            sendError(session, "系统繁忙，请稍后重试");
        }
    }

    private boolean handleAgentAction(WebSocketSession session, Map<String, Object> payload, String action) {
        if (AGENT_ACTIONS.contains(action) && !authenticatedRoles(session).contains("AGENT")) {
            sendJson(session, Map.of("type", "register_failed",
                    "content", "未认证的客服连接"));
            return true;
        }
        return switch (action) {
            case "register" -> {
                Long agentId = authenticatedUserId(session);
                if (agentId != null) {
                    agentSessions.put(session, agentId);
                    sessionStatePort.markAgentOnline(agentId);
                    Consumer<Map<String, Object>> sub = data -> sendJson(session, data);
                    agentSubscribers.put(session, sub);
                    agentBroadcaster.subscribe(sub);
                    startPerAgentListener(session, agentId);
                    startQueueListener(session);
                    sendJson(session, Map.of("type", "registered",
                            "agentId", agentId,
                            "queueSize", messageRouter.getWaitingQueueSize()));
                    log.info("Agent {} registered via WS", agentId);
                } else {
                    sendJson(session, Map.of("type", "register_failed",
                            "content", "无效的客服ID，请重新登录"));
                }
                yield true;
            }
            case "claim" -> {
                String sessionId = (String) payload.get("sessionId");
                Long agentId = agentSessions.get(session);
                if (sessionId != null && agentId != null) {
                    boolean success = messageRouter.claimSession(sessionId, agentId);
                    if (success) {
                        sendJson(session, Map.of("type", "claimed", "sessionId", sessionId));
                        sendToUser(sessionId, Map.of("type", "agent_joined",
                                "sessionId", sessionId,
                                "agentId", agentId));
                        chatMessageService.saveSystem(sessionId, "客服已接入，为您服务");
                        // 广播通知所有客服该会话已被认领，使其从本地队列中移除
                        agentBroadcaster.broadcast(Map.of(
                                "type", "session_claimed",
                                "sessionId", sessionId,
                                "claimedByAgentId", agentId));
                    } else {
                        sendJson(session, Map.of("type", "claim_failed",
                                "sessionId", sessionId,
                                "content", "该会话已被其他客服认领"));
                    }
                }
                yield true;
            }
            case "agent_message" -> {
                String sessionId = (String) payload.get("sessionId");
                String content = (String) payload.get("content");
                Long agentId = agentSessions.get(session);
                if (sessionId != null && content != null
                        && isAssignedAgent(sessionId, agentId)) {
                    sendToUser(sessionId, Map.of("type", "agent_msg",
                            "content", content));
                    sendJson(session, Map.of("type", "echo", "content", content));
                    chatMessageService.saveAgent(sessionId, agentId, content);
                } else if (sessionId != null) {
                    sendJson(session, Map.of("type", "echo",
                            "content", content,
                            "rejected", true));
                }
                yield true;
            }
            case "transfer_ai" -> {
                String sid = (String) payload.get("sessionId");
                Long agentId = agentSessions.get(session);
                if (sid != null && isAssignedAgent(sid, agentId)) {
                    messageRouter.transferBackToAi(sid);
                    sendToUser(sid, Map.of("type", "back_to_ai", "content", "当前服务已结束"));
                    sendToUser(sid, Map.of("type", "satisfaction_required", "content", "请对本次服务进行评价"));
                    sendJson(session, Map.of("type", "service_ended", "sessionId", sid, "content", "当前服务已结束，已转回AI"));
                    chatMessageService.saveSystem(sid, "人工服务已结束，已切换回AI服务");
                }
                yield true;
            }
            case "transfer_to_agent" -> {
                String sid = (String) payload.get("sessionId");
                Long targetId = parseLong(payload.get("targetAgentId"));
                Long agentId = agentSessions.get(session);
                if (sid != null && targetId != null && isAssignedAgent(sid, agentId)) {
                    messageRouter.transferToAgent(sid, targetId);
                    sendToUser(sid, Map.of("type", "agent_transferred", "content", "已为您转接其他客服"));
                    sendJson(session, Map.of("type", "transferred", "sessionId", sid, "targetAgentId", targetId));
                }
                yield true;
            }
            case "request_satisfaction" -> {
                String sid = (String) payload.get("sessionId");
                Long agentId = agentSessions.get(session);
                if (sid != null && isAssignedAgent(sid, agentId)) {
                    sendToUser(sid, Map.of("type", "satisfaction_required", "content", "请对本次服务进行评价"));
                }
                yield true;
            }
            case "close" -> {
                String sid = (String) payload.get("sessionId");
                Long agentId = agentSessions.get(session);
                if (sid != null && isAssignedAgent(sid, agentId)) {
                    messageRouter.closeSession(sid);
                    sendToUser(sid, Map.of("type", "session_closed", "content", "当前服务已结束"));
                    sendToUser(sid, Map.of("type", "satisfaction_required", "content", "请对本次服务进行评价"));
                    sendJson(session, Map.of("type", "service_ended", "sessionId", sid, "content", "当前服务已结束，会话已关闭"));
                    chatMessageService.saveSystem(sid, "会话已关闭，服务已结束");
                }
                yield true;
            }
            case "heartbeat" -> {
                Long agentId = agentSessions.get(session);
                if (agentId != null) {
                    sessionStatePort.refreshAgentHeartbeat(agentId);
                    sendJson(session, Map.of("type", "heartbeat_ack",
                            "queueSize", messageRouter.getWaitingQueueSize()));
                }
                yield true;
            }
            default -> false;
        };
    }

    private boolean isAssignedAgent(String sessionId, Long agentId) {
        return agentId != null
                && messageRouter.isHumanSession(sessionId)
                && agentId.equals(messageRouter.getAssignedAgent(sessionId));
    }

    private Long authenticatedUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(WebSocketAuthenticationInterceptor.ATTR_USER_ID);
        return value instanceof Number number ? number.longValue() : null;
    }

    private String authenticatedChatSessionId(WebSocketSession session) {
        Object value = session.getAttributes().get(WebSocketAuthenticationInterceptor.ATTR_CHAT_SESSION_ID);
        return value instanceof String sessionId ? sessionId : null;
    }

    @SuppressWarnings("unchecked")
    private Set<String> authenticatedRoles(WebSocketSession session) {
        Object value = session.getAttributes().get(WebSocketAuthenticationInterceptor.ATTR_ROLES);
        return value instanceof Set<?> set ? (Set<String>) set : Set.of();
    }

    public boolean sendToUser(String sessionId, Object payload) {
        WebSocketSession userSession = userSessions.get(sessionId);
        if (userSession != null && userSession.isOpen()) {
            sendJson(userSession, payload);
            return true;
        }
        if (redisStreamAdapter != null) {
            redisStreamAdapter.publishToStreamAsync(
                    RedisStreamAdapter.USER_STREAM_PREFIX + sessionId, payload);
        }
        return false;
    }

    public void broadcastToAgents(Object payload) {
        agentSessions.forEach((session, agentId) -> {
            if (session.isOpen()) {
                sendJson(session, payload);
            }
        });
    }

    /**
     * 向指定客服发送消息。仅发给该客服的 WebSocket 连接，不发给其他客服。
     *
     * @param agentId 目标客服ID
     * @param payload 消息内容
     * @return true 表示至少发送给了一个在线连接
     */
    public boolean sendToAgent(Long agentId, Object payload) {
        boolean sent = false;
        for (var entry : agentSessions.entrySet()) {
            if (agentId.equals(entry.getValue()) && entry.getKey().isOpen()) {
                sendJson(entry.getKey(), payload);
                sent = true;
            }
        }
        return sent;
    }

    private void startQueueListener(WebSocketSession session) {
        if (redisStreamAdapter == null) return;
        String consumerId = "agent-q-" + hostname + "-" + session.getId().substring(0, 8);
        Runnable cancel = redisStreamAdapter.startGroupListener(
                RedisStreamAdapter.AGENT_QUEUE_STREAM,
                RedisStreamAdapter.AGENT_QUEUE_GROUP,
                consumerId, msg -> sendJson(session, msg));
        addAgentListener(session, cancel);
    }

    private void startPerAgentListener(WebSocketSession session, Long agentId) {
        if (redisStreamAdapter == null) return;
        String streamKey = RedisStreamAdapter.AGENT_STREAM_PREFIX + agentId;
        String consumerId = "agent-msg-" + agentId + "-" + session.getId();
        Runnable cancel = redisStreamAdapter.startListener(streamKey, consumerId, msg ->
                sendJson(session, msg));
        addAgentListener(session, cancel);
    }

    private void addAgentListener(WebSocketSession session, Runnable cancel) {
        agentListeners.computeIfAbsent(session, k -> new ArrayList<>()).add(cancel);
    }

    private void startUserListenerIfNeeded(String sessionId, WebSocketSession session) {
        wsIdToSessionId.put(session.getId(), sessionId);
        userSessions.put(sessionId, session);
        if (userListeners.containsKey(sessionId) || redisStreamAdapter == null) return;
        String streamKey = RedisStreamAdapter.USER_STREAM_PREFIX + sessionId;
        String consumerId = "user-" + sessionId + "-" + UUID.randomUUID().toString().substring(0, 8);
        Runnable cancel = redisStreamAdapter.startListener(streamKey, consumerId, msg ->
            sendJson(session, msg));
        userListeners.put(sessionId, cancel);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sendJson(session, Map.of("type", "connected", "content", "WebSocket connected"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sendQueues.remove(session);

        String sessionId = wsIdToSessionId.remove(session.getId());
        if (sessionId != null) {
            userSessions.remove(sessionId);
            Runnable cancel = userListeners.remove(sessionId);
            if (cancel != null) cancel.run();
        }

        Long agentId = agentSessions.remove(session);
        Consumer<Map<String, Object>> sub = agentSubscribers.remove(session);
        if (sub != null) agentBroadcaster.unsubscribe(sub);
        List<Runnable> listeners = agentListeners.remove(session);
        if (listeners != null) listeners.forEach(Runnable::run);

        if (agentId != null) {
            // Requeue sessions dispatched to this agent
            List<Map<String, Object>> waitingSessions = sessionStatePort.getAllWaitingSessionDetails();
            if (waitingSessions != null) {
                for (Map<String, Object> waiting : waitingSessions) {
                    String waitingSessionId = (String) waiting.get("sessionId");
                    if (waitingSessionId == null) continue;
                    Long dispatchedAgent = sessionStatePort.getSessionDispatched(waitingSessionId);
                    if (agentId.equals(dispatchedAgent)) {
                        sessionStatePort.clearSessionDispatched(waitingSessionId);
                        log.info("Agent {} offline, requeued session {}", agentId, waitingSessionId);
                    }
                }
            }
            sessionStatePort.markAgentOffline(agentId);
            log.info("Agent {} disconnected", agentId);
        }
        log.info("WS closed: {}, sessionId={}, agentId={}", session.getId(), sessionId, agentId);
    }

    private void sendConnected(WebSocketSession session) {
        sendJson(session, Map.of("type", "connected", "content", "WebSocket connected"));
    }

    private void sendError(WebSocketSession session, String error) {
        sendJson(session, Map.of("type", "error", "content", error));
    }

    private void sendJson(WebSocketSession session, Object obj) {
        try {
            String text = objectMapper.writeValueAsString(obj);
            Queue<String> q = sendQueues.computeIfAbsent(session,
                    k -> new ConcurrentLinkedQueue<>());
            q.add(text);
            drainQueue(session);
        } catch (Exception e) {
            log.error("Send failed: {}", e.getMessage());
        }
    }

    private void drainQueue(WebSocketSession session) {
        if (!drainingSessions.add(session)) return;
        try {
            Queue<String> q = sendQueues.get(session);
            if (q == null) return;
            while (true) {
                String text = q.poll();
                if (text == null) break;
                try {
                    session.sendMessage(new TextMessage(text));
                } catch (Exception e) {
                    q.add(text);
                    break;
                }
            }
        } finally {
            drainingSessions.remove(session);
            Queue<String> q2 = sendQueues.get(session);
            if (q2 != null && !q2.isEmpty()) {
                drainQueue(session);
            }
        }
    }

    private Long parseLong(Object value) {
        return ConvertUtils.parseLong(value);
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseRoleList(Object value) {
        if (value instanceof List) {
            return new HashSet<>((List<String>) value);
        }
        return Set.of();
    }
}
