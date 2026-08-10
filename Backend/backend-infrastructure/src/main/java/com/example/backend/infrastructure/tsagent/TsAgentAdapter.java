package com.example.backend.infrastructure.tsagent;

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
 * TS Agent 适配器
 *
 * 实现与 DifyAdapter 完全相同的接口（AiChatPort + KnowledgeBasePort），
 * 但底层调用 TypeScript 重构的 Agent 服务，而非 Dify 平台。
 *
 * 切换方式：在 application.yml 中设置 agent.provider=ts-agent 即可激活此适配器，
 * 设置 agent.provider=dify（或不设置）则使用 DifyAdapter。
 *
 * @see com.example.backend.infrastructure.dify.DifyAdapter
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.provider", havingValue = "ts-agent", matchIfMissing = false)
public class TsAgentAdapter implements AiChatPort, KnowledgeBasePort {

    private final TsAgentClient tsAgentClient;

    /* ──── AiChatPort ──── */

    @Override
    public void sendStreamingMessage(String query, String user, String conversationId,
                                      Map<String, Object> inputs,
                                      Consumer<String> onData, Consumer<String> onError) {
        tsAgentClient.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
    }

    @Override
    public Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                                     Map<String, Object> inputs) {
        return tsAgentClient.sendMessage(query, user, conversationId, inputs);
    }

    /* ──── KnowledgeBasePort ──── */

    @Override
    public String uploadFile(File file, String filename, String datasetId) {
        return tsAgentClient.uploadFile(file, filename, datasetId);
    }

    @Override
    public void deleteDocument(String datasetId, String documentId) {
        tsAgentClient.deleteDocument(datasetId, documentId);
    }

    @Override
    public Map<String, Object> getDataset(String datasetId) {
        return tsAgentClient.getDataset(datasetId);
    }

    @Override
    public void updateDocumentStatus(String datasetId, String documentId, boolean enable) {
        tsAgentClient.updateDocumentStatus(datasetId, documentId, enable);
    }

    @Override
    public List<Map<String, Object>> listDocuments(String datasetId, int page, int limit) {
        return tsAgentClient.listDocuments(datasetId, page, limit);
    }

    @Override
    public List<Map<String, Object>> listAllDocuments(String datasetId) {
        return tsAgentClient.listAllDocuments(datasetId);
    }
}
