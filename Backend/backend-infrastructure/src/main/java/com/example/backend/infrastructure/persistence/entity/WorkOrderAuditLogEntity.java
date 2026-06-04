package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WorkOrderAuditLogEntity {
    private Long id;
    private Long workOrderId;
    private String eventType;
    private String actorType;
    private Long actorId;
    private String action;
    private String detail;
    private Boolean internalOnly;
    private LocalDateTime createTime;
}