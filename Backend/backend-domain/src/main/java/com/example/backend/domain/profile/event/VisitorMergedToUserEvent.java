package com.example.backend.domain.profile.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class VisitorMergedToUserEvent extends DomainEvent {
    private final String sessionId;
    private final Long userId;

    public VisitorMergedToUserEvent(String sessionId, Long userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }
}
