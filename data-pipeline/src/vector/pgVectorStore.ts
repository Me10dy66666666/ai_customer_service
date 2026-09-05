import { Pool, type PoolConfig } from "pg";

import type {
  StoredDocument,
  VectorSearchFilter,
  VectorSearchResult,
  VectorStore,
  VectorDocumentSummary
} from "./vectorStore.js";

const TABLE_NAME = "knowledge_chunks";
const RETRYABLE_SQL_STATES = new Set([
  "08000", "08003", "08006", "40P01", "55P03", "57P01", "53300"
]);
const RETRYABLE_DRIVER_CODES = new Set([
  "ECONNRESET", "ECONNREFUSED", "ETIMEDOUT", "EPIPE", "EAI_AGAIN"
]);

interface QueryResult<Row extends Record<string, unknown> = Record<string, unknown>> {
  rows: Row[];
}

interface PgClient {
  query<Row extends Record<string, unknown> = Record<string, unknown>>(
    text: string,
    values?: readonly unknown[]
  ): Promise<QueryResult<Row>>;
  release(): void;
}

interface PgPool extends PgClient {
  connect(): Promise<PgClient>;
  end(): Promise<void>;
}

interface ChunkRow extends Record<string, unknown> {
  id: string;
  document_id: string;
  chunk_id: string;
  content: string;
  metadata: Record<string, unknown> | string | null;
  enabled: boolean;
  distance?: number | string | null;
}

interface DocumentSummaryRow extends Record<string, unknown> {
  document_id: string;
  title: string | null;
  enabled: boolean;
  chunk_count: number | string;
  updated_at: Date | string;
}

export interface PgVectorStoreOptions {
  connectionString: string;
  dimensions: number;
  poolMax?: number | undefined;
  idleTimeoutMs?: number | undefined;
  connectionTimeoutMs?: number | undefined;
  statementTimeoutMs?: number | undefined;
  retryAttempts?: number | undefined;
  retryDelayMs?: number | undefined;
  pool?: PgPool | undefined;
}

/** Error raised when the vector storage Adapter cannot satisfy its interface. */
export class VectorStoreError extends Error {
  public constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = "VectorStoreError";
  }
}

/**
 * PostgreSQL + pgvector Adapter for the vector storage seam.
 *
 * The Adapter creates only idempotent schema objects, validates the configured vector
 * dimension against the database, writes chunks in one transaction, and retries only
 * failures classified as transient by PostgreSQL or the network driver.
 */
export class PgVectorStore implements VectorStore {
  private readonly pool: PgPool;
  private readonly dimensions: number;
  private readonly retryAttempts: number;
  private readonly retryDelayMs: number;
  private initialization: Promise<void> | null = null;

  public constructor(options: PgVectorStoreOptions) {
    if (!Number.isInteger(options.dimensions) || options.dimensions < 1 || options.dimensions > 16_000) {
      throw new VectorStoreError("embedding dimensions must be an integer between 1 and 16000");
    }
    this.dimensions = options.dimensions;
    this.retryAttempts = Math.max(1, options.retryAttempts ?? 3);
    this.retryDelayMs = Math.max(0, options.retryDelayMs ?? 100);
    this.pool = options.pool ?? (new Pool(this.poolConfig(options)) as unknown as PgPool);
  }

  public async initialize(): Promise<void> {
    if (this.initialization === null) {
      this.initialization = this.withRetry("schema initialization", () => this.runMigrations())
        .catch((error: unknown) => {
          this.initialization = null;
          throw error instanceof VectorStoreError
            ? error
            : new VectorStoreError("failed to initialize pgvector schema", { cause: error });
        });
    }
    await this.initialization;
  }

