package com.example.backend.domain.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String EVENT_UPLOAD = "DIFY_UPLOAD";
    public static final String EVENT_UPDATE = "DIFY_UPDATE";
    public static final String EVENT_DELETE = "DIFY_DELETE";

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

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public void markProcessing() {
        this.status = STATUS_PROCESSING;
    }

    public void markCompleted() {
        this.status = STATUS_COMPLETED;
    }

    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.lastError = errorMessage;
    }

    public void incrementRetry() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        this.retryCount++;
    }

    public boolean exceedsMaxRetry() {
        int max = this.maxRetry != null ? this.maxRetry : 5;
        int count = this.retryCount != null ? this.retryCount : 0;
        return count >= max;
    }
}
