package com.example.backend.application.service;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.service.KnowledgeBasePort;
import com.example.backend.infrastructure.es.EsDocumentIndexService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DifySyncService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBasePort knowledgeBasePort;
    private final EsDocumentIndexService esIndexService;

    @Value("${dify.knowledge.dataset-id:}")
    private String difyDatasetId;

    public DifySyncService(KnowledgeDocumentRepository documentRepository,
                           KnowledgeBasePort knowledgeBasePort,
                           EsDocumentIndexService esIndexService) {
        this.documentRepository = documentRepository;
        this.knowledgeBasePort = knowledgeBasePort;
        this.esIndexService = esIndexService;
    }

    @Scheduled(fixedDelay = 300000)
    @SchedulerLock(name = "syncDifyDocuments", lockAtMostFor = "4m")
    public void syncDifyDocuments() {
        if (difyDatasetId == null || difyDatasetId.trim().isEmpty()) {
            log.debug("Dify datasetId not configured, skipping sync");
            return;
        }
        try {
            List<Map<String, Object>> difyDocs = knowledgeBasePort.listAllDocuments(difyDatasetId);
            int createdCount = 0;
            int updatedCount = 0;
            for (Map<String, Object> dDoc : difyDocs) {
                String difyId = String.valueOf(dDoc.get("id"));
                String name = String.valueOf(dDoc.get("name"));
                boolean difyEnabled = !dDoc.containsKey("enabled") || Boolean.TRUE.equals(dDoc.get("enabled"));

                KnowledgeDocument existing = documentRepository.findByDifyDocumentId(difyId).orElse(null);
                if (existing == null) {
                    KnowledgeDocument newDoc = new KnowledgeDocument();
                    newDoc.setTitle(name);
                    newDoc.setDifyDocumentId(difyId);
                    newDoc.setDifySyncStatus(KnowledgeDocument.DIFY_SYNCED);
                    newDoc.setStatus(KnowledgeDocument.STATUS_PUBLISHED);
                    newDoc.setCategory("external");
                    newDoc.setEnabled(difyEnabled);
                    newDoc.setVersion(1);
                    newDoc.setIsLatest(true);
                    newDoc.setContent("[外部导入，无法预览]");
                    newDoc.setFileType("UNKNOWN");
                    documentRepository.save(newDoc);
                    esIndexService.indexDocument(newDoc);
                    createdCount++;
                }
            }
            if (createdCount > 0 || updatedCount > 0) {
                log.info("Dify sync completed: {} created, {} updated", createdCount, updatedCount);
            }
        } catch (Exception e) {
            log.error("Dify sync failed", e);
        }
    }
}
