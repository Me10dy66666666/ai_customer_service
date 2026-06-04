package com.example.backend.application.service;

import lombok.Data;

@Data
public class KnowledgeDocumentEsEntity {
    private Long id;
    private String title;
    private String content;
    private String tocJson;
    private String fileType;
    private String category;
    private String tags;
    private String status;
    private Integer version;
    private Boolean enabled;
}
