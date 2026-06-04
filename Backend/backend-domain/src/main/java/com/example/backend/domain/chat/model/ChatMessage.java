package com.example.backend.domain.chat.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChatMessage extends BaseAggregateRoot {
    private Long id;
    private String sessionId;
    private String senderType;
    private Long senderId;
    private String content;
    private Integer messageSeq;

    public static final String TYPE_USER = "USER";
    public static final String TYPE_AGENT = "AGENT";
    public static final String TYPE_SYSTEM = "SYSTEM";

    public static ChatMessage create(String sessionId, String senderType, Long senderId,
                                     String content, int messageSeq) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setMessageSeq(messageSeq);
        return msg;
    }

    public static ChatMessage user(String sessionId, Long userId, String content, int seq) {
        return create(sessionId, TYPE_USER, userId, content, seq);
    }

    public static ChatMessage agent(String sessionId, Long agentId, String content, int seq) {
        return create(sessionId, TYPE_AGENT, agentId, content, seq);
    }

    public static ChatMessage system(String sessionId, String content, int seq) {
        return create(sessionId, TYPE_SYSTEM, null, content, seq);
    }
}
