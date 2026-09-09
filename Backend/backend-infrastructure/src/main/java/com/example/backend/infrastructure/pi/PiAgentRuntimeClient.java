package com.example.backend.infrastructure.pi;

import com.example.backend.infrastructure.observability.AgentRuntimeMetrics;
import com.example.backend.infrastructure.persistence.entity.User;
import com.example.backend.infrastructure.persistence.mapper.UserMapper;
import com.example.backend.infrastructure.resilience.ExternalCallRetryPolicy;
import com.example.backend.infrastructure.security.JwtUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.micrometer.core.instrument.Timer;

/**
 * Java-owned BFF boundary for the Pi Agent Runtime. Pi never receives a user
 * identity as a model argument; its capability is issued here and forwarded
 * only to the Java tool gateway.
 */
@Slf4j
@Component
public class PiAgentRuntimeClient {
    private static final String RUNTIME = "pi";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> CUSTOMER_SERVICE_SCOPES = Set.of(
            "order:read:self", "work_order:propose:self", "knowledge:read");

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final AgentRuntimeMetrics metrics;
    private final ExternalCallRetryPolicy retryPolicy;
    private final Semaphore bulkhead;
    private final int failureThreshold;
    private final long circuitCooldownNanos;
    private final long permitIntervalNanos;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilNanos = new AtomicLong();
    private final AtomicLong nextPermitNanos = new AtomicLong();

    @Value("${pi.runtime.base-url:http://localhost:3001}")
    private String baseUrl;
    @Value("${pi.runtime.service-token:}")
    private String serviceToken;
    @Value("${pi.runtime.model:unknown}")
    private String model;

