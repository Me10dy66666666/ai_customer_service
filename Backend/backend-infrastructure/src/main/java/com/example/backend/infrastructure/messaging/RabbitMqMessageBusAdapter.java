package com.example.backend.infrastructure.messaging;

import com.example.backend.domain.shared.messaging.MessageBusPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(value = "spring.rabbitmq.host")
public class RabbitMqMessageBusAdapter implements MessageBusPort {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public static final String EXCHANGE_NAME = "ai_cs.async.exchange";

    public RabbitMqMessageBusAdapter(RabbitTemplate rabbitTemplate,
                                      ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String channel, Object payload) {
        send(channel, Map.of(), payload);
    }

    @Override
    public void send(String channel, Map<String, Object> headers, Object payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            MessageProperties props = new MessageProperties();
            props.setContentType("application/json");
            headers.forEach((k, v) -> props.setHeader(k, v));

            org.springframework.amqp.core.Message msg = new org.springframework.amqp.core.Message(body, props);
            rabbitTemplate.send(EXCHANGE_NAME, channel, msg);
            log.debug("MQ sent → exchange: {}, routing: {}", EXCHANGE_NAME, channel);
        } catch (JsonProcessingException e) {
            log.error("MQ serialize failure for channel {}: {}", channel, e.getMessage());
            throw new MessagingException("Failed to serialize payload", e);
        }
    }

    @Override
    public String implementationName() {
        return "RabbitMQ";
    }

    /**
     * 声明 Topic 交换机（启动时自动创建）。
     */
    @Configuration
    static class RabbitMqDeclarations {

        static final String CHAT_ASYNC_QUEUE = "ai_cs.chat.async";
        static final String OCR_QUEUE = "ai_cs.ocr.queue";
        static final String SESSION_CLOSED_QUEUE = "ai_cs.session.closed";
        static final String DIFY_UPLOAD_QUEUE = "ai_cs.knowledge.dify";

        @Bean
        TopicExchange asyncExchange() {
            return ExchangeBuilder.topicExchange(EXCHANGE_NAME).durable(true).build();
        }

        @Bean
        Queue chatAsyncQueue() {
            return QueueBuilder.durable(CHAT_ASYNC_QUEUE).build();
        }

        @Bean
        Queue sessionClosedQueue() {
            return QueueBuilder.durable(SESSION_CLOSED_QUEUE).build();
        }

        @Bean
        Queue ocrQueue() {
            return QueueBuilder.durable(OCR_QUEUE)
                    .withArgument("x-dead-letter-exchange", "")
                    .withArgument("x-dead-letter-routing-key", OCR_QUEUE + ".dlq")
                    .build();
        }

        @Bean
        Queue ocrDlq() {
            return QueueBuilder.durable(OCR_QUEUE + ".dlq").build();
        }

        @Bean
        Binding chatAsyncBinding(Queue chatAsyncQueue, TopicExchange asyncExchange) {
            return BindingBuilder.bind(chatAsyncQueue).to(asyncExchange).with("chat.async");
        }

        @Bean
        Binding ocrBinding(Queue ocrQueue, TopicExchange asyncExchange) {
            return BindingBuilder.bind(ocrQueue).to(asyncExchange).with("ocr.process");
        }

        @Bean
        Binding sessionClosedBinding(Queue sessionClosedQueue, TopicExchange asyncExchange) {
            return BindingBuilder.bind(sessionClosedQueue).to(asyncExchange).with("session.closed");
        }

        @Bean
        Queue difyUploadQueue() {
            return QueueBuilder.durable(DIFY_UPLOAD_QUEUE)
                    .withArgument("x-dead-letter-exchange", "")
                    .withArgument("x-dead-letter-routing-key", DIFY_UPLOAD_QUEUE + ".dlq")
                    .build();
        }

        @Bean
        Queue difyUploadDlq() {
            return QueueBuilder.durable(DIFY_UPLOAD_QUEUE + ".dlq").build();
        }

        @Bean
        Binding difyUploadBinding(Queue difyUploadQueue, TopicExchange asyncExchange) {
            return BindingBuilder.bind(difyUploadQueue).to(asyncExchange).with("knowledge.dify");
        }
    }

    public static class MessagingException extends RuntimeException {
        public MessagingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
