package com.example.backend.application.service;

import com.example.backend.domain.chat.model.ConsultationLog;
import com.example.backend.domain.chat.model.ChatMessage;
import com.example.backend.domain.chat.repository.ConsultationLogRepository;
import com.example.backend.infrastructure.dify.SummaryClient;
import com.example.backend.infrastructure.dify.WorkOrderAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSummaryService {

    private final ConsultationLogRepository consultationLogRepository;
    private final ChatMessageService chatMessageService;
    private final SummaryClient summaryClient;
    private final ObjectMapper objectMapper;

    @Value("${dify.intervention.workflow}")
    private String transferWorkflow;

    @Value("${dify.workorder.workflow}")
    private String workorderWorkflow;

    @Value("${dify.workorder.api-key}")
    private String workorderApiKey;

    public void summarizeTransfer(String sessionId, Long userId,
                                   java.util.function.Consumer<SummaryClient.SummaryResult> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> chatHistory = buildDailyChatHistory(sessionId);
                if (chatHistory == null) return;
                SummaryClient.SummaryResult result = summaryClient.callTransferWorkflow(chatHistory, transferWorkflow);
                if (result == null) return;
                String priority = result.getPriority() != null ? result.getPriority() : "Low";
                String summaryContent = result.getSummary() != null ? result.getSummary() : "";
                saveSummaryToMessages(sessionId, priority, summaryContent);
                onDone.accept(result);
            } catch (Exception e) {
                log.warn("Transfer summary failed for session {}: {}", sessionId, e.getMessage());
            }
        }).orTimeout(45, TimeUnit.SECONDS)
          .exceptionally(ex -> {
              log.warn("Transfer summary timed out for session {}", sessionId);
              return null;
          });
    }

    public void summarizeWorkorder(String sessionId, Long userId,
                                    String title, String type, String description,
                                    LocalDateTime workOrderTime,
                                    java.util.function.Consumer<WorkOrderAnalysisResult> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> inputs = buildDailyChatHistory(sessionId, workOrderTime);
                if (inputs == null) {
                    inputs = new LinkedHashMap<>();
                    inputs.put("sessionID", sessionId);
                    inputs.put("messages", "[]");
                }
                inputs.put("title", title != null ? title : "");
                inputs.put("type", type != null ? type : "");
                inputs.put("description", description != null ? description : "");
                WorkOrderAnalysisResult result = summaryClient.callWorkorderWorkflow(inputs, workorderWorkflow, workorderApiKey);
                if (result == null) return;
                onDone.accept(result);
            } catch (Exception e) {
                log.warn("Workorder summary failed for session {}: {}", sessionId, e.getMessage());
            }
        }).orTimeout(45, TimeUnit.SECONDS)
          .exceptionally(ex -> {
              log.warn("Workorder summary timed out for session {}", sessionId);
              return null;
          });
    }

    private Map<String, Object> buildDailyChatHistory(String sessionId) {
        return buildDailyChatHistory(sessionId, LocalDate.now().atStartOfDay());
    }

    private Map<String, Object> buildDailyChatHistory(String sessionId, LocalDateTime since) {
        List<ConsultationLog> logs = consultationLogRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);

        List<Map<String, String>> messages = new ArrayList<>();
        if (logs != null) {
            for (ConsultationLog l : logs) {
                if (l.getCreateTime() != null && l.getCreateTime().isBefore(since)) continue;
                if (l.getUserInput() != null && !l.getUserInput().isEmpty()) {
                    Map<String, String> userMsg = new LinkedHashMap<>();
                    userMsg.put("role", "user");
                    userMsg.put("content", l.getUserInput());
                    userMsg.put("time", l.getCreateTime() != null ? l.getCreateTime().toString() : "");
                    messages.add(userMsg);
                }
                if (l.getAiResponse() != null && !l.getAiResponse().isEmpty()) {
                    Map<String, String> aiMsg = new LinkedHashMap<>();
                    aiMsg.put("role", "ai");
                    aiMsg.put("content", l.getAiResponse());
                    aiMsg.put("time", l.getCreateTime() != null ? l.getCreateTime().toString() : "");
                    messages.add(aiMsg);
                }
            }
        }
        if (messages.isEmpty()) return null;

        List<ChatMessage> humanMessages = chatMessageService.getHistory(sessionId);
        if (humanMessages != null) {
            for (ChatMessage cm : humanMessages) {
                if (cm.getCreateTime() != null && cm.getCreateTime().isBefore(since)) continue;
                Map<String, String> msg = new LinkedHashMap<>();
                msg.put("role", "AGENT".equals(cm.getSenderType()) ? "agent" : "user");
                msg.put("content", cm.getContent());
                msg.put("time", cm.getCreateTime() != null ? cm.getCreateTime().toString() : "");
                messages.add(msg);
            }
            messages.sort((a, b) -> {
                String ta = a.get("time");
                String tb = b.get("time");
                if (ta == null) return 1;
                if (tb == null) return -1;
                return ta.compareTo(tb);
            });
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionID", sessionId);
        try {
            input.put("messages", objectMapper.writeValueAsString(messages));
        } catch (Exception e) {
            log.warn("Failed to serialize messages for session {}: {}", sessionId, e.getMessage());
            return null;
        }
        return input;
    }

    private void saveSummaryToMessages(String sessionId, String priority, String summaryContent) {
        String content = "{\"priority\":\"" + priority + "\",\"summary\":\"" + escapeJson(summaryContent) + "\"}";
        chatMessageService.saveSystem(sessionId, content);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
