package com.example.backend.domain.knowledge.repository;

import com.example.backend.domain.knowledge.model.OcrSegment;

import java.util.List;

public interface OcrSegmentRepository {
    void save(OcrSegment segment);
    void saveBatch(List<OcrSegment> segments);
    List<OcrSegment> findByDocumentIdOrderBySegmentIndex(Long documentId);
    List<OcrSegment> findByDocumentIdAndStatus(Long documentId, String status);
    void updateStatus(Long id, Long documentId, String status, String reviewedText);
    void deleteByDocumentId(Long documentId);
}
