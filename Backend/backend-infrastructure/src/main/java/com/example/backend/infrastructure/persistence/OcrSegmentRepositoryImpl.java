package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.knowledge.model.OcrSegment;
import com.example.backend.domain.knowledge.repository.OcrSegmentRepository;
import com.example.backend.infrastructure.persistence.mapper.OcrSegmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OcrSegmentRepositoryImpl implements OcrSegmentRepository {

    private final OcrSegmentMapper mapper;

    @Override
    public void save(OcrSegment segment) {
        mapper.insert(toEntity(segment));
    }

    @Override
    public void saveBatch(List<OcrSegment> segments) {
        if (segments.isEmpty()) return;
        List<com.example.backend.infrastructure.persistence.entity.OcrSegmentEntity> entities =
                segments.stream().map(this::toEntity).toList();
        mapper.insertBatch(entities);
    }

    @Override
    public List<OcrSegment> findByDocumentIdOrderBySegmentIndex(Long documentId) {
        return mapper.findByDocumentIdOrderBySegmentIndex(documentId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<OcrSegment> findByDocumentIdAndStatus(Long documentId, String status) {
        return mapper.findByDocumentIdAndStatus(documentId, status).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public void updateStatus(Long id, Long documentId, String status, String reviewedText) {
        mapper.updateStatus(id, documentId, status, reviewedText);
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        mapper.deleteByDocumentId(documentId);
    }

    private OcrSegment toDomain(com.example.backend.infrastructure.persistence.entity.OcrSegmentEntity po) {
        OcrSegment seg = new OcrSegment();
        seg.setId(po.getId());
        seg.setDocumentId(po.getDocumentId());
        seg.setSegmentIndex(po.getSegmentIndex());
        seg.setOcrText(po.getOcrText());
        seg.setReviewedText(po.getReviewedText());
        seg.setConfidence(po.getConfidence());
        seg.setBoundingBox(po.getBoundingBox());
        seg.setStatus(po.getStatus());
        seg.setCreatedAt(po.getCreatedAt());
        return seg;
    }

    private com.example.backend.infrastructure.persistence.entity.OcrSegmentEntity toEntity(OcrSegment seg) {
        com.example.backend.infrastructure.persistence.entity.OcrSegmentEntity po =
                new com.example.backend.infrastructure.persistence.entity.OcrSegmentEntity();
        po.setDocumentId(seg.getDocumentId());
        po.setSegmentIndex(seg.getSegmentIndex());
        po.setOcrText(seg.getOcrText());
        po.setReviewedText(seg.getReviewedText());
        po.setConfidence(seg.getConfidence());
        po.setBoundingBox(seg.getBoundingBox());
        po.setStatus(seg.getStatus());
        return po;
    }
}
