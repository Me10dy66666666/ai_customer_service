package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SlaPauseLog {
    private Long id;
    private Long workOrderId;
    private String pauseReason;
    private String resumeReason;
    private Long operatorId;
    private LocalDateTime pauseTime;
    private LocalDateTime resumeTime;
    private Long pausedEffectiveSeconds;
    private LocalDateTime originalResponseDeadline;
    private LocalDateTime originalSlaDeadline;
    private LocalDateTime resumeResponseDeadline;
    private LocalDateTime resumeSlaDeadline;
    private LocalDateTime createdAt;
}
