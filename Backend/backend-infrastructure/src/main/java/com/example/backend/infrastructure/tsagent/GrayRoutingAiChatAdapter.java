package com.example.backend.infrastructure.tsagent;

import com.example.backend.domain.chat.service.AiChatPort;
import com.example.backend.domain.knowledge.service.KnowledgeBasePort;
import com.example.backend.infrastructure.dify.DifyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Stable per-conversation canary router. Knowledge management stays on Dify during the pilot. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.provider", havingValue = "gray")
public class GrayRoutingAiChatAdapter implements AiChatPort, KnowledgeBasePort {

    private final DifyClient difyClient;
    private final TsAgentClient dshCompatibleClient;

    @Value("${agent.gray.dsh-percentage:0}")
    private int dshPercentage;

    @Override
    public void sendStreamingMessage(String query, String user, String conversationId,
                                     Map<String, Object> inputs, Consumer<String> onData,
                                     Consumer<String> onError) {
        if (useDsh(routeKey(user, conversationId), dshPercentage)) {
            dshCompatibleClient.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
        } else {
            difyClient.sendStreamingMessage(query, user, conversationId, inputs, onData, onError);
        }
    }

    @Override
    public Map<String, String> sendBlockingMessage(String query, String user, String conversationId,
                                                    Map<String, Object> inputs) {
        return useDsh(routeKey(user, conversationId), dshPercentage)
                ? dshCompatibleClient.sendMessage(query, user, conversationId, inputs)
                : difyClient.sendMessage(query, user, conversationId, inputs);
    }

    public static boolean useDsh(String routeKey, int percentage) {
        int normalizedPercentage = Math.max(0, Math.min(100, percentage));
        return Math.floorMod(routeKey.hashCode(), 100) < normalizedPercentage;
    }

    private String routeKey(String user, String conversationId) {
        return conversationId == null || conversationId.isBlank()
                ? java.util.Objects.toString(user, "anonymous") : conversationId;
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
