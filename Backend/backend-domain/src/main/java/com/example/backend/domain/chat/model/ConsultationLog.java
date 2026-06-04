package com.example.backend.domain.chat.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConsultationLog extends BaseAggregateRoot {
    private Long id;
    private String sessionId;
    private Long userId;
    private Long agentId;
    private String userInput;
    private String aiResponse;
    private String difyConversationId;
    private String intent;
    private String channel;
    private Integer duration;
    private Integer satisfaction;

    public void rateSatisfaction(int rating) {
        this.satisfaction = rating;
    }

    public void assignToUser(Long userId) {
        this.userId = userId;
    }

    public static ConsultationLog create(String sessionId, Long userId,
                                          String userInput, String channel) {
        ConsultationLog log = new ConsultationLog();
        log.setSessionId(sessionId);
        log.setUserId(userId);
        log.setUserInput(userInput);
        log.setChannel(channel);
        return log;
    }
}
