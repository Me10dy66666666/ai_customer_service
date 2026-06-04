package com.example.backend.application.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import com.example.backend.domain.knowledge.service.KnowledgeSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(value = "knowledge.search.engine", havingValue = "elasticsearch")
public class ElasticsearchKnowledgeSearchService implements KnowledgeSearchService {

    private static final String ES_FIELD_CONTENT = "content";
    private static final String ES_FIELD_TITLE_BOOST = "title^2";
    private static final String ES_FIELD_ENABLED = "enabled";
    private static final String ES_STATUS_PUBLISHED = "PUBLISHED";
    private static final String ES_FIELD_STATUS_KEYWORD = "status.keyword";
    private static final String ES_INDEX_NAME = "knowledge_documents";

    private final ElasticsearchClient esClient;

    public ElasticsearchKnowledgeSearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    @Override
    public List<KnowledgeDocument> search(String keyword, int page, int size) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            if (keyword != null && !keyword.isBlank()) {
                boolBuilder.must(MultiMatchQuery.of(m -> m
                        .query(keyword)
                        .fields(ES_FIELD_TITLE_BOOST, ES_FIELD_CONTENT)
                )._toQuery());
            }
            boolBuilder.filter(TermQuery.of(t -> t.field(ES_FIELD_ENABLED).value(true))._toQuery());
            boolBuilder.filter(TermQuery.of(t -> t.field(ES_FIELD_STATUS_KEYWORD).value(ES_STATUS_PUBLISHED))._toQuery());

            SearchResponse<KnowledgeDocumentEsEntity> response = esClient.search(s -> s
                    .index(ES_INDEX_NAME)
                    .from((page - 1) * size)
                    .size(size)
                    .query(q -> q.bool(boolBuilder.build())),
                    KnowledgeDocumentEsEntity.class);

            List<KnowledgeDocument> documents = new ArrayList<>();
            for (Hit<KnowledgeDocumentEsEntity> hit : response.hits().hits()) {
                KnowledgeDocumentEsEntity source = hit.source();
                if (source != null) {
                    documents.add(toDomain(source));
                }
            }
            return documents;
        } catch (Exception e) {
            log.error("Elasticsearch search failed for keyword '{}': {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long count(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return countAll();
        }
        try {
            BoolQuery boolQuery = BoolQuery.of(b -> b
                    .must(MultiMatchQuery.of(m -> m
                            .query(keyword)
                            .fields(ES_FIELD_TITLE_BOOST, ES_FIELD_CONTENT)
                    )._toQuery())
                    .filter(TermQuery.of(t -> t.field(ES_FIELD_ENABLED).value(true))._toQuery())
                    .filter(TermQuery.of(t -> t.field(ES_FIELD_STATUS_KEYWORD).value(ES_STATUS_PUBLISHED))._toQuery())
            );

            SearchResponse<KnowledgeDocumentEsEntity> response = esClient.search(s -> s
                    .index(ES_INDEX_NAME)
                    .size(0)
                    .query(q -> q.bool(boolQuery)),
                    KnowledgeDocumentEsEntity.class);

            var total = response.hits().total();
            return total != null ? total.value() : 0;
        } catch (Exception e) {
            log.error("Elasticsearch count failed for keyword '{}': {}", keyword, e.getMessage());
            return 0;
        }
    }

    @Override
    public List<KnowledgeDocument> search(String keyword, String category, int page, int size) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            if (keyword != null && !keyword.isBlank()) {
                boolBuilder.must(MultiMatchQuery.of(m -> m
                        .query(keyword)
                        .fields(ES_FIELD_TITLE_BOOST, ES_FIELD_CONTENT)
                )._toQuery());
            }
            boolBuilder.filter(TermQuery.of(t -> t.field(ES_FIELD_ENABLED).value(true))._toQuery());
            boolBuilder.filter(TermQuery.of(t -> t.field(ES_FIELD_STATUS_KEYWORD).value(ES_STATUS_PUBLISHED))._toQuery());
            if (category != null && !category.isBlank()) {
                boolBuilder.filter(TermQuery.of(t -> t.field("category.keyword").value(category))._toQuery());
            }

            SearchResponse<KnowledgeDocumentEsEntity> response = esClient.search(s -> s
                    .index(ES_INDEX_NAME)
                    .from((page - 1) * size)
                    .size(size)
                    .query(q -> q.bool(boolBuilder.build())),
                    KnowledgeDocumentEsEntity.class);

            List<KnowledgeDocument> documents = new ArrayList<>();
            for (Hit<KnowledgeDocumentEsEntity> hit : response.hits().hits()) {
                KnowledgeDocumentEsEntity source = hit.source();
                if (source != null) {
                    documents.add(toDomain(source));
                }
            }
            return documents;
        } catch (Exception e) {
            log.error("Elasticsearch search failed for keyword '{}' category '{}': {}", keyword, category, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long count(String keyword, String category) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            if (keyword != null && !keyword.isBlank()) {
                boolBuilder.must(MultiMatchQuery.of(m -> m
                        .query(keyword)
                        .fields(ES_FIELD_TITLE_BOOST, ES_FIELD_CONTENT)
                )._toQuery());
            }
            boolBuilder.filter(TermQuery.of(t -> t.field(ES_FIELD_ENABLED).value(true))._toQuery());
            boolBuilder.filter(TermQuery.of(t -> t.field(ES_FIELD_STATUS_KEYWORD).value(ES_STATUS_PUBLISHED))._toQuery());
            if (category != null && !category.isBlank()) {
                boolBuilder.filter(TermQuery.of(t -> t.field("category.keyword").value(category))._toQuery());
            }

            SearchResponse<KnowledgeDocumentEsEntity> response = esClient.search(s -> s
                    .index(ES_INDEX_NAME)
                    .size(0)
                    .query(q -> q.bool(boolBuilder.build())),
                    KnowledgeDocumentEsEntity.class);

            var total = response.hits().total();
            return total != null ? total.value() : 0;
        } catch (Exception e) {
            log.error("Elasticsearch count failed for keyword '{}' category '{}': {}", keyword, category, e.getMessage());
            return 0;
        }
    }

    private long countAll() {
        try {
            BoolQuery filterQuery = BoolQuery.of(b -> b
                    .filter(TermQuery.of(t -> t.field(ES_FIELD_ENABLED).value(true))._toQuery())
                    .filter(TermQuery.of(t -> t.field(ES_FIELD_STATUS_KEYWORD).value(ES_STATUS_PUBLISHED))._toQuery())
            );
            SearchResponse<KnowledgeDocumentEsEntity> response = esClient.search(s -> s
                    .index(ES_INDEX_NAME)
                    .size(0)
                    .query(q -> q.bool(filterQuery)),
                    KnowledgeDocumentEsEntity.class);
            var total = response.hits().total();
            return total != null ? total.value() : 0;
        } catch (Exception e) {
            log.error("Elasticsearch countAll failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    private KnowledgeDocument toDomain(KnowledgeDocumentEsEntity entity) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(entity.getId());
        doc.setTitle(entity.getTitle());
        doc.setContent(entity.getContent());
        doc.setTocJson(entity.getTocJson());
        doc.setFileType(entity.getFileType());
        doc.setCategory(entity.getCategory());
        doc.setTags(entity.getTags());
        doc.setStatus(entity.getStatus());
        doc.setVersion(entity.getVersion());
        doc.setEnabled(entity.getEnabled());
        return doc;
    }
}