  public async search(
    queryEmbedding: number[],
    limit: number,
    filter?: VectorSearchFilter
  ): Promise<VectorSearchResult[]> {
    await this.initialize();
    const vector = this.validateEmbedding(queryEmbedding);
    if (!Number.isFinite(limit) || limit < 1) {
      throw new VectorStoreError("search limit must be a positive finite number");
    }
    const boundedLimit = Math.min(Math.trunc(limit), 100);
    const { where, values } = this.buildSearchPredicate(filter);
    values.unshift(vector);
    values.push(boundedLimit);

    return this.withRetry("vector search", async () => {
      const result = await this.pool.query<ChunkRow>(
        `SELECT id, document_id, chunk_id, content, metadata, enabled,
                embedding <=> $1::vector AS distance
           FROM ${TABLE_NAME}
          WHERE ${where.join(" AND ")}
          ORDER BY embedding <=> $1::vector, id
          LIMIT $${values.length}`,
        values
      );
      return result.rows.map((row) => ({
        id: row.id,
        content: row.content,
        metadata: this.readMetadata(row),
        distance: this.readDistance(row.distance)
      }));
    });
  }

  public async add(documents: StoredDocument[], embeddings: number[][]): Promise<void> {
    if (documents.length === 0) return;
    if (documents.length !== embeddings.length) {
      throw new VectorStoreError("documents and embeddings must have the same length");
    }
    await this.initialize();
    await this.withRetry("vector upsert", async () => {
      const client = await this.pool.connect();
      try {
        await client.query("BEGIN");
        for (const [index, document] of documents.entries()) {
          const embedding = this.validateEmbedding(embeddings[index] ?? []);
          const documentId = document.documentId ?? document.metadata.docId ?? document.id;
          const chunkId = document.chunkId
            ?? `${document.metadata.chunkKind ?? "chunk"}-${document.metadata.chunkIndex ?? document.id}`;
          const enabled = document.metadata.enabled !== "false";
          const metadata = { ...document.metadata, enabled: String(enabled) };
          await client.query(
            `INSERT INTO ${TABLE_NAME}
                (id, document_id, chunk_id, content, embedding, metadata, enabled, created_at, updated_at)
             VALUES ($1, $2, $3, $4, $5::vector, $6::jsonb, $7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
             ON CONFLICT (id) DO UPDATE SET
                document_id = EXCLUDED.document_id,
                chunk_id = EXCLUDED.chunk_id,
                content = EXCLUDED.content,
                embedding = EXCLUDED.embedding,
                metadata = EXCLUDED.metadata,
                enabled = EXCLUDED.enabled,
                updated_at = CURRENT_TIMESTAMP`,
            [document.id, documentId, chunkId, document.content, embedding, JSON.stringify(metadata), enabled]
          );
        }
        await client.query("COMMIT");
      } catch (error) {
        try {
          await client.query("ROLLBACK");
        } catch (rollbackError) {
          throw new VectorStoreError("pgvector transaction rollback failed", { cause: rollbackError });
        }
        throw error;
      } finally {
        client.release();
      }
    });
  }

  public async getByIds(ids: string[]): Promise<StoredDocument[]> {
    if (ids.length === 0) return [];
    await this.initialize();
    return this.withRetry("vector lookup", async () => {
      const result = await this.pool.query<ChunkRow>(
        `SELECT id, document_id, chunk_id, content, metadata, enabled
           FROM ${TABLE_NAME}
          WHERE id = ANY($1::text[])
          ORDER BY array_position($1::text[], id)`,
        [ids]
      );
      return result.rows.map((row) => ({
        id: row.id,
        content: row.content,
        metadata: this.readMetadata(row),
        documentId: row.document_id,
        chunkId: row.chunk_id
      }));
    });
  }

  public async deleteByDocument(documentId: string): Promise<void> {
    await this.initialize();
    await this.withRetry("vector document delete", async () => {
      await this.pool.query(`DELETE FROM ${TABLE_NAME} WHERE document_id = $1`, [documentId]);
    });
  }

  public async setEnabled(documentId: string, enabled: boolean): Promise<void> {
    await this.initialize();
    await this.withRetry("vector document status update", async () => {
      await this.pool.query(
        `UPDATE ${TABLE_NAME}
            SET enabled = $2,
                metadata = jsonb_set(metadata, '{enabled}', to_jsonb($2::text), true),
                updated_at = CURRENT_TIMESTAMP
          WHERE document_id = $1`,
        [documentId, enabled]
      );
    });
  }

