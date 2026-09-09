package com.example.backend.domain.chat.service;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Provider-neutral boundary for the customer-service Agent runtime.
 * Business/application code depends on this port, never on Pi or a legacy runtime API.
 */
public interface AgentRuntimePort {
    void sendStreamingMessage(String query, String user, String conversationId,
                               Map<String, Object> inputs, Consumer<String> onData,
                               Consumer<String> onError);

    Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                             Map<String, Object> inputs);
}
