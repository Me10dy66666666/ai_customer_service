package com.example.backend.application.event;

import com.example.backend.domain.auth.event.UserLoggedInEvent;
import com.example.backend.domain.auth.event.UserRegisteredEvent;
import com.example.backend.domain.profile.event.UserProfileBuiltEvent;
import com.example.backend.domain.profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventConsumer {
    private final UserProfileRepository userProfileRepository;

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("User registered: userId={}, sessionId={}", event.getUserId(), event.getSessionId());
        if (event.getSessionId() != null && !event.getSessionId().isEmpty()) {
            userProfileRepository.findBySessionId(event.getSessionId()).ifPresent(visitor -> {
                visitor.setUserId(event.getUserId());
                visitor.setUserType("REGISTERED");
                userProfileRepository.save(visitor);
                log.info("Merged visitor profile sessionId={} to userId={}", event.getSessionId(), event.getUserId());
            });
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserLoggedIn(UserLoggedInEvent event) {
        log.info("User logged in: userId={}, sessionId={}", event.getUserId(), event.getSessionId());
        if (event.getSessionId() != null && !event.getSessionId().isEmpty()) {
            userProfileRepository.findBySessionId(event.getSessionId()).ifPresent(visitor -> {
                userProfileRepository.findByUserId(event.getUserId()).ifPresentOrElse(
                    userProfile -> {
                        userProfile.merge(visitor);
                        userProfileRepository.save(userProfile);
                        userProfileRepository.deleteById(visitor.getId());
                    },
                    () -> {
                        visitor.setUserId(event.getUserId());
                        visitor.setUserType("REGISTERED");
                        userProfileRepository.save(visitor);
                    }
                );
            });
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProfileBuilt(UserProfileBuiltEvent event) {
        log.info("User profile built: userId={}, type={}, tags={}", event.getUserId(), event.getUserType(), event.getTags());
    }
}
