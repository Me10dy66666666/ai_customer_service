import type { DocumentChunkingService } from "../chunking/documentChunkingService.js";
import type { EmbeddingClient } from "../embedding/embeddingClient.js";
import type { VectorStore, StoredDocument } from "../vector/vectorStore.js";
import type { KnowledgeSource } from "../types.js";

export interface KnowledgeBaseManagerDependencies {
  chunking: DocumentChunkingService;
  embedding: EmbeddingClient;
  vectorStore: VectorStore;
}

export interface IngestInput {
  filename: string;
  content: string;
  datasetId: string;
}

/**
 * 知识库管理门面：编排「切块 → 向量化 → 入库」与「查询 → 向量化 → 检索」，
 * 以及文档生命周期（删除、启停、重建索引）。
 */
export class KnowledgeBaseManager {
  public constructor(private readonly deps: KnowledgeBaseManagerDependencies) {}

  /**
   * 入库：分块 → 向量化 → 写入向量库，返回文档 ID 与块数量。
   */
  public async ingest(input: IngestInput): Promise<{ documentId: string; chunkCount: number }> {
    const { filename, content, datasetId } = input;
    const metadata = this.deps.chunking.extractMetadata(content);
    const documentId = `doc-${Date.now()}-${this.sanitizeFilename(filename)}`;

    // 父文档检索模式：用子文档做向量检索
    const { childChunks } = this.deps.chunking.splitWithParent(content);

    const documents: StoredDocument[] = childChunks.map((chunk) => ({
      id: `${documentId}-child-${chunk.index}`,
      content: chunk.content,
      metadata: {
        ...metadata,
        title: filename,
        sourceType: chunk.sourceType,
        docId: documentId,
        parentId: chunk.parentId ?? "",
        datasetId,
        enabled: "true"
      }
    }));

    const embeddings = await this.deps.embedding.embedTexts(documents.map((d) => d.content));
    await this.deps.vectorStore.add(documents, embeddings);

    return { documentId, chunkCount: documents.length };
  }

  /**
   * 语义检索：查询向量化 → 向量库检索 → 映射为知识来源。
   */
  public async search(input: { query: string; limit: number }): Promise<KnowledgeSource[]> {
    const [queryEmbedding] = await this.deps.embedding.embedTexts([input.query]);
    if (!queryEmbedding) return [];

    const results = await this.deps.vectorStore.search(queryEmbedding, input.limit);

    return results.map((r) => {
      const sourceType = r.metadata.sourceType as KnowledgeSource["sourceType"] | undefined;
      return {
        id: r.id,
        title: r.metadata.title ?? "未命名文档",
        excerpt: this.buildExcerpt(r.content, 500),
        sourceType: sourceType ?? "knowledge_base",
        metadata: r.metadata
      };
    });
  }

  /**
   * 删除文档及其全部块。
   */
  public async deleteDocument(documentId: string): Promise<void> {
    await this.deps.vectorStore.deleteByDocument(documentId);
  }

  /**
   * 启用/停用文档（停用后不再参与检索）。
   */
  public async updateDocumentStatus(documentId: string, enabled: boolean): Promise<void> {
    await this.deps.vectorStore.setEnabled(documentId, enabled);
  }

  /**
   * 重建索引：清空向量库后按给定文档重新入库。
   */
  public async reindex(documents: IngestInput[]): Promise<number> {
    await this.deps.vectorStore.clear();
    for (const doc of documents) {
      await this.ingest(doc);
    }
    return documents.length;
  }

  private buildExcerpt(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return text.slice(0, maxLength) + "...";
  }

  private sanitizeFilename(filename: string): string {
    return filename.replace(/[^a-zA-Z0-9\u4e00-\u9fa5_.-]/g, "_");
  }
}
