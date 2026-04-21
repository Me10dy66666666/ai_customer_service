package com.example.backend.service;

import com.example.backend.client.DifyClient;
import com.example.backend.common.exception.ExternalServiceException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.common.service.RedisService;
import com.example.backend.entity.ConsultationLog;
import com.example.backend.entity.HistoricalOrder;
import com.example.backend.entity.WorkOrder;
import com.example.backend.mapper.ConsultationLogMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ConsultationLogMapper consultationLogMapper;
    private final DifyClient difyClient;
    private final WorkOrderService workOrderService;
    private final UserProfileService userProfileService;
    private final OrderService orderService;
    private final RedisService redisService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void processStreamingMessage(String sessionId, Long userId, Integer userType, String content, Consumer<String> onData) {
        // 缓存 Key 改为全局唯一，不再依赖 sessionId
        String cacheKey = "chat:cache:global:" + content.trim();
        Object cachedResponse = null;
        try {
            cachedResponse = redisService.get(cacheKey);
        } catch (Exception e) {
            logger.error("Error retrieving from Redis cache for key {}: {}", cacheKey, e.getMessage());
            // Continue without cache if Redis is unavailable
        }

        Map<String, Object> inputs = new HashMap<>();
        int finalUserType = userType != null ? userType : 0;
        inputs.put("userType", finalUserType);

        if (userId != null) {
            List<HistoricalOrder> orders = orderService.getOrdersByUserId(userId);
            if (!orders.isEmpty()) {
                try {
                    inputs.put("history_orders", objectMapper.writeValueAsString(orders));
                } catch (Exception e) {
                    logger.error("Failed to serialize history orders", e);
                }
            }
        }

        String conversationId = null;
        ConsultationLog lastLog = consultationLogMapper.findFirstBySessionIdAndDifyConversationIdIsNotNullOrderByCreateTimeDesc(sessionId);
        if (lastLog != null) {
            conversationId = lastLog.getDifyConversationId();
        }

        StringBuilder fullResponseBuilder = new StringBuilder();
        final String[] conversationIdRef = {conversationId};
        final String[] errorMessageRef = {null};

        if (cachedResponse != null) {
            logger.info("Chat cache hit for sessionId: {}, content: {}", sessionId, content);
            String responseStr = cachedResponse.toString();
            fullResponseBuilder.append(responseStr);
            onData.accept(responseStr);
            // Skip calling Dify
        } else {
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
                    logger.error("Error parsing stream data: {}", e.getMessage());
                }
            };

            Consumer<String> errorConsumer = errorMsg -> {
                logger.error("Streaming error: {}", errorMsg);
                errorMessageRef[0] = errorMsg;
                onData.accept("\n[System Error: " + errorMsg + "]");
            };

            String difyUser = userId != null ? String.valueOf(userId) : sessionId;
            try {
                difyClient.sendStreamingMessage(content, difyUser, conversationId, inputs, dataConsumer, errorConsumer);
            } catch (HttpClientErrorException e) {
                errorMessageRef[0] = e.getMessage();
                int statusCode = e.getStatusCode().value();

                if (statusCode == 404 && e.getResponseBodyAsString().contains("Conversation Not Exists")) {
                    logger.warn("Conversation {} not found, retrying with new conversation", conversationId);
                    fullResponseBuilder.setLength(0);
                    conversationIdRef[0] = null;
                    errorMessageRef[0] = null;
                    try {
                        difyClient.sendStreamingMessage(content, difyUser, null, inputs, dataConsumer, errorConsumer);
                    } catch (Exception ex) {
                        logger.error("Retry failed: {}", ex.getMessage());
                        errorMessageRef[0] = "Retry failed: " + ex.getMessage();
                        throw new ExternalServiceException("Failed to retry conversation with Dify", ex);
                    }
                } else if (statusCode == 401) {
                    errorMessageRef[0] = "Dify authentication failed, please check API key";
                    throw new ExternalServiceException(errorMessageRef[0], e);
                } else if (statusCode == 404) {
                    errorMessageRef[0] = "Dify resource not found (404), please check configuration";
                    throw new ExternalServiceException(errorMessageRef[0], e);
                } else if (statusCode >= 400 && statusCode < 500) {
                    errorMessageRef[0] = "Dify client error (" + statusCode + "): " + e.getResponseBodyAsString();
                    throw new ExternalServiceException(errorMessageRef[0], e);
                } else {
                    errorMessageRef[0] = "Dify server error (" + statusCode + "): " + e.getMessage();
                    throw new ExternalServiceException(errorMessageRef[0], e);
                }
            } catch (org.springframework.web.client.ResourceAccessException e) {
                errorMessageRef[0] = "Connection to Dify timed out or failed";
                logger.error("Dify connection error: {}", e.getMessage());
                throw new ExternalServiceException(errorMessageRef[0], e);
            } catch (Exception e) {
                errorMessageRef[0] = "Unexpected error while calling Dify: " + e.getMessage();
                logger.error("Unexpected error calling Dify API", e);
                throw new ExternalServiceException(errorMessageRef[0], e);
            }
        }

        try {
            String aiResponse = fullResponseBuilder.toString();
            if (aiResponse.isEmpty() && errorMessageRef[0] != null) {
                aiResponse = "Error: " + errorMessageRef[0];
            }
            logger.debug("AI Response: {}", aiResponse);

            // If it was a cache miss and we have a valid response, store it
            if (cachedResponse == null && !aiResponse.isEmpty() && errorMessageRef[0] == null) {
                try {
                    redisService.set(cacheKey, aiResponse, 1L, java.util.concurrent.TimeUnit.HOURS);
                    logger.info("Chat response cached for key: {}", cacheKey);
                } catch (Exception e) {
                    logger.error("Error saving to Redis cache for key {}: {}", cacheKey, e.getMessage());
                }
            }

            String intent = null;
            String jsonBlock = extractJsonBlock(aiResponse);
            if (jsonBlock != null) {
                logger.debug("Extracted JSON Block: {}", jsonBlock);
                try {
                    JsonNode rootNode = objectMapper.readTree(jsonBlock);
                    intent = handleAiAction(rootNode, userId, content, onData::accept);
                } catch (Exception e) {
                    logger.warn("JSON parsing warning: {}", e.getMessage());
                }
            }

            ConsultationLog log = new ConsultationLog();
            log.setSessionId(sessionId);
            log.setUserId(userId);
            log.setUserType(finalUserType);
            log.setUserInput(content);
            log.setAiResponse(aiResponse);
            log.setDifyConversationId(conversationIdRef[0]);
            log.setChannel("WEB");
            log.setIntent(intent);

            consultationLogMapper.insert(log);
            userProfileService.updateVisitorStats(sessionId);
        } catch (Exception e) {
            logger.error("Error finalizing chat message processing", e);
        }
    }

    public ConsultationLog processMessage(String sessionId, Long userId, Integer userType, String content) {
        // 缓存 Key 改为全局唯一，不再依赖 sessionId
        String cacheKey = "chat:cache:global:" + content.trim();
        Object cachedResponse = null;
        try {
            cachedResponse = redisService.get(cacheKey);
        } catch (Exception e) {
            logger.error("Error retrieving from Redis cache for key {}: {}", cacheKey, e.getMessage());
            // Continue without cache if Redis is unavailable
        }

        Map<String, Object> inputs = new HashMap<>();
        int finalUserType = userType != null ? userType : 0;
        inputs.put("userType", finalUserType);

        if (userId != null) {
            List<HistoricalOrder> orders = orderService.getOrdersByUserId(userId);
            if (!orders.isEmpty()) {
                try {
                    inputs.put("history_orders", objectMapper.writeValueAsString(orders));
                } catch (Exception e) {
                    logger.error("Failed to serialize history orders", e);
                }
            }
        }

        String conversationId = null;
        ConsultationLog lastLog = consultationLogMapper.findFirstBySessionIdAndDifyConversationIdIsNotNullOrderByCreateTimeDesc(sessionId);
        if (lastLog != null) {
            conversationId = lastLog.getDifyConversationId();
        }

        String aiResponse = "";
        String newConversationId = conversationId;
        String intent = null;
        String errorMessage = null;

        if (cachedResponse != null) {
            logger.info("Chat cache hit (non-stream) for sessionId: {}, content: {}", sessionId, content);
            aiResponse = cachedResponse.toString();
        } else {
            try {
                Map<String, String> result = difyClient.sendMessage(content, sessionId, conversationId, inputs);
                aiResponse = result.get("answer");
                newConversationId = result.get("conversation_id");

                String jsonBlock = extractJsonBlock(aiResponse);
                if (jsonBlock != null) {
                    try {
                        JsonNode rootNode = objectMapper.readTree(jsonBlock);
                        StringBuilder notificationBuilder = new StringBuilder();
                        intent = handleAiAction(rootNode, userId, content, notificationBuilder::append);
                        if (notificationBuilder.length() > 0) {
                            aiResponse += notificationBuilder;
                        }
                    } catch (Exception e) {
                        logger.warn("JSON parsing warning: {}", e.getMessage());
                    }
                }

                // Cache the response
                if (!aiResponse.isEmpty()) {
                    try {
                        redisService.set(cacheKey, aiResponse, 1L, TimeUnit.HOURS);
                        logger.info("Chat response cached for key: {}", cacheKey);
                    } catch (Exception e) {
                        logger.error("Error saving to Redis cache for key {}: {}", cacheKey, e.getMessage());
                    }
                }

            } catch (HttpClientErrorException e) {
                errorMessage = e.getMessage();
                int statusCode = e.getStatusCode().value();

                if (statusCode == 404) {
                    if (e.getResponseBodyAsString().contains("Conversation Not Exists")) {
                        errorMessage = "Conversation has expired, please refresh and retry";
                    } else {
                        errorMessage = "Dify resource not found (404)";
                    }
                } else if (statusCode == 401) {
                    errorMessage = "Dify authentication failed, please check API key";
                } else {
                    errorMessage = "Dify API Error (" + statusCode + "): " + e.getMessage();
                }
            } catch (org.springframework.web.client.ResourceAccessException e) {
                errorMessage = "Connection to Dify timed out or failed";
            } catch (Exception e) {
                logger.error("Error processing message", e);
                errorMessage = "Service busy: " + e.getMessage();
            } finally {
                ConsultationLog log = new ConsultationLog();
                log.setSessionId(sessionId);
                log.setUserId(userId);
                log.setUserType(finalUserType);
                log.setUserInput(content);
                log.setAiResponse(aiResponse != null && !aiResponse.isEmpty() ? aiResponse : (errorMessage != null ? "Error: " + errorMessage : ""));
                log.setDifyConversationId(newConversationId);
                log.setChannel("WEB");
                log.setIntent(intent);

                consultationLogMapper.insert(log);
                userProfileService.updateVisitorStats(sessionId);
                
                if (errorMessage != null) {
                    throw new ExternalServiceException(errorMessage);
                }
            }
        }

        return consultationLogMapper.findFirstBySessionIdOrderByCreateTimeDesc(sessionId);
    }

    /**
     * Handle AI Actions based on JSON output
     * Implements requirements FR-022 to FR-026
     */
    private String handleAiAction(JsonNode rootNode, Long userId, String originalContent, Consumer<String> notifier) {
        if (!rootNode.has("action")) {
            return null;
        }
        String action = rootNode.get("action").asText();
        String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "";
        JsonNode dataNode = rootNode.path("data");

        if ("create_work_order".equals(action)) {
            if (userId == null) {
                notifier.accept("\n\n[System Notice: Work order request detected, please login first.]");
                return "CREATE_WORK_ORDER_FAILED_NO_AUTH";
            }

            String type = dataNode.has("type") ? dataNode.get("type").asText() : "after_sales";
            if ("quality_fault".equals(type) || "product_quality".equals(reason)) {
                type = "quality_fault";
            }

            createWorkOrder(userId, dataNode, type, originalContent, notifier);
            return "CREATE_WORK_ORDER";
        } else if ("transfer_manual".equals(action)) {
            if ("intent_low_confidence".equals(reason)) {
                notifier.accept("\n\n[System Notice: Intent is unclear, transferring to manual support.]");
            } else if ("kb_no_match".equals(reason)) {
                notifier.accept("\n\n[System Notice: No matching knowledge found, transferring to manual support.]");
                if (userId != null) {
                    createInternalWorkOrder(userId, "Knowledge gap: " + originalContent, "kb_supplement");
                }
            } else if ("quote_failed".equals(reason)) {
                notifier.accept("\n\n[System Notice: Quote calculation failed, transferring to manual sales.]");
            } else if ("complex_issue".equals(reason)) {
                notifier.accept("\n\n[System Notice: Complex issue detected, transferring to specialist.]");
                if (userId != null) {
                    createWorkOrder(userId, dataNode, "after_sales", originalContent, msg -> {});
                }
            } else {
                notifier.accept("\n\n[System Notice: Transferring to manual support.]");
            }
            return "TRANSFER_MANUAL";
        }

        return null;
    }

    private void createWorkOrder(Long userId, JsonNode dataNode, String type, String originalContent, Consumer<String> notifier) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setUserId(userId);
        workOrder.setTitle(dataNode.has("title") ? dataNode.get("title").asText() : "User Work Order");
        workOrder.setDescription(dataNode.has("description") ? dataNode.get("description").asText() : originalContent);
        workOrder.setType(type);
        workOrder.setPriority(dataNode.has("priority") ? dataNode.get("priority").asText() : "medium");
        workOrder.setStatus("pending");

        WorkOrder created = workOrderService.createWorkOrder(workOrder);
        notifier.accept("\n\n[System Notice: Work order created, id #" + created.getId() + ", type: " + type + "]");
    }

    private void createInternalWorkOrder(Long userId, String description, String type) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setUserId(userId);
        workOrder.setTitle("System Auto Work Order: " + type);
        workOrder.setDescription(description);
        workOrder.setType(type);
        workOrder.setPriority("low");
        workOrder.setStatus("pending");
        workOrderService.createWorkOrder(workOrder);
    }

    private String extractJsonBlock(String text) {
        if (text == null) {
            return null;
        }
        Pattern codeBlockPattern = Pattern.compile("```(?:json|JSON)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        try {
            int lastCloseBrace = text.lastIndexOf('}');
            if (lastCloseBrace != -1) {
                int openBrace = -1;
                int balance = 0;
                for (int i = lastCloseBrace; i >= 0; i--) {
                    char c = text.charAt(i);
                    if (c == '}') {
                        balance++;
                    } else if (c == '{') {
                        balance--;
                        if (balance == 0) {
                            openBrace = i;
                            break;
                        }
                    }
                }

                if (openBrace != -1) {
                    String potentialJson = text.substring(openBrace, lastCloseBrace + 1);
                    if (potentialJson.contains("\"action\"") || potentialJson.contains("'action'")) {
                        return potentialJson;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error in manual JSON extraction: {}", e.getMessage());
        }

        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String potentialJson = text.substring(firstBrace, lastBrace + 1);
            if (potentialJson.contains("\"action\"") || potentialJson.contains("'action'")) {
                return potentialJson;
            }
        }
        return null;
    }

    public List<ConsultationLog> getHistory(String sessionId) {
        return consultationLogMapper.findBySessionIdOrderByCreateTimeAsc(sessionId);
    }
}
