import type { KnowledgeBaseManager, KnowledgeRetriever } from "@enterprise-webagent/core";
import type { KnowledgeSource } from "@enterprise-webagent/shared";

interface DataPipelineConfig {
  baseUrl: string;
  serviceToken: string;
  timeoutMs: number;
  retryAttempts: number;
  retryDelayMs: number;
  embeddingModel: string;
  embeddingDimensions: number;
}

interface SearchResponse {
  sources?: Array<{
    id?: unknown;
    title?: unknown;
    excerpt?: unknown;
    sourceType?: unknown;
    metadata?: unknown;
  }>;
}

interface IngestResponse {
  documentId?: unknown;
}

/** Error boundary for the HTTP seam between the Agent runtime and data-pipeline. */
export class DataPipelineError extends Error {
  public constructor(message: string, public readonly statusCode?: number, options?: { cause?: unknown }) {
    super(message, options);
    this.name = "DataPipelineError";
  }
}

/**
 * HTTP adapter for the storage-neutral KnowledgeRetriever/KnowledgeBaseManager
 * seams. Chunking, embedding, filtering, and vector SQL remain owned by
 * data-pipeline; the Agent runtime only sees citations and lifecycle results.
 */
export class DataPipelineKnowledgeBase implements KnowledgeRetriever, KnowledgeBaseManager {
  private readonly baseUrl: string;

  public constructor(private readonly config: DataPipelineConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, "");
  }

  public async search(input: {
    query: string;
    userType: number;
    limit: number;
    signal?: AbortSignal;
  }): Promise<KnowledgeSource[]> {
    const response = await this.request("/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query: input.query,
        limit: Math.min(Math.max(Math.trunc(input.limit), 1), 10),
        roles: input.userType > 0 ? ["PUBLIC", "USER"] : ["PUBLIC"]
      })
    }, input.signal);
    const payload = await this.readJson<SearchResponse>(response, "search");
    return (payload.sources ?? []).flatMap((source, index) => {
      const id = typeof source.id === "string" ? source.id : `source-${index}`;
      const title = typeof source.title === "string" ? source.title : "未命名文档";
      const excerpt = typeof source.excerpt === "string" ? source.excerpt : "";
      if (!excerpt) return [];
      return [{
        id,
        title,
        excerpt,
        sourceType: this.sourceType(source.sourceType),
        metadata: this.metadata(source.metadata)
      }];
    });
  }

  public async uploadDocument(file: Buffer, filename: string, datasetId: string): Promise<string> {
    const form = new FormData();
    form.append("file", new Blob([new Uint8Array(file)]), filename);
    const query = new URLSearchParams({ datasetId });
    // The multipart endpoint generates a document id server-side. Retrying after
    // a lost response could therefore create a duplicate logical document.
    const response = await this.request(`/ingest/file?${query.toString()}`, {
      method: "POST",
      body: form
    }, undefined, false);
    const payload = await this.readJson<IngestResponse>(response, "document upload");
    if (typeof payload.documentId !== "string" || payload.documentId.length === 0) {
      throw new DataPipelineError("data-pipeline returned no document id");
    }
    return payload.documentId;
  }

  public async deleteDocument(_datasetId: string, documentId: string): Promise<void> {
    const response = await this.request(`/documents/${encodeURIComponent(documentId)}`, { method: "DELETE" });
    await response.body?.cancel();
  }

  public async getDataset(datasetId: string): Promise<Record<string, unknown>> {
    return {
      id: datasetId,
      name: datasetId,
      vector_store: "postgresql+pgvector",
      embedding_model: this.config.embeddingModel,
      embedding_dimensions: this.config.embeddingDimensions,
      retrieval: "cosine_hnsw_with_metadata_acl"
    };
  }

  public async updateDocumentStatus(_datasetId: string, documentId: string, enabled: boolean): Promise<void> {
    const response = await this.request(`/documents/${encodeURIComponent(documentId)}/status`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled })
    });
    await response.body?.cancel();
  }

  public async listDocuments(
    datasetId: string,
    page: number,
    limit: number
  ): Promise<Array<Record<string, unknown>>> {
    const query = new URLSearchParams({
      datasetId,
      page: String(Math.max(1, Math.trunc(page))),
      limit: String(Math.min(Math.max(1, Math.trunc(limit)), 100))
    });
    const response = await this.request(`/documents?${query.toString()}`, { method: "GET" });
    const payload = await this.readJson<{ data?: unknown }>(response, "document listing");
    return Array.isArray(payload.data)
      ? payload.data.filter((item): item is Record<string, unknown> => this.isRecord(item))
      : [];
  }

  public async listAllDocuments(datasetId: string): Promise<Array<Record<string, unknown>>> {
    return this.listDocuments(datasetId, 1, 100);
  }

  private async request(
    path: string,
    init: RequestInit,
    signal?: AbortSignal,
    retryable = true
  ): Promise<Response> {
    const attempts = retryable ? Math.max(1, this.config.retryAttempts) : 1;
    let lastFailure: unknown;
    for (let attempt = 1; attempt <= attempts; attempt += 1) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), Math.max(100, this.config.timeoutMs));
      const abortExternal = () => controller.abort(signal?.reason);
      signal?.addEventListener("abort", abortExternal, { once: true });
      if (signal?.aborted) abortExternal();
      try {
        const response = await fetch(`${this.baseUrl}${path}`, {
          ...init,
          headers: {
            ...(init.headers ?? {}),
            ...(this.config.serviceToken ? { Authorization: `Bearer ${this.config.serviceToken}` } : {})
          },
          signal: controller.signal
        });
        if (response.ok) return response;
        const failure = new DataPipelineError(
          `data-pipeline ${path} failed: HTTP ${response.status}`,
          response.status
        );
        lastFailure = failure;
        if (!this.isRetryableStatus(response.status) || attempt === attempts) throw failure;
        await response.body?.cancel();
      } catch (error) {
        lastFailure = error;
        if (error instanceof DataPipelineError && error.statusCode !== undefined
            && !this.isRetryableStatus(error.statusCode)) {
          throw error;
        }
        if (signal?.aborted) throw error;
        if (attempt === attempts) {
          throw error instanceof DataPipelineError
            ? error
            : new DataPipelineError(`data-pipeline ${path} request failed`, undefined, { cause: error });
        }
      } finally {
        clearTimeout(timeout);
        signal?.removeEventListener("abort", abortExternal);
      }
      await new Promise<void>((resolve) => setTimeout(resolve, this.config.retryDelayMs * attempt));
    }
    throw new DataPipelineError(`data-pipeline ${path} request failed`, undefined, { cause: lastFailure });
  }

  private async readJson<T>(response: Response, operation: string): Promise<T> {
    try {
      return await response.json() as T;
    } catch (error) {
      throw new DataPipelineError(`data-pipeline ${operation} returned invalid JSON`, undefined, { cause: error });
    }
  }

  private sourceType(value: unknown): KnowledgeSource["sourceType"] {
    return value === "policy" || value === "faq" ? value : "knowledge_base";
  }

  private metadata(value: unknown): Record<string, string> {
    if (!this.isRecord(value)) return {};
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, item]) => item !== null && item !== undefined)
        .map(([key, item]) => [key, typeof item === "string" ? item : String(item)])
    );
  }

  private isRetryableStatus(status: number): boolean {
    return status === 408 || status === 429 || status >= 500;
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }
}
