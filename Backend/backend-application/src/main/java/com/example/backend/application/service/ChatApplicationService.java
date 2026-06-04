package com.example.backend.application.service;

import com.example.backend.common.exception.ExternalServiceException;
import com.example.backend.domain.chat.event.ConversationCompletedEvent;
import com.example.backend.domain.chat.event.SatisfactionRatedEvent;
import com.example.backend.domain.chat.model.ConsultationLog;
import com.example.backend.domain.chat.repository.ConsultationLogRepository;
import com.example.backend.domain.chat.service.AiChatPort;
import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.infrastructure.persistence.mapper.ChatMessageMapper;
import com.example.backend.domain.order.model.HistoricalOrder;
import com.example.backend.domain.order.repository.HistoricalOrderRepository;
import com.example.backend.domain.shared.event.DomainEventPublisher;
import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.domain.workorder.repository.WorkOrderRepository;
import com.example.backend.domain.shared.messaging.MessageBusPort;
import com.example.backend.infrastructure.messaging.RedisStreamAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ChatApplicationService {
    private final ConsultationLogRepository consultationLogRepository;
    private final HistoricalOrderRepository orderRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AiChatPort aiChatPort;
    private final DomainEventPublisher eventPublisher;
    private final SessionStatePort sessionStatePort;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    private MessageBusPort messageBusPort;
    private RedisStreamAdapter redisStreamAdapter;

    public ChatApplicationService(ConsultationLogRepository consultationLogRepository,
                                   HistoricalOrderRepository orderRepository,
                                   WorkOrderRepository workOrderRepository,
                                   AiChatPort aiChatPort,
                                   DomainEventPublisher eventPublisher,
                                   SessionStatePort sessionStatePort,
                                   ChatMessageMapper chatMessageMapper,
                                   ObjectMapper objectMapper) {
        this.consultationLogRepository = consultationLogRepository;
        this.orderRepository = orderRepository;
        this.workOrderRepository = workOrderRepository;
        this.aiChatPort = aiChatPort;
        this.eventPublisher = eventPublisher;
        this.sessionStatePort = sessionStatePort;
        this.chatMessageMapper = chatMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setMessageBusPort(MessageBusPort messageBusPort) {
        this.messageBusPort = messageBusPort;
    }

    @Autowired(required = false)
    public void setRedisStreamAdapter(RedisStreamAdapter redisStreamAdapter) {
        this.redisStreamAdapter = redisStreamAdapter;
    }

    public void processStreamingMessage(String sessionId, Long userId, Set<String> roles,
                                         String content, Consumer<String> onData) {
        if (sessionStatePort.isAiBlocked(sessionId)) {
            log.info("AI blocked for session {}, message dropped", sessionId);
            onData.accept("");
            return;
        }
        Set<String> finalRoles = roles != null ? roles : Set.of();
        Map<String, Object> inputs = buildInputs(userId, finalRoles);

        String conversationId = consultationLogRepository
                .findLatestWithConversationIdBySessionId(sessionId)
                .map(ConsultationLog::getDifyConversationId)
                .orElse(null);

        StringBuilder fullResponseBuilder = new StringBuilder();
        String[] conversationIdRef = {conversationId};
        String[] errorMessageRef = {null};

        Consumer<String> dataConsumer = dataStr -> {
            try {
                JsonNode dataNode = objectMapper.readTree(dataStr);
                if (dataNode.has("event") && "message".equals(dataNode.get("event").asText())) {
                    String answerFragment = dataNode.has("answer") ? dataNode.get("answer").asText() : "";
                    fullResponseBuilder.append(answerFragment);
                    if (dataNode.has("conversation_id")) {
                        conversationIdRef[0] = dataNode.get("conversation_id").asText();
                    }
                    onData.accept(answerFragment);
                }
            } catch (Exception e) {
                log.error("Error parsing stream data: {}", e.getMessage());
            }
        };

        Consumer<String> errorConsumer = errorMsg -> {
            log.error("Streaming error: {}", errorMsg);
            errorMessageRef[0] = errorMsg;
        };

        String difyUser = userId != null ? String.valueOf(userId) : sessionId;
        try {
            aiChatPort.sendStreamingMessage(content, difyUser, conversationIdRef[0], inputs, dataConsumer, errorConsumer);
        } catch (Exception e) {
            errorMessageRef[0] = e.getMessage();
            throw new ExternalServiceException("AI chat service error: " + e.getMessage(), e);
        }

        String aiResponse = fullResponseBuilder.toString();
        if (aiResponse.isEmpty() && errorMessageRef[0] != null) {
            aiResponse = "Error: " + errorMessageRef[0];
        }

        String intent = processJsonActions(aiResponse, userId, content);

        ConsultationLog log = ConsultationLog.create(sessionId, userId, content, "WEB");
        log.setAiResponse(aiResponse);
        log.setDifyConversationId(conversationIdRef[0]);
        log.setIntent(intent);
        consultationLogRepository.save(log);

        eventPublisher.publish(new ConversationCompletedEvent(sessionId, userId, aiResponse, intent, conversationIdRef[0]));

        // ── 双路分发（异步消息方案）──
        Map<String, Object> dispatchPayload = new LinkedHashMap<>();
        dispatchPayload.put("sessionId", sessionId);
        dispatchPayload.put("userId", userId);
        dispatchPayload.put("roles", finalRoles);
        dispatchPayload.put("intent", intent);
        dispatchPayload.put("aiResponse", aiResponse);
        dispatchPayload.put("userInput", content);

        // 实时通路：Redis Stream → WebSocket 渲染
        if (redisStreamAdapter != null) {
            redisStreamAdapter.publish(sessionId, dispatchPayload);
        }

        // 异步通路：MQ → 自动打标、总结落盘
        if (messageBusPort != null) {
            messageBusPort.send("chat.async", dispatchPayload);
        }
    }

    public List<ConsultationLog> getHistory(String sessionId) {
        return consultationLogRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
    }

    @Transactional
    public void updateSatisfaction(String sessionId, Long userId, Integer satisfaction, Long agentId) {
        // #region debug-point H2:check-params
        log.info("[DEBUG-satisfaction] updateSatisfaction called: sessionId={}, satisfaction={}, agentId={}", sessionId, satisfaction, agentId);
        // #endregion

        // 写入 chat_messages：最后一条 AGENT 消息 —— 天然带有 sender_id（= agentId）
        int updated = chatMessageMapper.updateSatisfactionOnLatestAgentMsg(sessionId, satisfaction);
        log.info("[DEBUG-satisfaction] chat_messages updated: {} rows", updated);

        // 同时写入 consultation_logs
        // 修复：如果 findLatestBySessionId 返回空（例如刚创建会话尚无日志），
        // 则新建一条 consultation_log 确保满意度和 agentId 被持久化
        ConsultationLog entry = consultationLogRepository.findLatestBySessionId(sessionId)
                .orElseGet(() -> ConsultationLog.create(sessionId, userId, "", "WEB"));
        entry.rateSatisfaction(satisfaction);
        if (agentId != null) {
            entry.setAgentId(agentId);
        }
        consultationLogRepository.save(entry);
        eventPublisher.publish(new SatisfactionRatedEvent(sessionId, userId, satisfaction));
    }

    private Map<String, Object> buildInputs(Long userId, Set<String> roles) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("roles", roles);
        if (userId != null) {
            List<HistoricalOrder> orders = orderRepository.findByUserIdOrderByCreateTimeDesc(userId);
            if (!orders.isEmpty()) {
                try {
                    inputs.put("history_orders", objectMapper.writeValueAsString(orders));
                } catch (Exception e) {
                    log.error("Failed to serialize history orders", e);
                }
            }
        }
        return inputs;
    }

    private String processJsonActions(String aiResponse, Long userId, String originalContent) {
        String jsonBlock = extractJsonBlock(aiResponse);
        if (jsonBlock == null) return null;
        try {
            JsonNode rootNode = objectMapper.readTree(jsonBlock);
            if (!rootNode.has("action")) return null;

            String action = rootNode.get("action").asText();
            JsonNode dataNode = rootNode.path("data");

            if ("create_work_order".equals(action) && userId != null) {
                String type = dataNode.has("type") ? dataNode.get("type").asText() : "after_sales";
                WorkOrder wo = WorkOrder.create(userId,
                        dataNode.has("title") ? dataNode.get("title").asText() : "User Work Order",
                        dataNode.has("description") ? dataNode.get("description").asText() : originalContent,
                        type, "medium");
                WorkOrder created = workOrderRepository.save(wo);
                eventPublisher.publish(new com.example.backend.domain.workorder.event.WorkOrderCreatedEvent(
                        created.getId(), userId, type, "medium"));
                return "CREATE_WORK_ORDER";
            }

            return action;
        } catch (Exception e) {
            log.warn("JSON action parsing warning: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonBlock(String text) {
        if (text == null) return null;
        Pattern codeBlockPattern = Pattern.compile("```(?:json|JSON)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(text);
        if (matcher.find()) return matcher.group(1);

        try {
            int lastCloseBrace = text.lastIndexOf('}');
            if (lastCloseBrace != -1) {
                int balance = 0;
                for (int i = lastCloseBrace; i >= 0; i--) {
                    if (text.charAt(i) == '}') balance++;
                    else if (text.charAt(i) == '{') {
                        balance--;
                        if (balance == 0) {
                            String potentialJson = text.substring(i, lastCloseBrace + 1);
                            if (potentialJson.contains("\"action\"")) return potentialJson;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error in manual JSON extraction: {}", e.getMessage());
        }
        return null;
    }
}
