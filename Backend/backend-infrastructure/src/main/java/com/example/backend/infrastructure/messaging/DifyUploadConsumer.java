package com.example.backend.infrastructure.messaging;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.repository.KnowledgeOutboxRepository;
import com.example.backend.domain.knowledge.service.KnowledgeBasePort;
import com.example.backend.infrastructure.es.EsDocumentIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(value = "spring.rabbitmq.host")
public class DifyUploadConsumer {

    private static final String DEFAULT_EXTENSION = ".md";

    private final KnowledgeBasePort knowledgeBasePort;
    private final KnowledgeOutboxRepository outboxRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final EsDocumentIndexService esIndexService;

    @Value("${dify.knowledge.dataset-id:}")
    private String difyDatasetId;

    public DifyUploadConsumer(KnowledgeBasePort knowledgeBasePort,
                              KnowledgeOutboxRepository outboxRepository,
                              KnowledgeDocumentRepository documentRepository,
                              ObjectMapper objectMapper,
                              EsDocumentIndexService esIndexService) {
        this.knowledgeBasePort = knowledgeBasePort;
        this.outboxRepository = outboxRepository;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
        this.esIndexService = esIndexService;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMqMessageBusAdapter.RabbitMqDeclarations.DIFY_UPLOAD_QUEUE)
    public void onMessage(byte[] raw) {
        try {
            Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
            Long outboxId = toLong(payload.get("outboxId"));
            Long documentId = toLong(payload.get("documentId"));
            String title = (String) payload.get("title");
            String content = (String) payload.get("content");
            String fileType = (String) payload.getOrDefault("fileType", DEFAULT_EXTENSION);
            log.info("DifyUpload consumed: docId={}, outboxId={}", documentId, outboxId);

            String extension = resolveExtension(fileType);
            String difyFilename = stripOriginalExtension(title) + extension;

            Path tempFile = Files.createTempFile("dify-upload-", extension);
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }

            String difyDocId = knowledgeBasePort.uploadFile(
                    tempFile.toFile(), difyFilename, difyDatasetId);

            if (outboxId != null) {
                outboxRepository.markCompleted(outboxId);
            }

            KnowledgeDocument doc = documentRepository.findById(documentId).orElse(null);
            if (doc != null) {
                doc.markSynced(difyDocId);
                doc.publish();
                documentRepository.save(doc);
                esIndexService.indexDocument(doc);
                if (doc.getEnabled() != null && !doc.getEnabled()) {
                    try {
                        knowledgeBasePort.updateDocumentStatus(difyDatasetId, difyDocId, false);
                    } catch (Exception e) {
                        log.warn("Failed to sync disabled status to Dify for doc {}: {}", documentId, e.getMessage());
                    }
                }
            }

            log.info("DifyUpload success: docId={}, difyDocId={}", documentId, difyDocId);
            Files.deleteIfExists(tempFile);

        } catch (Exception e) {
            log.error("DifyUpload consumer error, will retry via MQ: {}", e.getMessage());
            throw new RuntimeException("Dify upload failed, will be retried by MQ", e);
        }
    }

    private String resolveExtension(String fileType) {
        if (fileType == null || fileType.isBlank()) return DEFAULT_EXTENSION;
        String lower = fileType.toLowerCase();
        if (lower.contains("md") || lower.contains("txt")) return ".md";
        if (lower.contains("pdf")) return ".md";
        if (lower.contains("docx")) return ".md";
        return DEFAULT_EXTENSION;
    }

    private String stripOriginalExtension(String filename) {
        if (filename == null || filename.isBlank()) return "document";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
