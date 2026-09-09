package com.example.backend.infrastructure.pi;

import com.example.backend.domain.chat.service.AgentRuntimePort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/** Default customer-service runtime adapter. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.runtime", havingValue = "pi", matchIfMissing = true)
public class PiAgentRuntimeAdapter implements AgentRuntimePort {
    private final PiAgentRuntimeClient client;

    @Override
    public void sendStreamingMessage(String query, String user, String conversationId,
                                     Map<String, Object> inputs, Consumer<String> onData,
                                     Consumer<String> onError) {
        client.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
    }

    @Override
    public Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                                   Map<String, Object> inputs) {
        return client.sendBlockingMessage(query, user, conversationId, inputs);
    }
}
