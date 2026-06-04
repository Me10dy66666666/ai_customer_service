package com.example.backend.domain.workorder.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class WorkOrderCreatedEvent extends DomainEvent {
    private final Long workOrderId;
    private final Long userId;
    private final String type;
    private final String priority;

    public WorkOrderCreatedEvent(Long workOrderId, Long userId, String type, String priority) {
        this.workOrderId = workOrderId;
        this.userId = userId;
        this.type = type;
        this.priority = priority;
    }
}
