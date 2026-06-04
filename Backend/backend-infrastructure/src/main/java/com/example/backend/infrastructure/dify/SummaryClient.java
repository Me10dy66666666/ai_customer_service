package com.example.backend.infrastructure.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class SummaryClient {

    @Value("${dify.intervention.api-key}") private String apiKey;
    @Value("${dify.base-url}") private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public SummaryClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SummaryResult callTransferWorkflow(Map<String, Object> inputs, String workflowEndpoint) {
        return callWorkflow(workflowEndpoint, inputs, SummaryResult.class);
    }

    public WorkOrderAnalysisResult callWorkorderWorkflow(Map<String, Object> inputs, String workflowEndpoint, String apiKey) {
        return callWorkflow(workflowEndpoint, inputs, WorkOrderAnalysisResult.class, apiKey);
    }

    private <T> T callWorkflow(String endpoint, Map<String, Object> inputs, Class<T> resultType) {
        return callWorkflow(endpoint, inputs, resultType, this.apiKey);
    }

    private <T> T callWorkflow(String endpoint, Map<String, Object> inputs, Class<T> resultType, String requestApiKey) {
        if (requestApiKey == null || requestApiKey.isEmpty() || baseUrl == null || baseUrl.isEmpty()) {
            log.warn("SummaryClient not configured: apiKey or baseUrl is empty");
            return null;
        }
        String url = baseUrl + endpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(requestApiKey);

        Map<String, Object> body = Map.of(
                "inputs", inputs,
                "response_mode", "blocking",
                "user", "system"
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getBody() == null) {
                log.warn("Dify workflow returned empty body for {}", endpoint);
                return null;
            }
            return parseWorkflowResponse(response.getBody(), resultType);
        } catch (Exception e) {
            log.error("Dify workflow call failed for {}: {}", endpoint, e.getMessage());
            return null;
        }
    }

    private <T> T parseWorkflowResponse(String responseBody, Class<T> resultType) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode outputsNode = root.has("data")
                    ? root.get("data").path("outputs")
                    : null;

            if (outputsNode != null && !outputsNode.isMissingNode() && outputsNode.isObject()) {
                return objectMapper.treeToValue(outputsNode, resultType);
            }

            if (isRecognizableResponse(root)) {
                return objectMapper.treeToValue(root, resultType);
            }

            log.warn("Dify workflow response missing expected fields. Response: {}",
                    responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody);
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Dify workflow response: {}", e.getMessage());
            return null;
        }
    }

    private boolean isRecognizableResponse(JsonNode root) {
        return root.has("priority") || root.has("summary") || root.has("tags");
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class SummaryResult {
        private String priority;
        private String summary;
        private String tags;

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
    }
}
