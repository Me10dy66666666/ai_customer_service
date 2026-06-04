package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeViewLogEntity {
    private Long id;
    private Long documentId;
    private Long viewerId;
    private String viewerRole;
    private LocalDateTime viewedAt;
}
