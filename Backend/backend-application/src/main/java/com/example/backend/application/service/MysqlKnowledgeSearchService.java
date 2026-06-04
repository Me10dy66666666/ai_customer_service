package com.example.backend.application.service;

import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.example.backend.domain.knowledge.service.KnowledgeSearchService;
import com.example.backend.common.util.FulltextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "knowledge.search.engine", havingValue = "mysql", matchIfMissing = true)
public class MysqlKnowledgeSearchService implements KnowledgeSearchService {

    private final KnowledgeDocumentRepository documentRepository;

    @Override
    public List<KnowledgeDocument> search(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            List<KnowledgeDocument> all = filterValidDocuments(documentRepository.findByStatusBrief("PUBLISHED"));
            int fromIndex = (page - 1) * size;
            int toIndex = Math.min(fromIndex + size, all.size());
            if (fromIndex >= all.size()) return Collections.emptyList();
            return all.subList(fromIndex, toIndex);
        }
        int offset = (page - 1) * size;
        return filterValidDocuments(documentRepository.searchFulltext(sanitizeKeyword(keyword), null, offset, size));
    }

    @Override
    public long count(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return documentRepository.countByStatus("PUBLISHED");
        }
        return documentRepository.countSearchFulltext(sanitizeKeyword(keyword), null);
    }

    @Override
    public List<KnowledgeDocument> search(String keyword, String category, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            List<KnowledgeDocument> all;
            if (category != null && !category.isBlank()) {
                all = documentRepository.findByCategory(category);
            } else {
                all = documentRepository.findByStatusBrief("PUBLISHED");
            }
            all = filterValidDocuments(all);
            int fromIndex = (page - 1) * size;
            int toIndex = Math.min(fromIndex + size, all.size());
            if (fromIndex >= all.size()) return Collections.emptyList();
            return all.subList(fromIndex, toIndex);
        }
        int offset = (page - 1) * size;
        return filterValidDocuments(documentRepository.searchFulltext(sanitizeKeyword(keyword), category, offset, size));
    }

    @Override
    public long count(String keyword, String category) {
        if (keyword == null || keyword.isBlank()) {
            if (category != null && !category.isBlank()) {
                return documentRepository.countByCategory(category);
            }
            return documentRepository.countByStatus("PUBLISHED");
        }
        return documentRepository.countSearchFulltext(sanitizeKeyword(keyword), category);
    }

    private static String sanitizeKeyword(String keyword) {
        return FulltextUtils.escapeBooleanMode(keyword);
    }

    private static List<KnowledgeDocument> filterValidDocuments(List<KnowledgeDocument> docs) {
        return docs.stream().filter(d -> d.getId() != null).toList();
    }
}
