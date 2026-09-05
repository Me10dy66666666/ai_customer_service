package com.example.backend.infrastructure.dsh;

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
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Timer;

/**
 * HTTP boundary for the project-owned BFF in front of the headless DeepSeek Harness runtime.
 * This client deliberately contains no knowledge-management API: DSH is the customer-service
 * Agent runtime, while Java remains the business source of truth.
 */
@Slf4j
@Component
public class DshGatewayClient {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_AGENT_CAPABILITY = "X-Agent-Capability-Token";
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

    @Value("${dsh.gateway.base-url:http://localhost:3001}")
    private String baseUrl;

    @Value("${dsh.gateway.service-token:}")
    private String serviceToken;

    @Value("${dsh.gateway.model:unknown}")
    private String model;

    public DshGatewayClient(ObjectMapper objectMapper,
                            AgentRuntimeMetrics metrics,
                            ExternalCallRetryPolicy retryPolicy,
                            JwtUtils jwtUtils,
                            UserMapper userMapper,
                            @Value("${dsh.gateway.connect-timeout-ms:3000}") int connectTimeoutMs,
                            @Value("${dsh.gateway.read-timeout-ms:120000}") int readTimeoutMs,
                            @Value("${dsh.gateway.max-concurrent:32}") int maxConcurrent,
                            @Value("${dsh.gateway.failure-threshold:5}") int failureThreshold,
                            @Value("${dsh.gateway.circuit-cooldown-ms:30000}") int circuitCooldownMs,
                            @Value("${dsh.gateway.requests-per-second:20}") int requestsPerSecond) {
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

    public Map<String, String> sendMessage(String query, String user, String conversationId,
                                           Map<String, Object> inputs) {
        return retryPolicy.executeNonIdempotent("dsh.sendMessage",
                () -> sendMessageOnce(query, user, conversationId, inputs));
    }

    private Map<String, String> sendMessageOnce(String query, String user, String conversationId,
                                                Map<String, Object> inputs) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        Timer.Sample sample = metrics.startRequest();
        boolean guardAcquired = false;
        try {
            acquireGuard();
            guardAcquired = true;
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/v1/customer-service/messages",
                    new HttpEntity<>(requestBody(query, user, normalizedConversationId, inputs, "blocking"),
                            buildHeaders(user, normalizedConversationId)),
                    String.class);
            Map<String, Object> responseBody = objectMapper.readValue(
                    response.getBody(), new TypeReference<>() {});
            Map<String, String> result = Map.of(
                    "answer", String.valueOf(responseBody.getOrDefault("answer", "")),
                    "conversation_id", String.valueOf(
                            responseBody.getOrDefault("conversation_id", normalizedConversationId)));
            recordGatewayFacts(responseBody);
            metrics.finishRequest(sample, "dsh", "blocking", "success");
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            metrics.finishRequest(sample, "dsh", "blocking", "failure");
            log.warn("DSH gateway blocking call failed: type={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            throw new DshGatewayException("DSH gateway is unavailable", e);
        } finally {
            if (guardAcquired) bulkhead.release();
        }
    }

    public void sendStreamingMessage(String query, String user, String conversationId,
                                     Map<String, Object> inputs,
                                     Consumer<String> onData, Consumer<String> onError) {
        retryPolicy.executeNonIdempotent("dsh.sendStreamingMessage", () -> {
            sendStreamingMessageOnce(query, user, conversationId, inputs, onData, onError);
            return null;
        });
    }

