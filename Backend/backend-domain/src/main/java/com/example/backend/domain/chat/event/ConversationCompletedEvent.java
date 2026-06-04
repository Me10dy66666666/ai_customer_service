package com.example.backend.domain.chat.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class ConversationCompletedEvent extends DomainEvent {
    private final String sessionId;
    private final Long userId;
    private final String aiResponse;
    private final String intent;
    private final String difyConversationId;

    public ConversationCompletedEvent(String sessionId, Long userId, String aiResponse,
                                       String intent, String difyConversationId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.aiResponse = aiResponse;
        this.intent = intent;
        this.difyConversationId = difyConversationId;
    }
}
