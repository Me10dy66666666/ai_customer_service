package com.example.backend.application.service;

import com.example.backend.common.config.KnowledgeProperties;
import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.model.KnowledgeRevisionLog;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.repository.KnowledgeRevisionLogRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class KnowledgeArchiveJob {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeRevisionLogRepository revisionLogRepository;
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeArchiveJob(KnowledgeDocumentRepository documentRepository,
                                KnowledgeRevisionLogRepository revisionLogRepository,
                                KnowledgeProperties knowledgeProperties) {
        this.documentRepository = documentRepository;
        this.revisionLogRepository = revisionLogRepository;
        this.knowledgeProperties = knowledgeProperties;
    }

    @Scheduled(cron = "${knowledge.document.expire-policy.auto-archive-cron:0 0 3 * * ?}")
    @SchedulerLock(name = "knowledgeAutoArchive", lockAtMostFor = "10m", lockAtLeastFor = "30s")
    public void autoArchive() {
        if (!knowledgeProperties.getExpirePolicy().isEnabled()) {
            return;
        }
        int graceDays = knowledgeProperties.getExpirePolicy().getArchiveGraceDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(graceDays);

        List<KnowledgeDocument> published = documentRepository.findByStatus(
                KnowledgeDocument.STATUS_PUBLISHED);
        int archivedCount = 0;
        for (KnowledgeDocument doc : published) {
            if (doc.getExpiredAt() != null && doc.getExpiredAt().isBefore(threshold)) {
                doc.archive("AUTO_EXPIRE");
                documentRepository.save(doc);

                KnowledgeRevisionLog log = new KnowledgeRevisionLog();
                log.setDocumentId(doc.getId());
                log.setChangeType(KnowledgeRevisionLog.TYPE_ARCHIVE);
                log.setChangedFields("status");
                log.setOldValue(KnowledgeDocument.STATUS_PUBLISHED);
                log.setNewValue(KnowledgeDocument.STATUS_ARCHIVED);
                log.setChangedBy("system");
                revisionLogRepository.save(log);

                archivedCount++;
            }
        }
        if (archivedCount > 0) {
            log.info("Auto-archived {} expired documents", archivedCount);
        }
    }
}
