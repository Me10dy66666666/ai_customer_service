package com.example.backend.domain.knowledge.service;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface KnowledgeBasePort {
    String uploadFile(File file, String filename, String datasetId);
    void deleteDocument(String datasetId, String documentId);
    Map<String, Object> getDataset(String datasetId);
    void updateDocumentStatus(String datasetId, String documentId, boolean enable);
    List<Map<String, Object>> listDocuments(String datasetId, int page, int limit);
    List<Map<String, Object>> listAllDocuments(String datasetId);
}
