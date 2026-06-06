package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class KnowledgeDocumentRepositoryImpl implements KnowledgeDocumentRepository {

    private final KnowledgeDocumentMapper mapper;

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        com.example.backend.infrastructure.persistence.entity.KnowledgeDocumentEntity po = toEntity(document);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.update(po);
        }
        return toDomain(mapper.selectById(po.getId()));
    }

    @Override
    public Optional<KnowledgeDocument> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<KnowledgeDocument> findAllOrderByCreatedAtDesc() {
        return mapper.findAllOrderByCreatedAtDesc().stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeDocument> findByStatus(String status) {
        return mapper.findByStatus(status).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeDocument> findByStatusIn(List<String> statuses) {
        return mapper.findByStatusIn(statuses).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeDocument> findByCategory(String category) {
        return mapper.findByCategory(category).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByCategory(String category) {
        return mapper.countByCategory(category);
    }

    @Override
    public int clearCategory(String category) {
        return mapper.clearCategory(category);
    }

    @Override
    public Optional<KnowledgeDocument> findByDifyDocumentId(String difyDocumentId) {
        return Optional.ofNullable(mapper.findByDifyDocumentId(difyDocumentId)).map(this::toDomain);
    }

    @Override
    public long countByStatus(String status) {
        return mapper.countByStatus(status);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<KnowledgeDocument> findByStatusBrief(String status) {
        return mapper.findByStatusBrief(status).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeDocument> searchFulltext(String keyword, String category, int offset, int size) {
        return mapper.searchFulltext(keyword, category, offset, size).stream().map(this::toDomain).toList();
    }

    @Override
    public long countSearchFulltext(String keyword, String category) {
        return mapper.countSearchFulltext(keyword, category);
    }

    @Override
    public long countByDifySyncStatus(String difySyncStatus) {
        return mapper.countByDifySyncStatus(difySyncStatus);
    }

    @Override
    public List<KnowledgeDocument> findPendingReviewFiltered(String keyword, String category) {
        return mapper.findPendingReviewFiltered(keyword, category).stream().map(this::toDomain).toList();
    }

    @Override
    public long countPendingReviewFiltered(String keyword, String category) {
        return mapper.countPendingReviewFiltered(keyword, category);
    }

    @Override
    public List<Map<String, Object>> categoryStats() {
        return mapper.categoryStats();
    }

    private KnowledgeDocument toDomain(com.example.backend.infrastructure.persistence.entity.KnowledgeDocumentEntity po) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(po.getId());
        doc.setTitle(po.getTitle());
        doc.setContent(po.getContent());
        doc.setTocJson(po.getTocJson());
        doc.setFileType(po.getFileType());
        doc.setOriginalFileUrl(po.getOriginalFileUrl());
        doc.setPreviewPdfPath(po.getPreviewPdfPath());
        doc.setOcrRawJson(po.getOcrRawJson());
        doc.setDifyDocumentId(po.getDifyDocumentId());
        doc.setDifySyncStatus(po.getDifySyncStatus());
        doc.setCategory(po.getCategory());
        doc.setTags(po.getTags());
        doc.setStatus(po.getStatus());
        doc.setEnabled(po.getEnabled() != null && po.getEnabled() == 1);
        doc.setVersion(po.getVersion());
        doc.setIsLatest(po.getIsLatest() != null && po.getIsLatest() == 1);
        doc.setPublishedAt(po.getPublishedAt());
        doc.setExpiredAt(po.getExpiredAt());
        doc.setArchivedAt(po.getArchivedAt());
        doc.setArchiveReason(po.getArchiveReason());
        doc.setReviewedBy(po.getReviewedBy());
        doc.setReviewedAt(po.getReviewedAt());
        doc.setReviewStartedAt(po.getReviewStartedAt());
        doc.setCreateTime(po.getCreatedAt());
        doc.setUpdateTime(po.getUpdatedAt());
        return doc;
    }

    private com.example.backend.infrastructure.persistence.entity.KnowledgeDocumentEntity toEntity(KnowledgeDocument doc) {
        com.example.backend.infrastructure.persistence.entity.KnowledgeDocumentEntity po =
                new com.example.backend.infrastructure.persistence.entity.KnowledgeDocumentEntity();
        po.setId(doc.getId());
        po.setTitle(doc.getTitle());
        po.setContent(doc.getContent());
        po.setTocJson(doc.getTocJson());
        po.setFileType(doc.getFileType());
        po.setOriginalFileUrl(doc.getOriginalFileUrl());
        po.setPreviewPdfPath(doc.getPreviewPdfPath());
        po.setOcrRawJson(doc.getOcrRawJson());
        po.setDifyDocumentId(doc.getDifyDocumentId());
        po.setDifySyncStatus(doc.getDifySyncStatus());
        po.setCategory(doc.getCategory());
        po.setTags(doc.getTags());
        po.setStatus(doc.getStatus());
        po.setEnabled(doc.getEnabled() != null && doc.getEnabled() ? 1 : 0);
        po.setVersion(doc.getVersion());
        po.setIsLatest(doc.getIsLatest() != null && doc.getIsLatest() ? 1 : 0);
        po.setPublishedAt(doc.getPublishedAt());
        po.setExpiredAt(doc.getExpiredAt());
        po.setArchivedAt(doc.getArchivedAt());
        po.setArchiveReason(doc.getArchiveReason());
        po.setReviewedBy(doc.getReviewedBy());
        po.setReviewedAt(doc.getReviewedAt());
        po.setReviewStartedAt(doc.getReviewStartedAt());
        return po;
    }
}
