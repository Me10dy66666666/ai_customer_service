package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeSearchLogEntity {
    private Long id;
    private String keyword;
    private Integer resultCount;
    private Long searcherId;
    private LocalDateTime searchedAt;
}
