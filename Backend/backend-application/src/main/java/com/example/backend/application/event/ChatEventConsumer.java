package com.example.backend.application.event;

import com.example.backend.domain.chat.event.ConversationCompletedEvent;
import com.example.backend.domain.chat.event.SatisfactionRatedEvent;
import com.example.backend.application.service.UserProfileApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventConsumer {
    private final UserProfileApplicationService userProfileApplicationService;

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConversationCompleted(ConversationCompletedEvent event) {
        log.info("Conversation completed: sessionId={}, userId={}, intent={}",
                event.getSessionId(), event.getUserId(), event.getIntent());
        if (event.getSessionId() != null) {
            userProfileApplicationService.updateVisitorStats(event.getSessionId());
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSatisfactionRated(SatisfactionRatedEvent event) {
        log.info("Satisfaction rated: sessionId={}, userId={}, rating={}",
                event.getSessionId(), event.getUserId(), event.getSatisfaction());
        if (event.getUserId() != null) {
            userProfileApplicationService.updateUserSatisfaction(event.getUserId(), event.getSatisfaction());
        }
    }
}
