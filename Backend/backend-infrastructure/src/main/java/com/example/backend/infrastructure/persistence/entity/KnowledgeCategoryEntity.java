package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeCategoryEntity {
    private Long id;
    private String name;
    private Long creatorId;
    private Integer sortOrder;
    private String icon;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
