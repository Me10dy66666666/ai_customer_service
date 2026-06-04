package com.example.backend.domain.knowledge.service;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;

import java.util.List;

public interface KnowledgeSearchService {
    List<KnowledgeDocument> search(String keyword, int page, int size);
    long count(String keyword);
    List<KnowledgeDocument> search(String keyword, String category, int page, int size);
    long count(String keyword, String category);
}
