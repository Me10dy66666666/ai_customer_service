package com.example.backend.domain.knowledge.model;

import lombok.Data;

@Data
public class DocumentLink {
    private Long id;
    private Long sourceDocId;
    private Long targetDocId;
    private String linkType;
}
