package com.example.backend.domain.shared.messaging;

import java.util.Map;

/**
 * 消息总线抽象端口。
 * 封装中间件（RabbitMQ / Kafka / RocketMQ）的生产能力。
 * application 层仅依赖此接口，实现可在 infrastructure 层热插拔。
 */
public interface MessageBusPort {

    /**
     * 发送一条异步消息到指定通道。
     *
     * @param channel  通道/路由键（如 "chat.async", "ocr.queue"）
     * @param payload  消息体（会被序列化为 JSON）
     */
    void send(String channel, Object payload);

    /**
     * 发送一条带扩展头的异步消息。
     *
     * @param channel  通道/路由键
     * @param headers  消息头（如指定延迟、优先级）
     * @param payload  消息体
     */
    void send(String channel, Map<String, Object> headers, Object payload);

    /**
     * 当前实现类型标识（用于运维日志）。
     */
    String implementationName();
}
