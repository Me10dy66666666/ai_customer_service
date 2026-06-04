package com.example.backend.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费「聊天异步」队列：
 *   - 自动打标（意图识别）、动态总结、数据库落盘
 *   实际处理逻辑委托给 application 层的事件监听器。
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "spring.rabbitmq.host")
public class ChatAsyncConsumer {

    private final ObjectMapper objectMapper;

    public ChatAsyncConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMqMessageBusAdapter.RabbitMqDeclarations.CHAT_ASYNC_QUEUE)
    public void onMessage(byte[] raw) {
        try {
            Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
            String sessionId = (String) payload.get("sessionId");
            String intent = (String) payload.get("intent");
            log.info("ChatAsync consumed: session={}, intent={}", sessionId, intent);
            // 后续接入 AI 打标 / 总结服务
        } catch (Exception e) {
            log.error("ChatAsync consumer error: {}", e.getMessage());
        }
    }
}
