package com.example.backend.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Component
public class RedisStreamAdapter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ExecutorService listenerPool = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            r -> { Thread t = new Thread(r, "rs-lsn"); t.setDaemon(true); return t; });

    private final ExecutorService publishPool = Executors.newFixedThreadPool(
            2,
            r -> { Thread t = new Thread(r, "rs-pub"); t.setDaemon(true); return t; });

    private final Map<String, AtomicBoolean> activeListeners = new ConcurrentHashMap<>();

    public static final String CHAT_STREAM_KEY = "ai_cs:stream:chat";
    public static final String AGENT_QUEUE_STREAM = "ai_cs:stream:agent_queue";
    public static final String AGENT_QUEUE_GROUP = "agent-group";
    public static final String AGENT_STREAM_PREFIX = "ai_cs:stream:agent:";
    public static final String USER_STREAM_PREFIX = "ai_cs:stream:user:";
    public static final int MAX_STREAM_LEN = 2000;

    public RedisStreamAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishToStream(String streamKey, Object payload) {
        try {
            String json = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
            Map<String, String> body = Map.of("json", json);
            redisTemplate.opsForStream().add(
                    StreamRecords.newRecord().in(streamKey).ofMap(body));
            redisTemplate.opsForStream().trim(streamKey, MAX_STREAM_LEN, false);
            log.debug("RS XADD \u2192 {}", streamKey);
        } catch (Exception e) {
            log.error("RS publish {} failed: {}", streamKey, e.getMessage());
        }
    }

    public CompletableFuture<Void> publishToStreamAsync(String streamKey, Object payload) {
        return CompletableFuture.runAsync(
                () -> publishToStream(streamKey, payload), publishPool);
    }

    public void publish(String sessionId, Object payload) {
        publishToStream(CHAT_STREAM_KEY, Map.of("sessionId", sessionId, "payload", payload));
    }

    public void ensureConsumerGroup(String streamKey, String group) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, group);
            log.info("Consumer Group created: {} group={}", streamKey, group);
        } catch (Exception e) {
            log.debug("Consumer Group {} already exists for {}", group, streamKey);
        }
    }

    @SuppressWarnings("unchecked")
    public Runnable startListener(String streamKey, String consumerId,
                                   Consumer<Map<String, Object>> onMessage) {
        AtomicBoolean running = new AtomicBoolean(true);
        activeListeners.put(consumerId, running);

        listenerPool.submit(() -> {
            log.info("RS listener started: {} consumer={}", streamKey, consumerId);
            String lastId = "$";
            while (running.get()) {
                try {
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                            .read(StreamReadOptions.empty().count(10)
                                    .block(Duration.ofSeconds(2)),
                                  StreamOffset.create(streamKey, ReadOffset.from(lastId)));

                    if (records == null || records.isEmpty()) continue;

                    for (MapRecord<String, Object, Object> rec : records) {
                        lastId = rec.getId().getValue();
                        Object jsonObj = rec.getValue().get("json");
                        if (jsonObj != null) {
                            Map<String, Object> msg = objectMapper.readValue(
                                    jsonObj.toString(), Map.class);
                            onMessage.accept(msg);
                        }
                    }
                } catch (Exception e) {
                    if (running.get())
                        log.warn("RS listener {} error: {}", streamKey, e.getMessage());
                }
            }
            log.info("RS listener stopped: {}", consumerId);
        });

        return () -> {
            running.set(false);
            activeListeners.remove(consumerId);
        };
    }

    @SuppressWarnings("unchecked")
    public Runnable startGroupListener(String streamKey, String group, String consumerId,
                                        Consumer<Map<String, Object>> onMessage) {
        AtomicBoolean running = new AtomicBoolean(true);
        activeListeners.put(consumerId, running);
        ensureConsumerGroup(streamKey, group);

        listenerPool.submit(() -> {
            log.info("RS group listener started: {} group={} consumer={}", streamKey, group, consumerId);
            while (running.get()) {
                try {
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                            .read(org.springframework.data.redis.connection.stream.Consumer.from(group, consumerId),
                                  StreamReadOptions.empty().count(10)
                                          .block(Duration.ofSeconds(2)),
                                  StreamOffset.create(streamKey, ReadOffset.lastConsumed()));

                    if (records == null || records.isEmpty()) continue;

                    for (MapRecord<String, Object, Object> rec : records) {
                        Object jsonObj = rec.getValue().get("json");
                        if (jsonObj == null) continue;
                        Map<String, Object> msg = objectMapper.readValue(
                                jsonObj.toString(), Map.class);
                        try {
                            onMessage.accept(msg);
                            redisTemplate.opsForStream()
                                    .acknowledge(streamKey, group, rec.getId().getValue());
                        } catch (Exception ex) {
                            log.warn("Handler failed for {}, will retry via XCLAIM", rec.getId().getValue());
                        }
                    }
                } catch (Exception e) {
                    if (running.get())
                        log.warn("RS group listener {} error: {}", streamKey, e.getMessage());
                }
            }
            activeListeners.remove(consumerId);
            log.info("RS group listener stopped: {}", consumerId);
        });

        return () -> {
            running.set(false);
            activeListeners.remove(consumerId);
        };
    }

    public void stopListener(String consumerId) {
        AtomicBoolean r = activeListeners.remove(consumerId);
        if (r != null) r.set(false);
    }
}
