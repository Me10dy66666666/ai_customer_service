package com.example.backend.domain.knowledge.event;

import com.example.backend.domain.shared.event.DomainEvent;
import lombok.Getter;

@Getter
public class DocumentPublishedEvent extends DomainEvent {
    private final Long documentId;
    private final String title;
    private final String category;
    private final String difyDocumentId;

    public DocumentPublishedEvent(Long documentId, String title, String category, String difyDocumentId) {
        this.documentId = documentId;
        this.title = title;
        this.category = category;
        this.difyDocumentId = difyDocumentId;
    }
}
