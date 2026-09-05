package com.example.backend.infrastructure.dsh;

import com.example.backend.domain.chat.service.AiChatPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/** Selects DeepSeek Harness as the customer-service Agent runtime. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.provider", havingValue = "dsh")
public class DshAiChatAdapter implements AiChatPort {

    private final DshGatewayClient dshGatewayClient;

    @Override
    public void sendStreamingMessage(String query, String user, String conversationId,
                                     Map<String, Object> inputs,
                                     Consumer<String> onData, Consumer<String> onError) {
        dshGatewayClient.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
    }

    @Override
    public Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                                   Map<String, Object> inputs) {
        return dshGatewayClient.sendMessage(query, user, conversationId, inputs);
    }
}
