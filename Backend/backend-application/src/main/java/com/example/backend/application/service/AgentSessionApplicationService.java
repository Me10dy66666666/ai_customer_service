package com.example.backend.application.service;

import com.example.backend.application.dto.AgentSessionContext;
import com.example.backend.domain.chat.model.ChatMessage;
import com.example.backend.domain.chat.model.ConsultationLog;
import com.example.backend.domain.chat.repository.ConsultationLogRepository;
import com.example.backend.domain.chat.service.SessionStatePort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionApplicationService {

    private final SessionStatePort sessionStatePort;
    private final ChatMessageService chatMessageService;
    private final ConsultationLogRepository consultationLogRepository;
    private final ObjectMapper objectMapper;

    public List<AgentSessionContext> getActiveSessions(Long agentId) {
        Set<String> allSessionIds = new LinkedHashSet<>();

        List<Map<String, Object>> waitingList = sessionStatePort.getAllWaitingSessionDetails();
        for (Map<String, Object> waiting : waitingList) {
            String sessionId = (String) waiting.get("sessionId");
            if (sessionId != null) {
                allSessionIds.add(sessionId);
            }
        }

        if (agentId != null) {
            List<String> claimedSessions = sessionStatePort.getAgentActiveSessions(agentId);
            if (claimedSessions != null) {
                allSessionIds.addAll(claimedSessions);
            }
        }

        if (allSessionIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Map<String, Object>> waitingMap = new LinkedHashMap<>();
        for (Map<String, Object> w : waitingList) {
            String sid = (String) w.get("sessionId");
            if (sid != null) {
                waitingMap.put(sid, w);
            }
        }

        Set<String> claimedSet = new HashSet<>();
        if (agentId != null) {
            List<String> claimedSessions = sessionStatePort.getAgentActiveSessions(agentId);
            if (claimedSessions != null) {
                claimedSet.addAll(claimedSessions);
            }
        }

        List<AgentSessionContext> result = new ArrayList<>();
        for (String sessionId : allSessionIds) {
            AgentSessionContext ctx = buildContext(sessionId, waitingMap.get(sessionId), claimedSet.contains(sessionId));
            if (ctx != null) {
                result.add(ctx);
            }
        }
        return result;
    }

    private AgentSessionContext buildContext(String sessionId, Map<String, Object> waitingInfo, boolean isClaimed) {
        SessionStatePort.SessionInfo sessionInfo = sessionStatePort.getSessionInfo(sessionId);
        if (sessionInfo == null) {
            return null;
        }

        AgentSessionContext ctx = new AgentSessionContext();
        ctx.setSessionId(sessionId);
        ctx.setUserId(sessionInfo.userId());
        ctx.setIntent(sessionInfo.intent() != null ? sessionInfo.intent() : "");
        ctx.setStatus(sessionInfo.status() != null ? sessionInfo.status().name() : "AI");

        if (waitingInfo != null) {
            ctx.setPosition(toLong(waitingInfo.get("position")));
            ctx.setEstimatedWait(toLong(waitingInfo.get("estimatedWait")));
        }

        extractSummaryFromMessages(sessionId, ctx);

        List<Map<String, String>> aiMessages = buildAiConversation(sessionId);
        ctx.setAiMessages(aiMessages);

        return ctx;
    }

    private void extractSummaryFromMessages(String sessionId, AgentSessionContext ctx) {
        try {
            List<ChatMessage> messages = chatMessageService.getHistory(sessionId);
            if (messages == null) return;
            for (ChatMessage msg : messages) {
                if (!ChatMessage.TYPE_SYSTEM.equals(msg.getSenderType()) || msg.getContent() == null) {
                    continue;
                }
                Map<String, String> parsed = tryParseSummary(msg.getContent());
                if (parsed != null) {
                    if (parsed.get("priority") != null) ctx.setPriority(parsed.get("priority"));
                    if (parsed.get("summary") != null) ctx.setSummary(parsed.get("summary"));
                    if (parsed.get("tags") != null) ctx.setTags(parsed.get("tags"));
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract summary for session {}: {}", sessionId, e.getMessage());
        }
    }

    private Map<String, String> tryParseSummary(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, String>>() {});
        } catch (Exception ignore) {
            return null;
        }
    }

    private List<Map<String, String>> buildAiConversation(String sessionId) {
        try {
            List<ConsultationLog> logs = consultationLogRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
            if (logs == null || logs.isEmpty()) return Collections.emptyList();

            LocalDate today = LocalDate.now();
            List<Map<String, String>> messages = new ArrayList<>();
            for (ConsultationLog log : logs) {
                if (log.getCreateTime() != null && log.getCreateTime().toLocalDate().isBefore(today)) {
                    continue;
                }
                if (log.getUserInput() != null && !log.getUserInput().isEmpty()) {
                    Map<String, String> userMsg = new LinkedHashMap<>();
                    userMsg.put("role", "user");
                    userMsg.put("content", log.getUserInput());
                    userMsg.put("time", log.getCreateTime() != null ? log.getCreateTime().toString() : "");
                    messages.add(userMsg);
                }
                if (log.getAiResponse() != null && !log.getAiResponse().isEmpty()) {
                    Map<String, String> aiMsg = new LinkedHashMap<>();
                    aiMsg.put("role", "ai");
                    aiMsg.put("content", log.getAiResponse());
                    aiMsg.put("time", log.getCreateTime() != null ? log.getCreateTime().toString() : "");
                    messages.add(aiMsg);
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("Failed to build AI conversation for session {}: {}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
