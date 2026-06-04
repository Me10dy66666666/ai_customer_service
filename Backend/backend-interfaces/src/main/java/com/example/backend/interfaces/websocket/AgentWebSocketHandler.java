package com.example.backend.interfaces.websocket;

import com.example.backend.application.service.ChatMessageService;
import com.example.backend.common.util.ConvertUtils;
import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.infrastructure.messaging.MessageRouter;
import com.example.backend.infrastructure.messaging.AgentBroadcaster;
import com.example.backend.infrastructure.messaging.RedisStreamAdapter;
import com.example.backend.domain.shared.messaging.MessageBusPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

import java.util.function.Consumer;

@Slf4j
@Component
@SuppressWarnings("java:S1192")
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final MessageRouter messageRouter;
    private final AgentBroadcaster agentBroadcaster;
    private final SessionStatePort sessionStatePort;
    private final ChatMessageService chatMessageService;
    private RedisStreamAdapter redisStreamAdapter;
    private MessageBusPort messageBusPort;

    private final Map<WebSocketSession, Long> agentSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Consumer<Map<String, Object>>> sessionSubscribers = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, List<Runnable>> agentListeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> sendLocks = new ConcurrentHashMap<>();

    public AgentWebSocketHandler(MessageRouter messageRouter,
                                  AgentBroadcaster agentBroadcaster,
                                  SessionStatePort sessionStatePort,
                                  ChatMessageService chatMessageService,
                                  ObjectMapper objectMapper) {
        this.messageRouter = messageRouter;
        this.agentBroadcaster = agentBroadcaster;
        this.sessionStatePort = sessionStatePort;
        this.chatMessageService = chatMessageService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setRedisStreamAdapter(RedisStreamAdapter redisStreamAdapter) {
        this.redisStreamAdapter = redisStreamAdapter;
    }

    @Autowired(required = false)
    public void setMessageBusPort(MessageBusPort messageBusPort) {
        this.messageBusPort = messageBusPort;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Agent WS connected: {}", session.getId());
        sendJson(session, Map.of("type", "connected", "content", "Agent WebSocket connected"));
        startQueueListener(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long agentId = agentSessions.remove(session);
        Consumer<Map<String, Object>> sub = sessionSubscribers.remove(session);
        if (sub != null) agentBroadcaster.unsubscribe(sub);
        List<Runnable> listeners = agentListeners.remove(session);
        if (listeners != null) listeners.forEach(Runnable::run);

        if (agentId != null) {
            sessionStatePort.markAgentOffline(agentId);
            transferOfflineSessions(agentId);
        }
        log.info("Agent WS closed: {}, agentId={}", session.getId(), agentId);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String action = (String) payload.getOrDefault("action", "");

            switch (action) {
                case "register"              -> handleRegister(session, payload);
                case "claim"                 -> handleClaim(session, payload);
                case "agent_message"         -> handleAgentMsg(session, payload);
                case "transfer_ai"           -> handleTransferAi(session, payload);
                case "transfer_to_agent"     -> handleTransferToAgent(session, payload);
                case "request_satisfaction"  -> handleRequestSatisfaction(session, payload);
                case "close"                 -> handleClose(session, payload);
                case "heartbeat"             -> handleHeartbeat(session, payload);
                default                      -> sendJson(session, Map.of("type", "unknown_action", "action", action));
            }
        } catch (Exception e) {
            log.error("Agent WS error: {}", e.getMessage(), e);
            sendJson(session, Map.of("type", "error", "content", "系统繁忙，请稍后重试"));
        }
    }

    private void handleRegister(WebSocketSession session, Map<String, Object> payload) {
        Long agentId = parseLong(payload.get("agentId"));
        if (agentId != null) {
            agentSessions.put(session, agentId);
            sessionStatePort.markAgentOnline(agentId);
            Consumer<Map<String, Object>> sub = data -> sendJson(session, data);
            sessionSubscribers.put(session, sub);
            agentBroadcaster.subscribe(sub);
            startPerAgentListener(session, agentId);
            sendJson(session, Map.of("type", "registered",
                    "agentId", agentId,
                    "queueSize", messageRouter.getWaitingQueueSize()));
        } else {
            log.warn("Agent register rejected: invalid agentId from session {}", session.getId());
            sendJson(session, Map.of("type", "register_failed",
                    "content", "无效的客服ID，请重新登录"));
        }
    }

    private void handleClaim(WebSocketSession session, Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        Long agentId = agentSessions.get(session);
        if (sessionId == null || agentId == null) return;
        boolean success = messageRouter.claimSession(sessionId, agentId);
        if (success) {
            sendJson(session, Map.of("type", "claimed", "sessionId", sessionId));
            sendToUser(sessionId, Map.of("type", "agent_joined",
                    "content", "客服已接入，为您服务"));
            chatMessageService.saveSystem(sessionId, "客服已接入，为您服务");
        } else {
            sendJson(session, Map.of("type", "claim_failed",
                    "sessionId", sessionId,
                    "content", "该会话已被其他客服认领"));
        }
    }

    private void handleAgentMsg(WebSocketSession session, Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        String content = (String) payload.get("content");
        Long agentId = agentSessions.get(session);
        if (sessionId == null || content == null) return;
        sendToUser(sessionId, Map.of("type", "agent_msg", "content", content));
        sendJson(session, Map.of("type", "echo", "content", content));
        chatMessageService.saveAgent(sessionId, agentId, content);
    }

    private void handleTransferAi(WebSocketSession session, Map<String, Object> payload) {
        String sid = (String) payload.get("sessionId");
        if (sid == null) return;
        messageRouter.transferBackToAi(sid);
        sendToUser(sid, Map.of("type", "back_to_ai", "content", "当前服务已结束"));
        sendToUser(sid, Map.of("type", "satisfaction_required", "content", "请对本次服务进行评价"));
        sendJson(session, Map.of("type", "service_ended", "content", "当前服务已结束，已转回AI"));
        chatMessageService.saveSystem(sid, "人工服务已结束，已切换回AI服务");
    }

    private void handleTransferToAgent(WebSocketSession session, Map<String, Object> payload) {
        String sid = (String) payload.get("sessionId");
        Long targetId = parseLong(payload.get("targetAgentId"));
        if (sid == null || targetId == null) return;
        messageRouter.transferToAgent(sid, targetId);
        sendToUser(sid, Map.of("type", "agent_transferred", "content", "已为您转接其他客服"));
        sendJson(session, Map.of("type", "transferred", "sessionId", sid, "targetAgentId", targetId));
    }

    private void handleRequestSatisfaction(WebSocketSession session, Map<String, Object> payload) {
        String sid = (String) payload.get("sessionId");
        if (sid != null) {
            sendToUser(sid, Map.of("type", "satisfaction_required",
                    "content", "请对本次服务进行评价"));
        }
    }

    private void handleClose(WebSocketSession session, Map<String, Object> payload) {
        String sid = (String) payload.get("sessionId");
        if (sid == null) return;
        messageRouter.closeSession(sid);
        archiveSession(sid);
        sendToUser(sid, Map.of("type", "session_closed", "content", "当前服务已结束"));
        sendToUser(sid, Map.of("type", "satisfaction_required", "content", "请对本次服务进行评价"));
        sendJson(session, Map.of("type", "service_ended", "content", "当前服务已结束，会话已关闭"));
        chatMessageService.saveSystem(sid, "会话已关闭，服务已结束");
    }

    private void handleHeartbeat(WebSocketSession session, Map<String, Object> payload) {
        Long agentId = agentSessions.get(session);
        if (agentId != null) {
            sessionStatePort.refreshAgentHeartbeat(agentId);
            sendJson(session, Map.of("type", "heartbeat_ack",
                    "queueSize", messageRouter.getWaitingQueueSize()));
        }
    }

    private void sendToUser(String sessionId, Object payload) {
        if (redisStreamAdapter == null) return;
        redisStreamAdapter.publishToStream(
                RedisStreamAdapter.USER_STREAM_PREFIX + sessionId, payload);
    }

    private void startQueueListener(WebSocketSession session) {
        if (redisStreamAdapter == null) return;
        String consumerId = "agent-q-" + session.getId();
        Runnable cancel = redisStreamAdapter.startListener(
                RedisStreamAdapter.AGENT_QUEUE_STREAM, consumerId, msg ->
                        sendJson(session, msg));
        addListener(session, cancel);
    }

    private void startPerAgentListener(WebSocketSession session, Long agentId) {
        if (redisStreamAdapter == null) return;
        String streamKey = RedisStreamAdapter.AGENT_STREAM_PREFIX + agentId;
        String consumerId = "agent-msg-" + agentId + "-" + session.getId();
        Runnable cancel = redisStreamAdapter.startListener(streamKey, consumerId, msg ->
                sendJson(session, msg));
        addListener(session, cancel);
    }

    private void addListener(WebSocketSession session, Runnable cancel) {
        agentListeners.computeIfAbsent(session, k -> new ArrayList<>()).add(cancel);
    }

    private void archiveSession(String sessionId) {
        if (messageBusPort != null) {
            try {
                Map<String, Object> archive = new LinkedHashMap<>();
                archive.put("sessionId", sessionId);
                archive.put("action", "archive");
                messageBusPort.send("session.closed", archive);
            } catch (Exception e) {
                log.warn("MQ archive failed for session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    private void transferOfflineSessions(Long offlineAgentId) {
        List<String> sessions = sessionStatePort.getAgentActiveSessions(offlineAgentId);
        if (sessions.isEmpty()) return;

        Set<Long> onlineAgents = sessionStatePort.getOnlineAgents();
        if (onlineAgents.isEmpty()) {
            log.warn("No online agents available for transferring {} sessions from agent {}",
                    sessions.size(), offlineAgentId);
            for (String sid : sessions) {
                messageRouter.transferBackToAi(sid);
                sendToUser(sid, Map.of("type", "agent_offline",
                        "content", "当前客服已离线，已为您切换回AI服务"));
            }
            return;
        }

        Long targetAgent = onlineAgents.iterator().next();
        log.info("Transferring {} sessions from agent {} to agent {}",
                sessions.size(), offlineAgentId, targetAgent);

        for (String sid : sessions) {
            messageRouter.transferToAgent(sid, offlineAgentId, targetAgent);
            sendToUser(sid, Map.of("type", "agent_transferred",
                    "content", "当前客服已离线，已为您转接其他客服"));
        }

        for (WebSocketSession ws : agentSessions.entrySet().stream()
                .filter(e -> targetAgent.equals(e.getValue()))
                .map(Map.Entry::getKey).toList()) {
            sendJson(ws, Map.of("type", "sessions_transferred_in",
                    "count", sessions.size(),
                    "fromAgentId", offlineAgentId));
        }
    }

    public void scanStaleHeartbeats() {
        Set<Long> expired = sessionStatePort.expireStaleHeartbeats(HEARTBEAT_TIMEOUT_SECONDS);
        for (Long agentId : expired) {
            log.warn("Heartbeat scan: agent {} expired", agentId);
            transferOfflineSessions(agentId);
        }
    }

    private void sendJson(WebSocketSession session, Object payload) {
        try {
            Object lock = sendLocks.computeIfAbsent(session.getId(), k -> new Object());
            synchronized (lock) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.error("Send failed: {}", e.getMessage());
        }
    }

    private Long parseLong(Object v) {
        return ConvertUtils.parseLong(v);
    }
}