  public async listDocuments(datasetId: string, page: number, limit: number): Promise<VectorDocumentSummary[]> {
    await this.initialize();
    if (!Number.isInteger(page) || page < 1) throw new VectorStoreError("page must be a positive integer");
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
      throw new VectorStoreError("limit must be an integer between 1 and 100");
    }
    return this.withRetry("vector document listing", async () => {
      const result = await this.pool.query<DocumentSummaryRow>(
        `SELECT document_id,
                MAX(metadata->>'title') AS title,
                BOOL_AND(enabled) AS enabled,
                COUNT(*)::int AS chunk_count,
                MAX(updated_at) AS updated_at
           FROM ${TABLE_NAME}
          WHERE metadata->>'datasetId' = $1
          GROUP BY document_id
          ORDER BY MAX(updated_at) DESC, document_id
          LIMIT $2 OFFSET $3`,
        [datasetId, limit, (page - 1) * limit]
      );
      return result.rows.map((row) => ({
        documentId: row.document_id,
        title: row.title ?? row.document_id,
        enabled: Boolean(row.enabled),
        chunkCount: Number(row.chunk_count),
        updatedAt: row.updated_at instanceof Date ? row.updated_at.toISOString() : String(row.updated_at)
      }));
    });
  }

  public async clear(): Promise<void> {
    await this.initialize();
    await this.withRetry("vector index clear", async () => {
      await this.pool.query(`TRUNCATE TABLE ${TABLE_NAME}`);
    });
  }

  public async close(): Promise<void> {
    await this.pool.end();
  }

  public async health(): Promise<void> {
    await this.initialize();
    await this.withRetry("vector database health check", async () => {
      await this.pool.query("SELECT 1");
    });
  }

  /** Encodes a finite vector for PostgreSQL's vector input format. */
  public static toPgVectorLiteral(vector: number[], dimensions: number): string {
    if (vector.length !== dimensions || vector.some((value) => !Number.isFinite(value))) {
      throw new VectorStoreError(`embedding must contain exactly ${dimensions} finite numbers`);
    }
    return `[${vector.join(",")}]`;
  }

  private poolConfig(options: PgVectorStoreOptions): PoolConfig {
    return {
      connectionString: options.connectionString,
      max: Math.max(1, options.poolMax ?? 10),
      idleTimeoutMillis: Math.max(0, options.idleTimeoutMs ?? 30_000),
      connectionTimeoutMillis: Math.max(100, options.connectionTimeoutMs ?? 5_000),
      statement_timeout: Math.max(100, options.statementTimeoutMs ?? 15_000)
    };
  }

  private async runMigrations(): Promise<void> {
    await this.pool.query("CREATE EXTENSION IF NOT EXISTS vector");
    await this.pool.query(
      `CREATE TABLE IF NOT EXISTS ${TABLE_NAME} (
          id TEXT PRIMARY KEY,
          document_id TEXT NOT NULL,
          chunk_id TEXT NOT NULL,
          content TEXT NOT NULL,
          embedding vector(${this.dimensions}) NOT NULL,
          metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
          enabled BOOLEAN NOT NULL DEFAULT TRUE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
          CONSTRAINT uk_knowledge_chunks_document_chunk UNIQUE (document_id, chunk_id)
      )`
    );
    await this.pool.query(
      `CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_embedding_hnsw
         ON ${TABLE_NAME} USING hnsw (embedding vector_cosine_ops)
         WITH (m = 16, ef_construction = 64)`
    );
    await this.pool.query(
      `CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_metadata
         ON ${TABLE_NAME} USING gin (metadata)`
    );
    await this.pool.query(
      `CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_dataset_domain
         ON ${TABLE_NAME} ((metadata->>'datasetId'), (metadata->>'knowledgeDomain'))
         WHERE enabled = TRUE`
    );
    const result = await this.pool.query<{ type_name: string }>(
      `SELECT format_type(a.atttypid, a.atttypmod) AS type_name
         FROM pg_attribute a
         JOIN pg_class c ON c.oid = a.attrelid
        WHERE c.relname = $1
          AND c.relnamespace = current_schema()::regnamespace
          AND a.attname = 'embedding'
          AND a.attnum > 0
          AND NOT a.attisdropped`,
      [TABLE_NAME]
    );
    const actualType = result.rows[0]?.type_name;
    if (actualType !== `vector(${this.dimensions})`) {
      throw new VectorStoreError(
        `pgvector dimension mismatch: configured=${this.dimensions}, database=${actualType ?? "missing"}`
      );
    }
  }

  private buildSearchPredicate(filter: VectorSearchFilter | undefined): { where: string[]; values: unknown[] } {
    const where = ["enabled = TRUE"];
    const values: unknown[] = [];
    if (filter?.datasetId !== undefined) {
      values.push(filter.datasetId);
      where.push(`metadata->>'datasetId' = $${values.length + 1}`);
    }
    if (filter?.knowledgeDomain !== undefined) {
      values.push(filter.knowledgeDomain);
      where.push(`metadata->>'knowledgeDomain' = $${values.length + 1}`);
    }
    if (filter?.chunkKind !== undefined) {
      values.push(filter.chunkKind);
      where.push(`metadata->>'chunkKind' = $${values.length + 1}`);
    }
    if (filter?.roles !== undefined && filter.roles.length > 0) {
      const roles = [...new Set(filter.roles.map((role) => role.trim().toUpperCase()).filter(Boolean))];
      values.push(roles);
      where.push(
        `string_to_array(upper(COALESCE(metadata->>'allowedRoles', 'PUBLIC')), ',')
         && (ARRAY['PUBLIC']::text[] || $${values.length + 1}::text[])`
      );
    }
    if (filter?.excludeExpired === true) {
      where.push("(NULLIF(metadata->>'expiresAt', '') IS NULL OR (metadata->>'expiresAt')::timestamptz > CURRENT_TIMESTAMP)");
    }
    return { where, values };
  }

  private validateEmbedding(vector: number[]): string {
    return PgVectorStore.toPgVectorLiteral(vector, this.dimensions);
  }

  private readMetadata(row: ChunkRow): Record<string, string> {
    let metadata: Record<string, unknown>;
    if (typeof row.metadata === "string") {
      try {
        metadata = JSON.parse(row.metadata) as Record<string, unknown>;
      } catch (error) {
        throw new VectorStoreError(`invalid metadata for vector ${row.id}`, { cause: error });
      }
    } else {
      metadata = row.metadata ?? {};
    }
    const cleaned: Record<string, string> = {};
    for (const [key, value] of Object.entries(metadata)) {
      if (value !== null && value !== undefined) cleaned[key] = String(value);
    }
    cleaned.enabled = String(row.enabled);
    cleaned.docId ??= row.document_id;
    return cleaned;
  }

  private readDistance(distance: number | string | null | undefined): number | null {
    if (distance === null || distance === undefined) return null;
    const parsed = typeof distance === "number" ? distance : Number(distance);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private async withRetry<T>(_operation: string, action: () => Promise<T>): Promise<T> {
    let lastFailure: unknown;
    for (let attempt = 1; attempt <= this.retryAttempts; attempt += 1) {
      try {
        return await action();
      } catch (error) {
        lastFailure = error;
        if (!this.isRetryable(error) || attempt === this.retryAttempts) throw error;
        await new Promise<void>((resolve) => setTimeout(resolve, this.retryDelayMs * attempt));
      }
    }
    throw lastFailure;
  }

  private isRetryable(error: unknown): boolean {
    if (!(error instanceof Error)) return false;
    const code = (error as Error & { code?: unknown }).code;
    return typeof code === "string"
      && (RETRYABLE_SQL_STATES.has(code) || code.startsWith("08") || RETRYABLE_DRIVER_CODES.has(code));
  }
}

export default PgVectorStore;
