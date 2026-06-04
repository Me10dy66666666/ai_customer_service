package com.example.backend.domain.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeRevisionLog {

    public static final String TYPE_CREATE = "CREATE";
    public static final String TYPE_UPDATE = "UPDATE";
    public static final String TYPE_DELETE = "DELETE";
    public static final String TYPE_PUBLISH = "PUBLISH";
    public static final String TYPE_ARCHIVE = "ARCHIVE";

    private Long id;
    private Long documentId;
    private String changeType;
    private String changedFields;
    private String oldValue;
    private String newValue;
    private String changedBy;
    private LocalDateTime changedAt;
}
