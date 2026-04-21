package com.example.backend.client;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

// 新增 HttpClient 5 相关导入
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Consumer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

@Component
public class DifyClient {

    @Value("${dify.api.base-url}")
    private String baseUrl;

    @Value("${dify.api.key}")
    private String datasetApiKey;

    @Value("${dify.chat.key}")
    private String chatApiKey;

    @Value("${dify.chat.timeout:10000}")
    private int apiTimeout;

    @Value("${dify.chat.retry:3}")
    private int apiRetry;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 1. 配置超时策略
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(apiTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(apiTimeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(apiTimeout))
                .build();

        // 2. 配置重试策略 (重试次数, 重试间隔)
        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(
                apiRetry, 
                TimeValue.of(1, TimeUnit.SECONDS)
        );

        // 3. 构建 HttpClient
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(retryStrategy)
                .build();

        // 4. 创建 Factory (HttpComponentsClientHttpRequestFactory 默认支持 PATCH)
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        // 5. 初始化 RestTemplate
        this.restTemplate = new RestTemplate(factory);
    }

    public String uploadFile(File file, String filename, String datasetId) {
        String url = baseUrl + "/datasets/" + datasetId + "/document/create_by_file";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + datasetApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // Override getFilename to send the desired filename to Dify
        body.add("file", new FileSystemResource(file) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        // Construct the 'data' JSON part
        Map<String, Object> processRule = new HashMap<>();
        processRule.put("mode", "automatic");

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("indexing_technique", "high_quality");
        dataMap.put("process_rule", processRule);

        try {
            String dataJson = objectMapper.writeValueAsString(dataMap);
            body.add("data", dataJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize data JSON", e);
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("document")) {
                    Map<String, Object> document = (Map<String, Object>) responseBody.get("document");
                    return (String) document.get("id");
                }
            }
            throw new RuntimeException("Failed to upload file to Dify: " + response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error calling Dify API: " + e.getMessage(), e);
        }
    }

    public void deleteDocument(String datasetId, String documentId) {
        String url = baseUrl + "/datasets/" + datasetId + "/documents/" + documentId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + datasetApiKey);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error deleting document from Dify: " + e.getMessage(), e);
        }
    }
    
    public Map<String, Object> getDataset(String datasetId) {
        String url = baseUrl + "/datasets/" + datasetId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + datasetApiKey);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("Failed to get dataset info from Dify: " + response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error getting dataset info from Dify: " + e.getMessage(), e);
        }
    }
    
    public void updateDocumentStatus(String datasetId, String documentId, boolean enable) {
        String action = enable ? "enable" : "disable";
        String url = baseUrl + "/datasets/" + datasetId + "/documents/status/" + action;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + datasetApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> body = new HashMap<>();
        body.put("document_ids", List.of(documentId));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // 直接调用即可，init() 中配置的 factory 已经支持 PATCH
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PATCH, requestEntity, Map.class);
            
            if (response.getStatusCode() != HttpStatus.OK) {
                 throw new RuntimeException("Failed to update status in Dify: " + response.getBody());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error updating document status in Dify: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listDocuments(String datasetId, int page, int limit) {
        String url = baseUrl + "/datasets/" + datasetId + "/documents?page=" + page + "&limit=" + limit;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + datasetApiKey);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.containsKey("data")) {
                    return (List<Map<String, Object>>) body.get("data");
                }
            }
            throw new RuntimeException("Failed to list documents from Dify: " + response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error listing documents from Dify: " + e.getMessage(), e);
        }
    }

    public void sendStreamingMessage(String query, String user, String conversationId, Map<String, Object> inputs, Consumer<String> onData, Consumer<String> onError) {
        String url = baseUrl + "/chat-messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + chatApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", inputs);
        body.put("query", query);
        body.put("response_mode", "streaming");
        body.put("conversation_id", conversationId != null ? conversationId : "");
        body.put("user", user);

        // 使用 RequestCallback 和 ResponseExtractor 实现流式读取
        restTemplate.execute(url, HttpMethod.POST, request -> {
            request.getHeaders().addAll(headers);
            new ObjectMapper().writeValue(request.getBody(), body);
        }, response -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        onData.accept(data);
                    }
                }
            } catch (IOException e) {
                onError.accept(e.getMessage());
            }
            return null;
        });
    }

    // Keep the blocking method for compatibility or specific use cases if needed
    public Map<String, String> sendMessage(String query, String user, String conversationId, Map<String, Object> inputs) {
        String url = baseUrl + "/chat-messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + chatApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", inputs);
        body.put("query", query);
        body.put("response_mode", "blocking");
        body.put("conversation_id", conversationId != null ? conversationId : "");
        body.put("user", user);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("answer")) {
                    Map<String, String> result = new HashMap<>();
                    result.put("answer", (String) responseBody.get("answer"));
                    result.put("conversation_id", (String) responseBody.get("conversation_id"));
                    return result;
                }
            }
            throw new RuntimeException("Failed to send message to Dify: " + response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error calling Dify Chat API: " + e.getMessage(), e);
        }
    }
}
