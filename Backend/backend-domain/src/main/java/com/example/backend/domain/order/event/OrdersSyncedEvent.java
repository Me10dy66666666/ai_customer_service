package com.example.backend.domain.order.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class OrdersSyncedEvent extends DomainEvent {
    private final Long userId;
    private final int orderCount;

    public OrdersSyncedEvent(Long userId, int orderCount) {
        this.userId = userId;
        this.orderCount = orderCount;
    }
}
