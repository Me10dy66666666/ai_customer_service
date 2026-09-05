/**
 * 向量存储接缝。生产实现使用 PostgreSQL + pgvector，测试实现可以使用内存索引。
 * 检索默认只返回启用（enabled !== "false"）的文档。
 */
export interface StoredDocument {
  id: string;
  content: string;
  metadata: Record<string, string>;
  /** Stable document identity used by delete, status, and reindex operations. */
  documentId?: string | undefined;
  /** Stable chunk identity unique within documentId. */
  chunkId?: string | undefined;
}

export interface VectorSearchResult {
  id: string;
  content: string;
  metadata: Record<string, string>;
  distance: number | null;
}

export interface VectorSearchFilter {
  datasetId?: string | undefined;
  knowledgeDomain?: string | undefined;
  roles?: readonly string[] | undefined;
  chunkKind?: string | undefined;
  excludeExpired?: boolean | undefined;
}

export interface VectorDocumentSummary {
  documentId: string;
  title: string;
  enabled: boolean;
  chunkCount: number;
  updatedAt: string;
}

export interface VectorStore {
  search(
    queryEmbedding: number[],
    limit: number,
    filter?: VectorSearchFilter
  ): Promise<VectorSearchResult[]>;
  add(documents: StoredDocument[], embeddings: number[][]): Promise<void>;
  /** 按父块 ID 回查完整上下文，向量库只是可重建投影。 */
  getByIds(ids: string[]): Promise<StoredDocument[]>;
  /** 删除某文档的全部块。 */
  deleteByDocument(documentId: string): Promise<void>;
  /** 启用/停用某文档的全部块。 */
  setEnabled(documentId: string, enabled: boolean): Promise<void>;
  /** Optional paginated document projection for management APIs. */
  listDocuments?(datasetId: string, page: number, limit: number): Promise<VectorDocumentSummary[]>;
  /** 清空向量库（用于重建索引）。 */
  clear(): Promise<void>;
  /** Optional startup hook for adapters that own external resources. */
  initialize?(): Promise<void>;
  /** Optional shutdown hook for adapters that own external resources. */
  close?(): Promise<void>;
  /** Optional readiness probe for adapters backed by an external service. */
  health?(): Promise<void>;
}
