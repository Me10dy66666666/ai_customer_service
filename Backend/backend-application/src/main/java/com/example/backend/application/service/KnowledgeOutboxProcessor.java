package com.example.backend.application.service;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.model.KnowledgeOutbox;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.repository.KnowledgeOutboxRepository;
import com.example.backend.domain.knowledge.service.KnowledgeBasePort;
import com.example.backend.infrastructure.es.EsDocumentIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KnowledgeOutboxProcessor {

    private final KnowledgeOutboxRepository outboxRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBasePort knowledgeBasePort;
    private final EsDocumentIndexService esIndexService;
    private final ObjectMapper objectMapper;

    @Value("${dify.knowledge.dataset-id:}")
    private String difyDatasetId;

    public KnowledgeOutboxProcessor(KnowledgeOutboxRepository outboxRepository,
                                     KnowledgeDocumentRepository documentRepository,
                                     KnowledgeBasePort knowledgeBasePort,
                                     EsDocumentIndexService esIndexService,
                                     ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.documentRepository = documentRepository;
        this.knowledgeBasePort = knowledgeBasePort;
        this.esIndexService = esIndexService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 300_000)
    @SchedulerLock(name = "processKnowledgeOutbox", lockAtMostFor = "4m", lockAtLeastFor = "30s")
    public void processOutbox() {
        LocalDateTime now = LocalDateTime.now().plusMinutes(5);
        List<KnowledgeOutbox> pending = outboxRepository.findPendingBefore(now);
        for (KnowledgeOutbox entry : pending) {
            tryProcessEntry(entry);
        }
    }

    @SuppressWarnings("unchecked")
    private void tryProcessEntry(KnowledgeOutbox entry) {
        if (!outboxRepository.updateToProcessing(entry.getId())) {
            return;
        }
        Path tempFile = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(entry.getPayload(), Map.class);
            String title = (String) payload.getOrDefault("title", "document");
            String content = (String) payload.getOrDefault("content", "");

            tempFile = Files.createTempFile("dify-outbox-", ".md");
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }

            String difyDocId = knowledgeBasePort.uploadFile(
                    tempFile.toFile(), stripOriginalExtension(title) + ".md", difyDatasetId);
            outboxRepository.markCompleted(entry.getId());

            KnowledgeDocument doc = documentRepository.findById(entry.getDocumentId()).orElse(null);
            if (doc != null && !KnowledgeDocument.STATUS_PUBLISHED.equals(doc.getStatus())) {
                doc.markSynced(difyDocId);
                doc.publish();
                documentRepository.save(doc);
                esIndexService.indexDocument(doc);
            }

            log.info("Outbox retry success: outboxId={}, difyDocId={}", entry.getId(), difyDocId);

        } catch (Exception e) {
            log.error("Outbox retry failed: outboxId={}, error={}", entry.getId(), e.getMessage());
            entry.incrementRetry();
            if (entry.exceedsMaxRetry()) {
                outboxRepository.markFailed(entry.getId(),
                        "Max retry exceeded: " + e.getMessage());
            } else {
                long delaySec = (long) Math.pow(2, entry.getRetryCount()) * 10;
                LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySec);
                outboxRepository.scheduleRetry(entry.getId(), nextRetry, e.getMessage());
            }
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception cleanupError) {
                    log.warn("Failed to clean temporary outbox file: path={}, error={}",
                            tempFile, cleanupError.getMessage());
                }
            }
        }
    }

    private String stripOriginalExtension(String title) {
        if (title == null) return "document";
        int dot = title.lastIndexOf(".");
        return dot > 0 ? title.substring(0, dot) : title;
    }
}
