package com.example.backend.domain.auth.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

import java.util.Set;

@Getter
public class UserLoggedInEvent extends DomainEvent {
    private final Long userId;
    private final String username;
    private final Set<String> roles;
    private final String sessionId;

    public UserLoggedInEvent(Long userId, String username, Set<String> roles, String sessionId) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
        this.sessionId = sessionId;
    }
}
