package com.example.backend.domain.auth.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class UserRegisteredEvent extends DomainEvent {
    private final Long userId;
    private final String username;
    private final String sessionId;

    public UserRegisteredEvent(Long userId, String username, String sessionId) {
        this.userId = userId;
        this.username = username;
        this.sessionId = sessionId;
    }
}
