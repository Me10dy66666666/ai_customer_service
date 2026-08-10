package com.example.backend.infrastructure.dify;

import com.example.backend.domain.chat.service.AiChatPort;
import com.example.backend.domain.knowledge.service.KnowledgeBasePort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dify 平台适配器（默认）
 *
 * 当 agent.provider 未设置或设置为 "dify" 时激活。
 * 设置 agent.provider=ts-agent 将切换到 TsAgentAdapter。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.provider", havingValue = "dify", matchIfMissing = true)
public class DifyAdapter implements AiChatPort, KnowledgeBasePort {
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

    @Override public String uploadFile(File file, String filename, String datasetId) {
        return difyClient.uploadFile(file, filename, datasetId);
    }

    @Override public void deleteDocument(String datasetId, String documentId) {
        difyClient.deleteDocument(datasetId, documentId);
    }

    @Override public Map<String, Object> getDataset(String datasetId) {
        return difyClient.getDataset(datasetId);
    }

    @Override public void updateDocumentStatus(String datasetId, String documentId, boolean enable) {
        difyClient.updateDocumentStatus(datasetId, documentId, enable);
    }

    @Override public List<Map<String, Object>> listDocuments(String datasetId, int page, int limit) {
        return difyClient.listDocuments(datasetId, page, limit);
    }

    @Override public List<Map<String, Object>> listAllDocuments(String datasetId) {
        return difyClient.listAllDocuments(datasetId);
    }
}
