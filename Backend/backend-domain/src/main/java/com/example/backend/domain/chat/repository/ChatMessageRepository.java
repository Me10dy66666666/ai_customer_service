package com.example.backend.domain.chat.repository;

import com.example.backend.domain.chat.model.ChatMessage;
import java.util.List;

public interface ChatMessageRepository {
    ChatMessage save(ChatMessage message);

    List<ChatMessage> findBySessionIdOrderByMessageSeqAsc(String sessionId);

    Integer getMaxMessageSeqBySessionId(String sessionId);
}