    private void sendStreamingMessageOnce(String query, String user, String conversationId,
                                          Map<String, Object> inputs,
                                          Consumer<String> onData, Consumer<String> onError) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        Timer.Sample sample = metrics.startRequest();
        long startedAt = System.nanoTime();
        AtomicBoolean firstToken = new AtomicBoolean(true);
        boolean guardAcquired = false;
        try {
            acquireGuard();
            guardAcquired = true;
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
                                if (line.startsWith("data: ")) {
                                    String rawData = line.substring(6);
                                    boolean tokenFrame = recordStreamingEvent(rawData);
                                    if (tokenFrame && firstToken.compareAndSet(true, false)) {
                                        metrics.recordFirstToken("dsh",
                                                Duration.ofNanos(System.nanoTime() - startedAt));
                                    }
                                    String normalized = normalizeStreamingData(rawData, normalizedConversationId);
                                    if (normalized != null) onData.accept(normalized);
                                }
                            }
                        }
                        return null;
                    });
            metrics.finishRequest(sample, "dsh", "streaming", "success");
            recordSuccess();
        } catch (Exception e) {
            recordFailure();
            metrics.finishRequest(sample, "dsh", "streaming", "failure");
            log.warn("DSH gateway streaming call failed: type={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            onError.accept("DSH gateway is unavailable");
        } finally {
            if (guardAcquired) bulkhead.release();
        }
    }

    private void acquireGuard() {
        long now = System.nanoTime();
        long openUntil = circuitOpenUntilNanos.get();
        if (openUntil > now) {
            throw new DshGatewayException("DSH gateway circuit is open", null);
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
                    throw new DshGatewayException("Interrupted while rate limiting DSH request", interrupted);
                }
            }
        }
        try {
            if (!bulkhead.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                throw new DshGatewayException("DSH gateway bulkhead is full", null);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new DshGatewayException("Interrupted while acquiring DSH bulkhead", interrupted);
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

    private boolean recordStreamingEvent(String rawData) {
        try {
            Map<String, Object> event = objectMapper.readValue(rawData, new TypeReference<>() {});
            recordGatewayFacts(event);
            return "token".equals(event.get("type"))
                    || "text-delta".equals(event.get("type"))
                    || event.containsKey("delta");
        } catch (Exception ignored) {
            // Token frames are deliberately opaque to the metrics boundary; malformed
            // provider presentation must not break the user's SSE stream.
            return false;
        }
    }

    /** Keep the existing Java application port stable while DSH owns its own SSE vocabulary. */
    private String normalizeStreamingData(String rawData, String fallbackConversationId) {
        try {
            Map<String, Object> event = objectMapper.readValue(rawData, new TypeReference<>() {});
            String type = String.valueOf(event.getOrDefault("type", ""));
            if (!("token".equals(type) || "text-delta".equals(type) || "message".equals(type))) {
                return null;
            }
            Object content = event.containsKey("text") ? event.get("text")
                    : event.containsKey("delta") ? event.get("delta") : event.get("answer");
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
                    String name = String.valueOf(callMap.containsKey("name") ? callMap.get("name") : "unknown");
                    String outcome = String.valueOf(callMap.containsKey("outcome") ? callMap.get("outcome") : "unknown");
                    metrics.recordToolCall(name, outcome);
                }
            }
        }
        Object toolEvent = body.get("type");
        if ("tool".equals(toolEvent) && !"started".equals(body.get("outcome"))) {
            metrics.recordToolCall(String.valueOf(body.getOrDefault("name", "unknown")),
                    String.valueOf(body.getOrDefault("outcome", "unknown")));
        }
        Object handoff = body.get("handoff");
        if (handoff != null) metrics.recordHumanHandoff(String.valueOf(handoff));
    }

    private void recordToken(Map<?, ?> usage, String camelKey, String snakeKey, String tokenType) {
        Object value = usage.containsKey(camelKey) ? usage.get(camelKey) : usage.get(snakeKey);
        if (value instanceof Number number) {
            metrics.recordTokenUsage("dsh", model, tokenType, number.longValue());
        }
    }

    private HttpHeaders buildHeaders(String user, String conversationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (serviceToken != null && !serviceToken.isBlank()) {
            headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + serviceToken);
        }
        String capabilityToken = issueCapabilityToken(user, conversationId);
        if (capabilityToken != null) {
            // This header is consumed only by the trusted DSH BFF and is never
            // copied into the model message or prompt context.
            headers.set(HEADER_AGENT_CAPABILITY, capabilityToken);
        }
        return headers;
    }

    private String normalizeConversationId(String conversationId) {
        return conversationId == null || conversationId.isBlank()
                ? "pending-" + UUID.randomUUID() : conversationId;
    }

    private String issueCapabilityToken(String user, String conversationId) {
        User principal = null;
        if (user != null && !user.isBlank()) {
            try {
                principal = userMapper.selectById(Long.valueOf(user));
            } catch (NumberFormatException ignored) {
                principal = userMapper.findByUsername(user);
            }
        }
        if (principal == null || principal.getUsername() == null || principal.getUsername().isBlank()) {
            log.warn("Unable to issue DSH capability: user identity is unavailable");
            return null;
        }
        return jwtUtils.generateAgentCapabilityToken(
                principal.getUsername(), conversationId, CUSTOMER_SERVICE_SCOPES);
    }

    public static class DshGatewayException extends RuntimeException {
        public DshGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
