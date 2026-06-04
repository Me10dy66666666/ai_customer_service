package com.example.backend.domain.knowledge.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeSearchLog {
    private Long id;
    private String keyword;
    private Integer resultCount;
    private Long searcherId;
    private LocalDateTime searchedAt;
}
