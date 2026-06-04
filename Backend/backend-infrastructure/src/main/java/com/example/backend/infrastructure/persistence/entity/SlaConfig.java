package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SlaConfig {
    private Long id;
    private String bizTag;
    private String priority;
    private Integer responseMinutes;
    private Integer resolutionMinutes;
    private Integer escalationMinutes;
    private BigDecimal emergencyThreshold;
    private Long calendarId;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
