package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WorkOrderTransferLog {
    private Long id;
    private Long workOrderId;
    private Long fromHandlerId;
    private Long toHandlerId;
    private String transferReason;
    private LocalDateTime createTime;
}
