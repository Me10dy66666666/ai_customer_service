package com.example.backend.infrastructure.dify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class DifyClient {
    private static final String API_BASE_PATH = "/datasets/";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KEY_CONVERSATION_ID = "conversation_id";
    private static final String KEY_ANSWER = "answer";

    @Value("${dify.base-url}") private String baseUrl;
    @Value("${dify.knowledge.key}") private String datasetApiKey;
    @Value("${dify.chat.key}") private String chatApiKey;
    @Value("${dify.chat.timeout:10000}") private int apiTimeout;
    @Value("${dify.chat.retry:3}") private int apiRetry;

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF =
            new TypeReference<Map<String, Object>>() {};

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DifyClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        ConnectionConfig connConfig = ConnectionConfig.custom()
                .setConnectTimeout(apiTimeout, TimeUnit.MILLISECONDS)
                .build();

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connConfig)
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(apiTimeout, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(apiTimeout, TimeUnit.MILLISECONDS)
                .build();

        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(
                apiRetry, TimeValue.ofSeconds(1));

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(retryStrategy)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restTemplate = new RestTemplate(factory);
    }

    private <T> T parseResponse(ResponseEntity<String> response, TypeReference<T> typeRef) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new DifyApiException("Dify API error: " + response.getStatusCode());
        }
        try {
            return objectMapper.readValue(response.getBody(), typeRef);
        } catch (IOException e) {
            throw new DifyApiException("Failed to parse Dify response", e);
        }
    }

    public String uploadFile(File file, String filename, String datasetId) {
        String url = baseUrl + API_BASE_PATH + datasetId + "/document/create_by_file";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + datasetApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file) {
            @Override public String getFilename() { return filename; }
        });

        Map<String, Object> processRule = new HashMap<>();
        processRule.put("mode", "automatic");
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("indexing_technique", "high_quality");
        dataMap.put("process_rule", processRule);

        try { body.add("data", objectMapper.writeValueAsString(dataMap)); }
        catch (Exception e) { throw new DifyApiException("Failed to serialize data JSON", e); }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            Map<String, Object> responseBody = parseResponse(response, MAP_TYPE_REF);
            if (responseBody.containsKey("document")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> document = (Map<String, Object>) responseBody.get("document");
                return (String) document.get("id");
            }
            throw new DifyApiException("Failed to upload file to Dify: " + responseBody);
        } catch (DifyApiException e) { throw e; }
        catch (Exception e) { throw new DifyApiException("Error calling Dify API: " + e.getMessage(), e); }
    }

    public void deleteDocument(String datasetId, String documentId) {
        String url = baseUrl + API_BASE_PATH + datasetId + "/documents/" + documentId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + datasetApiKey);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        try { restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class); }
        catch (Exception e) { throw new DifyApiException("Error deleting document from Dify: " + e.getMessage(), e); }
    }

    public Map<String, Object> getDataset(String datasetId) {
        String url = baseUrl + API_BASE_PATH + datasetId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + datasetApiKey);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            return parseResponse(response, MAP_TYPE_REF);
        } catch (DifyApiException e) { throw e; }
        catch (Exception e) { throw new DifyApiException("Error getting dataset info from Dify: " + e.getMessage(), e); }
    }

    public void updateDocumentStatus(String datasetId, String documentId, boolean enable) {
        String action = enable ? "enable" : "disable";
        String url = baseUrl + API_BASE_PATH + datasetId + "/documents/status/" + action;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + datasetApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("document_ids", List.of(documentId));
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful())
                throw new DifyApiException("Failed to update status in Dify: " + response.getBody());
        } catch (DifyApiException e) { throw e; }
        catch (Exception e) { throw new DifyApiException("Error updating document status in Dify: " + e.getMessage(), e); }
    }

    public List<Map<String, Object>> listDocuments(String datasetId, int page, int limit) {
        Map<String, Object> responseBody = listDocumentsPage(datasetId, page, limit);
        if (responseBody.containsKey("data")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            return data;
        }
        return List.of();
    }

    public Map<String, Object> listDocumentsPage(String datasetId, int page, int limit) {
        String url = baseUrl + API_BASE_PATH + datasetId + "/documents?page=" + page + "&limit=" + limit;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + datasetApiKey);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            return parseResponse(response, MAP_TYPE_REF);
        } catch (DifyApiException e) { throw e; }
        catch (Exception e) { throw new DifyApiException("Error listing documents from Dify: " + e.getMessage(), e); }
    }

    public List<Map<String, Object>> listAllDocuments(String datasetId) {
        int page = 1;
        int limit = 100;
        boolean hasMore = true;
        List<Map<String, Object>> documents = new ArrayList<>();
        while (hasMore) {
            Map<String, Object> responseBody = listDocumentsPage(datasetId, page, limit);
            Object data = responseBody.get("data");
            if (data instanceof List<?> list)
                for (Object item : list)
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> docMap = (Map<String, Object>) item;
                        documents.add(docMap);
                    }
            hasMore = Boolean.TRUE.equals(responseBody.get("has_more"));
            page++;
        }
        return documents;
    }

    public void sendStreamingMessage(String query, String user, String conversationId,
                                      Map<String, Object> inputs, Consumer<String> onData, Consumer<String> onError) {
        String url = baseUrl + "/chat-messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + chatApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", inputs);
        body.put("query", query);
        body.put("response_mode", "streaming");
        body.put(KEY_CONVERSATION_ID, conversationId != null ? conversationId : "");
        body.put("user", user);

        restTemplate.execute(url, HttpMethod.POST, request -> {
            request.getHeaders().addAll(headers);
            this.objectMapper.writeValue(request.getBody(), body);
        }, response -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null)
                    if (line.startsWith("data: ")) onData.accept(line.substring(6));
            } catch (IOException e) { onError.accept(e.getMessage()); }
            return null;
        });
    }

    public Map<String, String> sendMessage(String query, String user, String conversationId, Map<String, Object> inputs) {
        String url = baseUrl + "/chat-messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + chatApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", inputs);
        body.put("query", query);
        body.put("response_mode", "blocking");
        body.put(KEY_CONVERSATION_ID, conversationId != null ? conversationId : "");
        body.put("user", user);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            Map<String, Object> responseBody = parseResponse(response, MAP_TYPE_REF);
            if (responseBody.containsKey(KEY_ANSWER)) {
                Map<String, String> result = new HashMap<>();
                result.put(KEY_ANSWER, (String) responseBody.get(KEY_ANSWER));
                result.put(KEY_CONVERSATION_ID, (String) responseBody.get(KEY_CONVERSATION_ID));
                return result;
            }
            throw new DifyApiException("Failed to send message to Dify: " + responseBody);
        } catch (DifyApiException e) { throw e; }
        catch (Exception e) { throw new DifyApiException("Error calling Dify Chat API: " + e.getMessage(), e); }
    }
}
