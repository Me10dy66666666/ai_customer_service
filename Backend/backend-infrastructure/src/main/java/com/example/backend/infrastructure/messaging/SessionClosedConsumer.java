package com.example.backend.infrastructure.messaging;

import com.example.backend.domain.chat.model.ConsultationLog;
import com.example.backend.domain.chat.repository.ConsultationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 濞村吋淇洪惁浠嬪礂閹惰姤锛旈柛姘缁辨挸顫㈤妷銊﹀劙闁烩晜蓱缁夐鎷圭涵鍛亾閸涱偀鍋? * 婵炴垵鐗愰崹?session.closed 闂傚啰鍠庨崹顏堟晬鐏炵晫娈洪悗鐟版湰閺嗭綁鎳曟繝鍌樹函閻犱焦婢樼紞宥夊箥瑜版帒娅ら柛鎰懃閸?consultation_logs闁? */
@Slf4j
@Component
@ConditionalOnProperty(value = "spring.rabbitmq.host")
public class SessionClosedConsumer {

    private final ConsultationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public SessionClosedConsumer(ConsultationLogRepository logRepository,
                                  ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMqMessageBusAdapter.RabbitMqDeclarations.SESSION_CLOSED_QUEUE)
    public void onMessage(byte[] raw) {
        try {
            Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
            String sessionId = (String) payload.get("sessionId");
            if (sessionId == null) return;

            List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
            if (messages == null || messages.isEmpty()) return;

            log.info("Archiving session {}: {} messages 闁?MySQL", sessionId, messages.size());

            for (Map<String, Object> msg : messages) {
                String from = (String) msg.getOrDefault("from", "user");
                String content = (String) msg.getOrDefault("content", "");
                Long userId = parseLong(msg.get("userId"));

                ConsultationLog log = ConsultationLog.create(sessionId, userId, content, "HUMAN");

                if ("agent".equals(from)) {
                    log.setAiResponse(content);
                    log.setUserInput("");
                } else if ("system".equals(from)) {
                    log.setAiResponse(content);
                    log.setUserInput("");
                    log.setChannel("SYSTEM");
                }

                logRepository.save(log);
            }

            log.info("Session {} archived successfully: {} records", sessionId, messages.size());

        } catch (Exception e) {
            log.error("Session archive consumer error: {}", e.getMessage());
        }
    }

    private Long parseLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                // no-op
            }
        }
        return null;
    }
}
