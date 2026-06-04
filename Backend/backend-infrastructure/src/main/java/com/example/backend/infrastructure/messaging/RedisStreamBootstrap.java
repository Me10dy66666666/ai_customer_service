package com.example.backend.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisStreamBootstrap {

    @Autowired(required = false)
    private RedisStreamAdapter redisStreamAdapter;

    @EventListener(ApplicationReadyEvent.class)
    public void initConsumerGroups() {
        if (redisStreamAdapter == null) {
            log.info("RedisStreamAdapter not available, skipping Consumer Group init");
            return;
        }
        redisStreamAdapter.ensureConsumerGroup(
                RedisStreamAdapter.AGENT_QUEUE_STREAM,
                RedisStreamAdapter.AGENT_QUEUE_GROUP);
        log.info("Redis Consumer Group initialized: {} → {}",
                RedisStreamAdapter.AGENT_QUEUE_STREAM,
                RedisStreamAdapter.AGENT_QUEUE_GROUP);
    }
}
