package com.example.backend.domain.workorder.model;

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

    public static WorkOrderTransferLog create(Long workOrderId, Long fromHandlerId,
                                               Long toHandlerId, String reason) {
        WorkOrderTransferLog log = new WorkOrderTransferLog();
        log.setWorkOrderId(workOrderId);
        log.setFromHandlerId(fromHandlerId);
        log.setToHandlerId(toHandlerId);
        log.setTransferReason(reason);
        log.setCreateTime(LocalDateTime.now());
        return log;
    }
}
