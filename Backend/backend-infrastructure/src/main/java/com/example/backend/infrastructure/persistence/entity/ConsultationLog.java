package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConsultationLog {
    private Long id;
    private String sessionId;
    private Long userId;
    private Long agentId;
    private String userInput;
    private String aiResponse;
    private String difyConversationId;
    private String intent;
    private String channel;
    private Integer duration;
    private Integer satisfaction;
    private LocalDateTime createTime;
}
