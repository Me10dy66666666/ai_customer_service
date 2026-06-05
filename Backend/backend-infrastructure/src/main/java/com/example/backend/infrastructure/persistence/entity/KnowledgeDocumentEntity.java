package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentEntity {
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
    private Integer enabled;
    private Integer version;
    private Integer isLatest;
    private LocalDateTime publishedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime archivedAt;
    private String archiveReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime reviewStartedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
