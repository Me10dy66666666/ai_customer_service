package com.example.backend.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.analysis.TokenChar;
import com.example.backend.domain.knowledge.model.KnowledgeDocument;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EsDocumentIndexService {

    private static final String INDEX_NAME = "knowledge_documents";

    private final ElasticsearchClient esClient;

    public EsDocumentIndexService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(INDEX_NAME)
                        .settings(s -> s
                                .analysis(a -> a
                                        .tokenizer("ngram_tokenizer", t -> t
                                                .definition(d -> d
                                                        .ngram(n -> n
                                                                .minGram(1)
                                                                .maxGram(2)
                                                                .tokenChars(TokenChar.Letter, TokenChar.Digit)
                                                        )
                                                )
                                        )
                                )
                        ));
                log.info("Created ES index: {}", INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("Elasticsearch not available, skipping index creation: {}", e.getMessage());
        }
    }

    public void indexDocument(KnowledgeDocument doc) {
        try {
            Map<String, Object> document = new HashMap<>();
            document.put("id", doc.getId());
            document.put("title", doc.getTitle());
            document.put("content", doc.getContent());
            document.put("tocJson", doc.getTocJson());
            document.put("fileType", doc.getFileType());
            document.put("category", doc.getCategory());
            document.put("tags", doc.getTags());
            document.put("status", doc.getStatus());
            document.put("version", doc.getVersion());
            document.put("enabled", doc.getEnabled());

            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(doc.getId()))
                    .document(document)
                    .refresh(Refresh.WaitFor));
        } catch (Exception e) {
            log.error("Failed to index document {}: {}", doc.getId(), e.getMessage());
        }
    }

    public void deleteDocument(Long id) {
        try {
            esClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(String.valueOf(id))
                    .refresh(Refresh.WaitFor));
        } catch (Exception e) {
            log.error("Failed to delete document {} from ES: {}", id, e.getMessage());
        }
    }

    public int reindexAll(List<KnowledgeDocument> docs) {
        deleteAll();
        int indexed = 0;
        for (KnowledgeDocument doc : docs) {
            try {
                indexDocument(doc);
                indexed++;
            } catch (Exception e) {
                log.error("Failed to reindex document {}: {}", doc.getId(), e.getMessage());
            }
        }
        log.info("Reindexed {} documents to ES", indexed);
        return indexed;
    }

    public void deleteAll() {
        try {
            esClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q.matchAll(m -> m)));
            log.info("Deleted all documents from ES index: {}", INDEX_NAME);
        } catch (Exception e) {
            log.error("Failed to delete all documents from ES: {}", e.getMessage());
        }
    }

    public void clearCache() {
        try {
            esClient.indices().clearCache(c -> c.index(INDEX_NAME));
            log.info("Cleared ES cache for index: {}", INDEX_NAME);
        } catch (Exception e) {
            log.error("Failed to clear ES cache: {}", e.getMessage());
        }
    }
}
