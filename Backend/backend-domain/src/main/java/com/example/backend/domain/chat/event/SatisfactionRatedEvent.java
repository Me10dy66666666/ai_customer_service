package com.example.backend.domain.chat.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class SatisfactionRatedEvent extends DomainEvent {
    private final String sessionId;
    private final Long userId;
    private final Integer satisfaction;

    public SatisfactionRatedEvent(String sessionId, Long userId, Integer satisfaction) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.satisfaction = satisfaction;
    }
}
