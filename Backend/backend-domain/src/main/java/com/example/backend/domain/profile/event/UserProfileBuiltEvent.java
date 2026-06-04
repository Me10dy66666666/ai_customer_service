package com.example.backend.domain.profile.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class UserProfileBuiltEvent extends DomainEvent {
    private final Long userId;
    private final String userType;
    private final String tags;

    public UserProfileBuiltEvent(Long userId, String userType, String tags) {
        this.userId = userId;
        this.userType = userType;
        this.tags = tags;
    }
}
