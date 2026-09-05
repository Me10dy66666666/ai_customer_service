package com.example.backend.infrastructure.dify;

import com.example.backend.domain.chat.service.AiChatPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dify 平台适配器（默认）
 *
 * 当 agent.provider 未设置或设置为 "dify" 时激活。
 * 设置 agent.provider=dsh 将切换到 DeepSeek Harness 客服 Agent。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.provider", havingValue = "dify", matchIfMissing = true)
public class DifyAdapter implements AiChatPort {
    private final DifyClient difyClient;

    @Override
    public void sendStreamingMessage(String query, String user, String conversationId,
                                      Map<String, Object> inputs, Consumer<String> onData,
                                      Consumer<String> onError) {
        difyClient.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
    }

    @Override
    public Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                                     Map<String, Object> inputs) {
        return difyClient.sendMessage(query, user, conversationId, inputs);
    }

}
