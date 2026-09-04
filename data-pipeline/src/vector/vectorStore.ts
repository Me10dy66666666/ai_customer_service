/**
 * 向量存储接缝：支持 ChromaDB(MVP)/Milvus/Qdrant/pgvector 等实现切换。
 * 检索仅返回启用（enabled !== "false"）的文档。
 */
export interface StoredDocument {
  id: string;
  content: string;
  metadata: Record<string, string>;
}

export interface VectorSearchResult {
  id: string;
  content: string;
  metadata: Record<string, string>;
  distance: number | null;
}

export interface VectorStore {
  search(queryEmbedding: number[], limit: number): Promise<VectorSearchResult[]>;
  add(documents: StoredDocument[], embeddings: number[][]): Promise<void>;
  /** 删除某文档的全部块。 */
  deleteByDocument(documentId: string): Promise<void>;
  /** 启用/停用某文档的全部块。 */
  setEnabled(documentId: string, enabled: boolean): Promise<void>;
  /** 清空向量库（用于重建索引）。 */
  clear(): Promise<void>;
}
