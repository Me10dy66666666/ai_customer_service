package com.example.backend.domain.knowledge.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeViewLog {
    private Long id;
    private Long documentId;
    private Long viewerId;
    private String viewerRole;
    private LocalDateTime viewedAt;
}