    public PiAgentRuntimeClient(ObjectMapper objectMapper,
                                AgentRuntimeMetrics metrics,
                                ExternalCallRetryPolicy retryPolicy,
                                JwtUtils jwtUtils,
                                UserMapper userMapper,
                                @Value("${pi.runtime.connect-timeout-ms:3000}") int connectTimeoutMs,
                                @Value("${pi.runtime.read-timeout-ms:120000}") int readTimeoutMs,
                                @Value("${pi.runtime.max-concurrent:32}") int maxConcurrent,
                                @Value("${pi.runtime.failure-threshold:5}") int failureThreshold,
                                @Value("${pi.runtime.circuit-cooldown-ms:30000}") int circuitCooldownMs,
                                @Value("${pi.runtime.requests-per-second:20}") int requestsPerSecond) {
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.retryPolicy = retryPolicy;
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
        this.bulkhead = new Semaphore(Math.max(1, maxConcurrent), true);
        this.failureThreshold = Math.max(1, failureThreshold);
        this.circuitCooldownNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1000, circuitCooldownMs));
        this.permitIntervalNanos = requestsPerSecond <= 0
                ? 0 : TimeUnit.SECONDS.toNanos(1) / Math.max(1, requestsPerSecond);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                                   Map<String, Object> inputs) {
        return retryPolicy.executeNonIdempotent("pi.sendBlockingMessage",
                () -> sendBlockingMessageOnce(query, user, conversationId, inputs));
    }

    public void sendStreamingMessage(String query, String user, String conversationId,
                                     Map<String, Object> inputs,
                                     Consumer<String> onData, Consumer<String> onError) {
        retryPolicy.executeNonIdempotent("pi.sendStreamingMessage", () -> {
            sendStreamingMessageOnce(query, user, conversationId, inputs, onData, onError);
            return null;
        });
    }

    private Map<String, String> sendBlockingMessageOnce(String query, String user, String conversationId,
                                                        Map<String, Object> inputs) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        Timer.Sample sample = metrics.startRequest();
        boolean acquired = false;
        try {
            acquireGuard();
            acquired = true;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/v1/customer-service/messages",
                    new HttpEntity<>(requestBody(query, user, normalizedConversationId, inputs, "blocking"),
                            buildHeaders(user, normalizedConversationId)),
                    String.class);
            Map<String, Object> body = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
            recordGatewayFacts(body);
            metrics.finishRequest(sample, RUNTIME, "blocking", "success");
            recordSuccess();
            return Map.of(
                    "answer", String.valueOf(body.getOrDefault("answer", "")),
                    "conversation_id", String.valueOf(body.getOrDefault("conversation_id", normalizedConversationId)));
        } catch (Exception error) {
            recordFailure();
            metrics.finishRequest(sample, RUNTIME, "blocking", "failure");
            log.warn("Pi runtime blocking call failed: type={}, message={}",
                    error.getClass().getSimpleName(), error.getMessage());
            throw new PiAgentRuntimeException("Pi Agent Runtime is unavailable", error);
        } finally {
            if (acquired) bulkhead.release();
        }
    }

    private void sendStreamingMessageOnce(String query, String user, String conversationId,
                                          Map<String, Object> inputs,
                                          Consumer<String> onData, Consumer<String> onError) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        Timer.Sample sample = metrics.startRequest();
        long startedAt = System.nanoTime();
        AtomicBoolean firstToken = new AtomicBoolean(true);
        boolean acquired = false;
        try {
            acquireGuard();
            acquired = true;
            restTemplate.execute(
                    baseUrl + "/api/v1/customer-service/messages/streaming",
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().addAll(buildHeaders(user, normalizedConversationId));
                        objectMapper.writeValue(request.getBody(),
                                requestBody(query, user, normalizedConversationId, inputs, "streaming"));
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data: ")) continue;
                                String rawData = line.substring(6);
                                recordGatewayFacts(objectMapper.readValue(rawData, new TypeReference<>() {}));
                                String normalized = normalizeStreamingData(rawData, normalizedConversationId);
                                if (normalized != null) {
                                    if (firstToken.compareAndSet(true, false)) {
                                        metrics.recordFirstToken(RUNTIME, Duration.ofNanos(System.nanoTime() - startedAt));
                                    }
                                    onData.accept(normalized);
                                }
                            }
                        }
                        return null;
                    });
            metrics.finishRequest(sample, RUNTIME, "streaming", "success");
            recordSuccess();
        } catch (Exception error) {
            recordFailure();
            metrics.finishRequest(sample, RUNTIME, "streaming", "failure");
            log.warn("Pi runtime streaming call failed: type={}, message={}",
                    error.getClass().getSimpleName(), error.getMessage());
            onError.accept("Pi Agent Runtime is unavailable");
        } finally {
            if (acquired) bulkhead.release();
        }
    }

    private String normalizeStreamingData(String rawData, String fallbackConversationId) {
        try {
            Map<String, Object> event = objectMapper.readValue(rawData, new TypeReference<>() {});
            String type = String.valueOf(event.getOrDefault("type", ""));
            String eventName = String.valueOf(event.getOrDefault("event", ""));
            if (!("token".equals(type) || "text-delta".equals(type)
                    || "message".equals(type) || "message".equals(eventName))) return null;
            Object content = event.containsKey("answer") ? event.get("answer")
                    : event.containsKey("text") ? event.get("text") : event.get("delta");
            if (!(content instanceof String text) || text.isEmpty()) return null;
            Map<String, Object> normalized = new HashMap<>();
            normalized.put("event", "message");
            normalized.put("answer", text);
            normalized.put("conversation_id", event.getOrDefault("conversation_id", fallbackConversationId));
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> requestBody(String query, String user, String conversationId,
                                            Map<String, Object> inputs, String responseMode) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("user", user);
        body.put("conversation_id", conversationId);
        body.put("inputs", inputs == null ? Map.of() : inputs);
        body.put("response_mode", responseMode);
        return body;
    }

    private HttpHeaders buildHeaders(String user, String conversationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (serviceToken != null && !serviceToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + serviceToken);
        }
        String capabilityToken = issueCapabilityToken(user, conversationId);
        if (capabilityToken != null) headers.set("X-Agent-Capability-Token", capabilityToken);
        return headers;
    }

    private String issueCapabilityToken(String user, String conversationId) {
        User principal = null;
        if (user != null && !user.isBlank()) {
            try { principal = userMapper.selectById(Long.valueOf(user)); }
            catch (NumberFormatException ignored) { principal = userMapper.findByUsername(user); }
        }
        if (principal == null || principal.getUsername() == null || principal.getUsername().isBlank()) {
            log.warn("Unable to issue Pi capability: user identity is unavailable");
            return null;
        }
        return jwtUtils.generateAgentCapabilityToken(principal.getUsername(), conversationId,
                CUSTOMER_SERVICE_SCOPES);
    }

    private String normalizeConversationId(String conversationId) {
        return conversationId == null || conversationId.isBlank()
                ? "pending-" + UUID.randomUUID() : conversationId;
    }

    private void acquireGuard() {
        long now = System.nanoTime();
        if (circuitOpenUntilNanos.get() > now) {
            throw new PiAgentRuntimeException("Pi Agent Runtime circuit is open", null);
        }
        if (permitIntervalNanos > 0) {
            while (true) {
                long previous = nextPermitNanos.get();
                long permitAt = Math.max(now, previous);
                if (nextPermitNanos.compareAndSet(previous, permitAt + permitIntervalNanos)) break;
                now = System.nanoTime();
            }
            long waitNanos = nextPermitNanos.get() - permitIntervalNanos - System.nanoTime();
            if (waitNanos > 0) {
                try { TimeUnit.NANOSECONDS.sleep(waitNanos); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new PiAgentRuntimeException("Interrupted while rate limiting Pi runtime", interrupted);
                }
            }
        }
        try {
            if (!bulkhead.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                throw new PiAgentRuntimeException("Pi Agent Runtime bulkhead is full", null);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PiAgentRuntimeException("Interrupted while acquiring Pi runtime bulkhead", interrupted);
        }
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpenUntilNanos.set(0);
    }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            circuitOpenUntilNanos.set(System.nanoTime() + circuitCooldownNanos);
        }
    }

    @SuppressWarnings("unchecked")
    private void recordGatewayFacts(Map<String, Object> body) {
        Object usage = body.get("usage");
        if (usage instanceof Map<?, ?> usageMap) {
            recordToken(usageMap, "inputTokens", "input_tokens", "input");
            recordToken(usageMap, "outputTokens", "output_tokens", "output");
        }
        Object calls = body.get("tool_calls");
        if (calls instanceof Iterable<?> iterable) {
            for (Object call : iterable) {
                if (call instanceof Map<?, ?> callMap) {
                    metrics.recordToolCall(String.valueOf(callMap.containsKey("name") ? callMap.get("name") : "unknown"),
                            String.valueOf(callMap.containsKey("outcome") ? callMap.get("outcome") : "unknown"));
                }
            }
        }
        Object handoff = body.get("handoff");
        if (handoff != null) metrics.recordHumanHandoff(String.valueOf(handoff));
    }

    private void recordToken(Map<?, ?> usage, String camelKey, String snakeKey, String tokenType) {
        Object value = usage.containsKey(camelKey) ? usage.get(camelKey) : usage.get(snakeKey);
        if (value instanceof Number number) metrics.recordTokenUsage(RUNTIME, model, tokenType, number.longValue());
    }

    public static class PiAgentRuntimeException extends RuntimeException {
        public PiAgentRuntimeException(String message, Throwable cause) { super(message, cause); }
    }
}
