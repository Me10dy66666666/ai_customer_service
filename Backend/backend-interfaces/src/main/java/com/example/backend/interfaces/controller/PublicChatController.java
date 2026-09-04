package com.example.backend.interfaces.controller;

import com.example.backend.application.service.ChatApplicationService;
import com.example.backend.application.service.ChatMessageService;
import com.example.backend.common.Result;
import com.example.backend.common.exception.UnauthorizedException;
import com.example.backend.domain.chat.model.ChatMessage;
import com.example.backend.domain.chat.model.ConsultationLog;
import com.example.backend.domain.chat.model.SessionState;
import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.infrastructure.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/public/chat")
@RequiredArgsConstructor
public class PublicChatController {

    private final ChatApplicationService chatApplicationService;
    private final ChatMessageService chatMessageService;
    private final SessionStatePort sessionStatePort;
    private final JwtUtils jwtUtils;

    private static final String KEY_SENDER_TYPE = "senderType";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_SOURCE = "source";

    @PostMapping("/session")
    public Result<Map<String, String>> createSession() {
        String sessionId = "guest-" + UUID.randomUUID();
        return Result.success(Map.of(
                "sessionId", sessionId,
                "sessionToken", jwtUtils.generateChatSessionToken(sessionId)));
    }

    @GetMapping("/history")
    public Result<List<ConsultationLog>> getHistory(
            @RequestParam String sessionId,
            @RequestHeader("X-Chat-Session-Token") String sessionToken) {
        requireSessionAccess(sessionId, sessionToken);
        return Result.success(chatApplicationService.getHistory(sessionId));
    }

    @GetMapping("/session/{sessionId}/status")
    public Result<Map<String, Object>> getSessionStatus(
            @PathVariable String sessionId,
            @RequestHeader("X-Chat-Session-Token") String sessionToken) {
        requireSessionAccess(sessionId, sessionToken);
        SessionState state = sessionStatePort.getState(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", state.name());
        result.put("humanSessionActive", state == SessionState.HUMAN);
        result.put("isWaiting", state == SessionState.WAITING);

        if (state == SessionState.WAITING) {
            result.put("waitPosition", sessionStatePort.getWaitQueuePosition(sessionId));
            result.put("estimatedWait", sessionStatePort.getEstimatedWaitTime(sessionId));
        }
        return Result.success(result);
    }

    @GetMapping("/session/{sessionId}/full-history")
    public Result<List<Map<String, Object>>> getFullHistory(@PathVariable String sessionId,
                                                             @RequestParam(required = false) String since,
                                                             @RequestHeader("X-Chat-Session-Token") String sessionToken) {
        requireSessionAccess(sessionId, sessionToken);
        LocalDateTime sinceTime = parseSinceTime(since);
        List<Map<String, Object>> merged = new ArrayList<>();

        List<ConsultationLog> aiLogs = chatApplicationService.getHistory(sessionId);
        List<ChatMessage> humanMessages = chatMessageService.getHistory(sessionId);

        collectAiMessages(aiLogs, sinceTime, merged);
        collectHumanMessages(humanMessages, sinceTime, merged);

        sortByTime(merged);
        return Result.success(merged);
    }

    private LocalDateTime parseSinceTime(String since) {
        if (since == null || since.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(since);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private void collectAiMessages(List<ConsultationLog> aiLogs, LocalDateTime sinceTime,
                                    List<Map<String, Object>> merged) {
        if (aiLogs == null) return;
        for (ConsultationLog log : aiLogs) {
            if (isBeforeSince(log.getCreateTime(), sinceTime)) continue;
            if (log.getUserInput() != null && !log.getUserInput().isEmpty()) {
                merged.add(buildMessageItem("user", "USER", log.getUserInput(),
                        log.getCreateTime(), "ai"));
            }
            if (log.getAiResponse() != null && !log.getAiResponse().isEmpty()) {
                merged.add(buildMessageItem("ai", "AI", log.getAiResponse(),
                        log.getCreateTime(), "ai"));
            }
        }
    }

    private void collectHumanMessages(List<ChatMessage> humanMessages, LocalDateTime sinceTime,
                                       List<Map<String, Object>> merged) {
        if (humanMessages == null) return;
        for (ChatMessage msg : humanMessages) {
            if (ChatMessage.TYPE_SYSTEM.equals(msg.getSenderType())) continue;
            if (isBeforeSince(msg.getCreateTime(), sinceTime)) continue;
            String role = ChatMessage.TYPE_AGENT.equals(msg.getSenderType()) ? "agent" : "user";
            Map<String, Object> item = buildMessageItem(role, msg.getSenderType(), msg.getContent(),
                    msg.getCreateTime(), "human");
            merged.add(item);
        }
    }

    private Map<String, Object> buildMessageItem(String role, String senderType, String content,
                                                  LocalDateTime time, String source) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put(KEY_SENDER_TYPE, senderType);
        item.put(KEY_CONTENT, content);
        item.put("time", time != null ? time.toString() : "");
        item.put(KEY_SOURCE, source);
        return item;
    }

    private boolean isBeforeSince(LocalDateTime time, LocalDateTime sinceTime) {
        return sinceTime != null && time != null && time.isBefore(sinceTime);
    }

    private void sortByTime(List<Map<String, Object>> merged) {
        merged.sort((a, b) -> {
            String ta = (String) a.getOrDefault("time", "");
            String tb = (String) b.getOrDefault("time", "");
            return ta.compareTo(tb);
        });
    }

    private void requireSessionAccess(String sessionId, String sessionToken) {
        if (!jwtUtils.validateChatSessionToken(sessionToken, sessionId)) {
            throw new UnauthorizedException("Invalid chat session token");
        }
    }
}
