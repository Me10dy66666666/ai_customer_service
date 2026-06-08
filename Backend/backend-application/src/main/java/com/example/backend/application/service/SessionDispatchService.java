package com.example.backend.application.service;

import com.example.backend.infrastructure.persistence.mapper.AgentSkillMapper;
import com.example.backend.infrastructure.persistence.mapper.ChatMessageMapper;
import com.example.backend.domain.chat.service.SessionStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionDispatchService {

    private final SessionStatePort sessionStatePort;
    private final AgentSkillMapper agentSkillMapper;
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 为 WAITING 会话匹配并派发给最合适的在线客服。
     * 派发逻辑：biz_tag → 匹配技能 → 在线过滤 → 负载均衡（最少活跃会话）
     *
     * @param sessionId 会话ID
     * @param bizTag    业务标签（"pre_sales"/"after_sales"/null）
     * @return 派发到的客服ID，null 表示无可用客服
     */
    public Long dispatch(String sessionId, String bizTag) {
        // 1. 获取在线客服列表
        Set<Long> onlineAgents = sessionStatePort.getOnlineAgents();
        if (onlineAgents == null || onlineAgents.isEmpty()) {
            log.warn("Session {} dispatch failed: no online agents", sessionId);
            return null;
        }

        // 2. 映射 bizTag 到技能名
        String targetSkill = mapBizTagToSkill(bizTag);

        // 3. 查询拥有该技能的客服
        List<Long> skilledAgentIds = agentSkillMapper.findAgentIdsBySkill(targetSkill);
        if (skilledAgentIds == null || skilledAgentIds.isEmpty()) {
            // 4. 无精确匹配 → 兜底使用通用技能
            log.info("Session {}: no agent with skill '{}', falling back to '通用'", sessionId, targetSkill);
            skilledAgentIds = agentSkillMapper.findAgentIdsBySkill("通用");
        }
        if (skilledAgentIds == null || skilledAgentIds.isEmpty()) {
            log.warn("Session {} dispatch failed: no agent with skill '{}' or '通用'", sessionId, targetSkill);
            return null;
        }

        // 5. 过滤：只保留在线客服
        List<Long> candidates = skilledAgentIds.stream()
                .filter(onlineAgents::contains)
                .toList();

        if (candidates.isEmpty()) {
            log.warn("Session {} dispatch failed: all skilled agents offline", sessionId);
            return null;
        }

        // 6. 负载均衡：选择当前活跃会话数最少的客服
        Long selectedAgent = null;
        int minActive = Integer.MAX_VALUE;
        for (Long agentId : candidates) {
            int activeCount = sessionStatePort.getAgentActiveSessions(agentId).size();
            if (activeCount < minActive) {
                minActive = activeCount;
                selectedAgent = agentId;
            }
        }

        // 7. 分布式锁绑定派发
        if (selectedAgent != null) {
            boolean locked = sessionStatePort.acquireClaimLock(sessionId, selectedAgent);
            if (locked) {
                try {
                    // 将会话标记为已派发给该客服
                    sessionStatePort.setSessionDispatched(sessionId, selectedAgent);
                    log.info("Session {} dispatched to agent {}", sessionId, selectedAgent);
                } finally {
                    sessionStatePort.releaseClaimLock(sessionId, selectedAgent);
                }
            } else {
                log.info("Session {} dispatch lock failed, already claimed", sessionId);
                return null;
            }
        }

        return selectedAgent;
    }

    /**
     * 获取会话的派发目标客服ID。
     *
     * @param sessionId 会话ID
     * @return 派发目标客服ID，null 表示未派发
     */
    public Long getDispatchedAgent(String sessionId) {
        return sessionStatePort.getSessionDispatched(sessionId);
    }

    /**
     * 清除会话的派发绑定。
     *
     * @param sessionId 会话ID
     */
    public void clearDispatch(String sessionId) {
        sessionStatePort.clearSessionDispatched(sessionId);
    }

    private String mapBizTagToSkill(String bizTag) {
        if (bizTag == null) return "通用";
        return switch (bizTag.toLowerCase()) {
            case "pre_sales", "presale", "售前", "售前咨询" -> "售前";
            case "after_sales", "aftersale", "售后", "售后服务" -> "售后";
            default -> "通用";
        };
    }

}
