package com.example.backend.infrastructure.tsagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * TS Agent HTTP 客户端
 *
 * 调用 TypeScript 重构的 Agent 服务端。
 * 对齐 Backend 原 DifyClient 的调用模式，使 DifyAdapter / TsAgentAdapter 可互换。
 */
@Slf4j
@Component
public class TsAgentClient {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${ts-agent.base-url:http://localhost:3001}")
    private String baseUrl;

    @Value("${ts-agent.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public TsAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /* ──── 对话 API（对齐 AiChatPort）──── */

    /**
     * 阻塞式消息 — 对齐 DifyClient.sendMessage()
     *
     * 请求: POST {baseUrl}/api/v1/chat-messages
     * 响应: { "answer": "...", "conversation_id": "..." }
     */
    public Map<String, String> sendMessage(String query, String user, String conversationId,
                                           Map<String, Object> inputs) {
        String url = baseUrl + "/api/v1/chat-messages";
        HttpHeaders headers = buildHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("user", user);
        body.put("conversation_id", conversationId != null ? conversationId : "");
        body.put("inputs", inputs);
        body.put("response_mode", "blocking");

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            Map<String, Object> responseBody = objectMapper.readValue(
                    response.getBody(), new TypeReference<Map<String, Object>>() {});

            Map<String, String> result = new HashMap<>();
            result.put("answer", (String) responseBody.getOrDefault("answer", ""));
            result.put("conversation_id", (String) responseBody.getOrDefault("conversation_id", ""));
            return result;
        } catch (Exception e) {
            log.error("TsAgent sendMessage failed: {}", e.getMessage());
            Map<String, String> fallback = new HashMap<>();
            fallback.put("answer", "抱歉，AI 服务暂时不可用，请稍后重试。");
            fallback.put("conversation_id", conversationId != null ? conversationId : "");
            return fallback;
        }
    }

    /**
     * 流式消息 — 对齐 DifyClient.sendStreamingMessage()
     *
     * 请求: POST {baseUrl}/api/v1/chat-messages/streaming
     * 响应: SSE 流
     */
    public void sendStreamingMessage(String query, String user, String conversationId,
                                      Map<String, Object> inputs,
                                      Consumer<String> onData, Consumer<String> onError) {
        String url = baseUrl + "/api/v1/chat-messages/streaming";
        HttpHeaders headers = buildHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("user", user);
        body.put("conversation_id", conversationId != null ? conversationId : "");
        body.put("inputs", inputs);
        body.put("response_mode", "streaming");

        restTemplate.execute(url, HttpMethod.POST, request -> {
            request.getHeaders().addAll(headers);
            this.objectMapper.writeValue(request.getBody(), body);
        }, response -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        onData.accept(line.substring(6));
                    }
                }
            } catch (IOException e) {
                onError.accept(e.getMessage());
            }
            return null;
        });
    }

    /* ──── 知识库 API（对齐 KnowledgeBasePort）──── */

    public String uploadFile(File file, String filename, String datasetId) {
        String url = baseUrl + "/api/v1/knowledge/datasets/" + datasetId + "/documents";
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file) {
            @Override public String getFilename() { return filename; }
        });

        try {
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            Map<String, Object> responseBody = objectMapper.readValue(
                    response.getBody(), new TypeReference<Map<String, Object>>() {});
            if (responseBody.containsKey("document")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> document = (Map<String, Object>) responseBody.get("document");
                return (String) document.get("id");
            }
            throw new RuntimeException("Failed to upload file: " + responseBody);
        } catch (Exception e) {
            log.error("TsAgent uploadFile failed: {}", e.getMessage());
            throw new RuntimeException("Upload failed", e);
        }
    }

    public void deleteDocument(String datasetId, String documentId) {
        String url = baseUrl + "/api/v1/knowledge/datasets/" + datasetId + "/documents/" + documentId;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(buildHeaders()), Void.class);
        } catch (Exception e) {
            log.error("TsAgent deleteDocument failed: {}", e.getMessage());
            throw new RuntimeException("Delete failed", e);
        }
    }

    public Map<String, Object> getDataset(String datasetId) {
        String url = baseUrl + "/api/v1/knowledge/datasets/" + datasetId;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class);
            return objectMapper.readValue(response.getBody(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("TsAgent getDataset failed: {}", e.getMessage());
            return Map.of();
        }
    }

    public void updateDocumentStatus(String datasetId, String documentId, boolean enable) {
        String url = baseUrl + "/api/v1/knowledge/datasets/" + datasetId
                + "/documents/" + documentId + "/status";
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("enabled", enable);
        try {
            restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body, headers), Void.class);
        } catch (Exception e) {
            log.error("TsAgent updateDocumentStatus failed: {}", e.getMessage());
            throw new RuntimeException("Status update failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listDocuments(String datasetId, int page, int limit) {
        String url = baseUrl + "/api/v1/knowledge/datasets/" + datasetId
                + "/documents?page=" + page + "&limit=" + limit;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class);
            Map<String, Object> responseBody = objectMapper.readValue(
                    response.getBody(), new TypeReference<Map<String, Object>>() {});
            Object data = responseBody.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
            return List.of();
        } catch (Exception e) {
            log.error("TsAgent listDocuments failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAllDocuments(String datasetId) {
        String url = baseUrl + "/api/v1/knowledge/datasets/" + datasetId + "/documents/all";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class);
            Object data = objectMapper.readValue(response.getBody(), Object.class);
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
            return List.of();
        } catch (Exception e) {
            log.error("TsAgent listAllDocuments failed: {}", e.getMessage());
            return List.of();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + apiKey);
        }
        return headers;
    }
}
