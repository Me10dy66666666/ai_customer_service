package com.example.backend.application.service;

import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.infrastructure.messaging.AgentBroadcaster;
import com.example.backend.infrastructure.messaging.MessageRouter;
import com.example.backend.infrastructure.messaging.RedisStreamAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class AgentSessionService {

    @Service
    public static class ChatTransferBridge {
        private final MessageRouter messageRouter;
        private final SessionStatePort sessionStatePort;
        private final AgentBroadcaster agentBroadcaster;
        private final ChatSummaryService chatSummaryService;
        private final SessionDispatchService sessionDispatchService;
        private RedisStreamAdapter redisStreamAdapter;

        public ChatTransferBridge(MessageRouter messageRouter,
                                   SessionStatePort sessionStatePort,
                                   AgentBroadcaster agentBroadcaster,
                                   ChatSummaryService chatSummaryService,
                                   SessionDispatchService sessionDispatchService) {
            this.messageRouter = messageRouter;
            this.sessionStatePort = sessionStatePort;
            this.agentBroadcaster = agentBroadcaster;
            this.chatSummaryService = chatSummaryService;
            this.sessionDispatchService = sessionDispatchService;
        }

        @Autowired(required = false)
        public void setRedisStreamAdapter(RedisStreamAdapter redisStreamAdapter) {
            this.redisStreamAdapter = redisStreamAdapter;
        }

        /**
         * 用户发起转人工 — 先技能匹配派发，再加入等待队列并广播通知。
         * 派发结果包含在广播消息中，前端据此过滤非目标客服。
         */
        public void transferToHuman(String sessionId, Long userId, String intent) {
            if (sessionStatePort.isWaiting(sessionId)) {
                log.info("Session {} already in waiting queue, skip duplicate", sessionId);
                return;
            }

            // 先技能匹配派发，再入队广播，确保 dispathedAgentId 可包含在通知中
            Long dispatchedAgentId = sessionDispatchService.dispatch(sessionId, intent);

            messageRouter.enqueueWaiting(sessionId);
            sessionStatePort.setUserInfo(sessionId, userId, intent);

            long position = sessionStatePort.getWaitQueuePosition(sessionId);
            long estimatedWait = sessionStatePort.getEstimatedWaitTime(sessionId);

            Map<String, Object> notify = new LinkedHashMap<>();
            notify.put("type", "agent_queue_notify");
            notify.put("sessionId", sessionId);
            notify.put("userId", userId);
            notify.put("intent", intent);
            notify.put("position", position);
            notify.put("estimatedWait", estimatedWait);
            notify.put("dispatchedAgentId", dispatchedAgentId);

            agentBroadcaster.broadcast(notify);

            if (redisStreamAdapter != null) {
                redisStreamAdapter.publishToStream(
                        RedisStreamAdapter.AGENT_QUEUE_STREAM, notify);
            }

            log.info("Session {} → WAITING, userId={}, position={}, estimatedWait={}s, dispatchedAgentId={}",
                    sessionId, userId, position, estimatedWait, dispatchedAgentId);

            if (dispatchedAgentId != null) {
                Map<String, Object> dispatchMsg = new LinkedHashMap<>();
                dispatchMsg.put("type", "session_dispatched");
                dispatchMsg.put("sessionId", sessionId);
                dispatchMsg.put("agentId", dispatchedAgentId);
                agentBroadcaster.broadcast(dispatchMsg);
                log.info("Session {} dispatched notification sent to agent {}", sessionId, dispatchedAgentId);
            }

            chatSummaryService.summarizeTransfer(sessionId, userId, summaryResult -> {
                try {
                    Map<String, Object> summaryMsg = new LinkedHashMap<>();
                    summaryMsg.put("type", "summary_ready");
                    summaryMsg.put("sessionId", sessionId);
                    if (summaryResult.getPriority() != null) {
                        summaryMsg.put("priority", summaryResult.getPriority());
                    }
                    if (summaryResult.getSummary() != null) {
                        summaryMsg.put("content", summaryResult.getSummary());
                    }
                    if (summaryResult.getTags() != null) {
                        summaryMsg.put("tags", summaryResult.getTags());
                    }
                    agentBroadcaster.broadcast(summaryMsg);
                } catch (Exception e) {
                    log.warn("Failed to broadcast summary for session {}", sessionId, e);
                }
            });
        }
    }
}
