package com.example.backend.infrastructure.messaging;

import com.example.backend.domain.chat.model.SessionState;
import com.example.backend.domain.chat.service.SessionStatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageRouter {

    private final SessionStatePort sessionStatePort;

    public static final String AGENT_QUEUE_STREAM = "ai_cs:stream:agent_queue";
    public static final String AGENT_QUEUE_GROUP = "agent-group";

    public MessageRouter(SessionStatePort sessionStatePort) {
        this.sessionStatePort = sessionStatePort;
    }

    public boolean shouldRouteToAi(String sessionId) {
        return sessionStatePort.getState(sessionId) == SessionState.AI;
    }

    public boolean isHumanSession(String sessionId) {
        return sessionStatePort.getState(sessionId) == SessionState.HUMAN;
    }

    /** 判断会话是否在排队等待中 */
    public boolean isWaitingSession(String sessionId) {
        return sessionStatePort.getState(sessionId) == SessionState.WAITING;
    }

    public void enqueueWaiting(String sessionId) {
        sessionStatePort.setState(sessionId, SessionState.WAITING, null);
        sessionStatePort.addToWaitQueue(sessionId);
    }

    /**
     * 客服认领会话（含分布式锁保护 + 状态前置校验）。
     * 仅 WAITING 状态的会话可被认领，防止劫持已由其他客服接管的会话。
     *
     * @return true=认领成功, false=已被其他客服认领或锁竞争失败
     */
    public boolean claimSession(String sessionId, Long agentId) {
        // 前置校验：仅 WAITING 状态的会话允许认领
        if (sessionStatePort.getState(sessionId) != SessionState.WAITING) {
            log.warn("Session {} claim rejected: current state is not WAITING for agent {}", sessionId, agentId);
            return false;
        }

        if (!sessionStatePort.acquireClaimLock(sessionId, agentId)) {
            log.warn("Session {} claim lock failed for agent {}", sessionId, agentId);
            return false;
        }
        try {
            // Double Check：获取锁后再次确认状态未变
            if (sessionStatePort.getState(sessionId) != SessionState.WAITING) {
                log.warn("Session {} state changed during lock acquisition, claim rejected for agent {}",
                        sessionId, agentId);
                return false;
            }
            sessionStatePort.setState(sessionId, SessionState.HUMAN, agentId);
            sessionStatePort.removeFromWaitQueue(sessionId);
            sessionStatePort.addAgentActiveSession(agentId, sessionId);
            sessionStatePort.setAiBlocked(sessionId, true);
            return true;
        } finally {
            sessionStatePort.releaseClaimLock(sessionId, agentId);
        }
    }

    public Long getAssignedAgent(String sessionId) {
        return sessionStatePort.getAgentId(sessionId);
    }

    public void transferBackToAi(String sessionId) {
        Long agentId = sessionStatePort.getAgentId(sessionId);
        if (agentId != null) {
            sessionStatePort.removeAgentActiveSession(agentId, sessionId);
        }
        sessionStatePort.setState(sessionId, SessionState.AI, null);
        sessionStatePort.setAiBlocked(sessionId, false);
        sessionStatePort.removeFromWaitQueue(sessionId);
    }

    /**
     * 将指定会话从旧客服转移到新客服。
     */
    public void transferToAgent(String sessionId, Long oldAgentId, Long newAgentId) {
        if (oldAgentId != null) {
            sessionStatePort.removeAgentActiveSession(oldAgentId, sessionId);
        }
        sessionStatePort.setState(sessionId, SessionState.HUMAN, newAgentId);
        sessionStatePort.addAgentActiveSession(newAgentId, sessionId);
    }

    /** 客服间转接（兼容旧接口） */
    public void transferToAgent(String sessionId, Long newAgentId) {
        Long oldAgentId = sessionStatePort.getAgentId(sessionId);
        transferToAgent(sessionId, oldAgentId, newAgentId);
    }

    public void cancelWaiting(String sessionId) {
        sessionStatePort.setState(sessionId, SessionState.AI, null);
        sessionStatePort.removeFromWaitQueue(sessionId);
        sessionStatePort.setAiBlocked(sessionId, false);
    }

    public void closeSession(String sessionId) {
        Long agentId = sessionStatePort.getAgentId(sessionId);
        if (agentId != null) {
            sessionStatePort.removeAgentActiveSession(agentId, sessionId);
        }
        sessionStatePort.setState(sessionId, SessionState.CLOSED, null);
        sessionStatePort.setAiBlocked(sessionId, false);
        sessionStatePort.removeFromWaitQueue(sessionId);
    }

    /** 获取等待队列长度 */
    public long getWaitingQueueSize() {
        return sessionStatePort.getWaitQueueSize();
    }

    /** 获取等待队列队首会话 */
    public String peekOldestWaiting() {
        return sessionStatePort.peekOldestWaiting();
    }
}
