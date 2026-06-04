package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeOutboxEntity {
    private Long id;
    private Long documentId;
    private String eventType;
    private String payload;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime nextRetryAt;
}
