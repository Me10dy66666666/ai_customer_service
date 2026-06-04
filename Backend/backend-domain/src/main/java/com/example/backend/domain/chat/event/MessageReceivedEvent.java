package com.example.backend.domain.chat.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class MessageReceivedEvent extends DomainEvent {
    private final String sessionId;
    private final Long userId;
    private final String content;

    public MessageReceivedEvent(String sessionId, Long userId, String content) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.content = content;
    }
}
