package com.example.backend.infrastructure.dify;

import com.example.backend.domain.chat.service.AgentRuntimePort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dify 平台兼容回退适配器
 *
 * 仅当显式设置 agent.runtime=dify 时激活，作为旧运行时兼容回退。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.runtime", havingValue = "dify")
public class DifyAdapter implements AgentRuntimePort {
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
