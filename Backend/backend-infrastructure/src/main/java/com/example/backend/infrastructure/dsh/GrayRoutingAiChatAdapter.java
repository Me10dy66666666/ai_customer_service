package com.example.backend.infrastructure.dsh;

import com.example.backend.domain.chat.service.AiChatPort;
import com.example.backend.infrastructure.dify.DifyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Stable per-conversation canary router between Dify fallback and the DSH runtime. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.provider", havingValue = "gray")
public class GrayRoutingAiChatAdapter implements AiChatPort {

    private final DifyClient difyClient;
    private final DshGatewayClient dshGatewayClient;

    @Value("${agent.gray.dsh-percentage:0}")
    private int dshPercentage;

    @Override
    public void sendStreamingMessage(String query, String user, String conversationId,
                                     Map<String, Object> inputs, Consumer<String> onData,
                                     Consumer<String> onError) {
        if (useDsh(routeKey(user, conversationId), dshPercentage)) {
            dshGatewayClient.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
            return;
        }
        difyClient.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
    }

    @Override
    public Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                                   Map<String, Object> inputs) {
        return useDsh(routeKey(user, conversationId), dshPercentage)
                ? dshGatewayClient.sendMessage(query, user, conversationId, inputs)
                : difyClient.sendMessage(query, user, conversationId, inputs);
    }

    public static boolean useDsh(String routeKey, int percentage) {
        int normalizedPercentage = Math.max(0, Math.min(100, percentage));
        return Math.floorMod(routeKey.hashCode(), 100) < normalizedPercentage;
    }

    private String routeKey(String user, String conversationId) {
        return conversationId == null || conversationId.isBlank()
                ? Objects.toString(user, "anonymous") : conversationId;
    }
}
