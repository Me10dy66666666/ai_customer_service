package com.example.backend.domain.chat.service;

import java.util.Map;
import java.util.function.Consumer;

public interface AiChatPort {
    void sendStreamingMessage(String query, String user, String conversationId,
                               Map<String, Object> inputs, Consumer<String> onData,
                               Consumer<String> onError);

    Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                             Map<String, Object> inputs);
}
