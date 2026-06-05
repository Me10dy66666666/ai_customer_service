package com.example.backend.domain.knowledge.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocument extends BaseAggregateRoot {

    public static final String STATUS_PENDING_OCR = "PENDING_OCR";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_PUBLISHING = "PUBLISHING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    public static final String DIFY_SYNCED = "SYNCED";
    public static final String DIFY_SYNCING = "SYNCING";
    public static final String DIFY_FAILED = "FAILED";

    private Long id;
    private String title;
    private String content;
    private String tocJson;
    private String fileType;
    private String originalFileUrl;
    private String previewPdfPath;
    private String ocrRawJson;
    private String difyDocumentId;
    private String difySyncStatus;
    private String category;
    private String tags;
    private String status;
    private Boolean enabled;
    private Integer version;
    private Boolean isLatest;
    private LocalDateTime publishedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime archivedAt;
    private String archiveReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime reviewStartedAt;

    public boolean isPublished() {
        return STATUS_PUBLISHED.equals(status);
    }

    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }

    public void publish() {
        this.status = STATUS_PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.isLatest = true;
        markUpdated();
    }

    public void archive(String reason) {
        this.status = STATUS_ARCHIVED;
        this.archivedAt = LocalDateTime.now();
        this.archiveReason = reason;
        markUpdated();
    }

    public void markSyncing() {
        this.difySyncStatus = DIFY_SYNCING;
    }

    public void markSynced(String difyDocumentId) {
        this.difyDocumentId = difyDocumentId;
        this.difySyncStatus = DIFY_SYNCED;
    }

    public void markSyncFailed() {
        this.difySyncStatus = DIFY_FAILED;
    }

    public void incrementVersion() {
        if (this.version == null) {
            this.version = 1;
        } else {
            this.version++;
        }
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
