-- PostgreSQL + pgvector schema for the data-pipeline service.
-- The service validates that EMBEDDING_DIMENSIONS matches vector(1024) at startup.
-- Change the type and the environment value together when selecting another embedding model.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id          TEXT PRIMARY KEY,
    document_id TEXT NOT NULL,
    chunk_id    TEXT NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1024) NOT NULL,
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_chunks_document_chunk UNIQUE (document_id, chunk_id)
);

-- HNSW is selected because customer-service knowledge is updated incrementally and the index
-- does not require a training pass. Tune m/ef_construction after measuring recall and latency.
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_embedding_hnsw
    ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_metadata
    ON knowledge_chunks USING gin (metadata);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_dataset_domain
    ON knowledge_chunks ((metadata->>'datasetId'), (metadata->>'knowledgeDomain'))
    WHERE enabled = TRUE;
