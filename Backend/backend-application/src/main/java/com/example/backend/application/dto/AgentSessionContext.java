package com.example.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentSessionContext {
    private String sessionId;
    private Long userId;
    private String intent;
    private String status;
    private Long position;
    private Long estimatedWait;
    private String priority;
    private String tags;
    private String summary;
    private List<Map<String, String>> aiMessages;
}
