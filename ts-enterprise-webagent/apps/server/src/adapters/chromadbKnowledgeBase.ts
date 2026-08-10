import { ChromaClient } from "chromadb";
import type { KnowledgeRetriever } from "@enterprise-webagent/core";
import type { KnowledgeSource } from "@enterprise-webagent/shared";

/**
 * ChromaDB 向量知识库适配器
 *
 * 实现 KnowledgeRetriever 接口，使用 ChromaDB 作为向量存储。
 * 支持 OpenAI 兼容的 Embedding API 进行文档向量化。
 *
 * 对应 Backend ElasticsearchKnowledgeSearchService 的职责，
 * 但使用向量语义检索替代全文关键词检索。
 */
export class ChromadbKnowledgeBase implements KnowledgeRetriever {
  private client: ChromaClient;
  private collectionName: string;
  private embeddingConfig: {
    apiKey: string;
    baseUrl: string;
    model: string;
    dimensions: number;
  };

  public constructor(options: {
    chromadbUrl: string;
    collection: string;
    embeddingApiKey: string;
    embeddingBaseUrl: string;
    embeddingModel: string;
    embeddingDimensions: number;
  }) {
    this.collectionName = options.collection;
    this.embeddingConfig = {
      apiKey: options.embeddingApiKey,
      baseUrl: options.embeddingBaseUrl,
      model: options.embeddingModel,
      dimensions: options.embeddingDimensions
    };
    this.client = new ChromaClient({ path: options.chromadbUrl });
  }

  // ============================================================
  // 公开方法
  // ============================================================

  /**
   * 向量检索知识
   */
  public async search(input: {
    query: string;
    userType: number;
    limit: number;
    signal?: AbortSignal;
  }): Promise<KnowledgeSource[]> {
    const { query, limit, signal } = input;

    try {
      const collection = await this.getOrCreateCollection();
      const queryEmbedding = await this.embedText(query, signal);

      const results = await collection.query({
        queryEmbeddings: [queryEmbedding],
        nResults: limit,
        include: ["metadatas", "documents", "distances"]
      });

      if (!results.ids || results.ids.length === 0 || !results.ids[0]) {
        return [];
      }

      return results.ids[0].map((id, idx) => {
        const metadata = results.metadatas?.[0]?.[idx] ?? {};
        const document = results.documents?.[0]?.[idx] ?? "";

        return {
          id: id ?? `doc-${idx}`,
          title: String(metadata.title ?? "未命名文档"),
          excerpt: this.buildExcerpt(document, 500),
          sourceType: (metadata.sourceType as KnowledgeSource["sourceType"]) ?? "knowledge_base",
          metadata: this.cleanMetadata(metadata)
        };
      });
    } catch (error) {
      console.error("ChromaDB search failed:", error);
      return [];
    }
  }

  /**
   * 添加文档到知识库
   */
  public async addDocuments(
    documents: Array<{
      id: string;
      content: string;
      metadata: Record<string, string>;
    }>
  ): Promise<void> {
    const collection = await this.getOrCreateCollection();
    const texts = documents.map((d) => d.content);
    const embeddings = await this.embedBatch(texts);

    await collection.add({
      ids: documents.map((d) => d.id),
      embeddings,
      documents: texts,
      metadatas: documents.map((d) => d.metadata)
    });
  }

  /**
   * 删除文档
   */
  public async deleteDocument(documentId: string): Promise<void> {
    try {
      const collection = await this.getOrCreateCollection();
      await collection.delete({ ids: [documentId] });
    } catch {
      // 忽略不存在的文档
    }
  }

  /**
   * 更新文档状态（仅 metadata 标记，不清除向量）
   */
  public async updateDocumentStatus(
    documentId: string,
    enabled: boolean
  ): Promise<void> {
    try {
      const collection = await this.getOrCreateCollection();
      await collection.update({
        ids: [documentId],
        metadatas: [{ enabled: String(enabled) }]
      });
    } catch {
      // 忽略错误
    }
  }

  /**
   * 重建索引：清空并重新写入所有文档
   */
  public async reindex(
    documents: Array<{
      id: string;
      content: string;
      metadata: Record<string, string>;
    }>
  ): Promise<void> {
    try {
      await this.client.deleteCollection({ name: this.collectionName });
    } catch {
      // 集合可能不存在
    }
    if (documents.length > 0) {
      await this.addDocuments(documents);
    }
  }

  // ============================================================
  // 私有方法
  // ============================================================

  private async getOrCreateCollection() {
    try {
      return await this.client.getCollection({ name: this.collectionName });
    } catch {
      return await this.client.createCollection({
        name: this.collectionName,
        metadata: { "hnsw:space": "cosine" }
      });
    }
  }

  /**
   * 调用 Embedding API 将单条文本向量化
   */
  private async embedText(text: string, signal?: AbortSignal): Promise<number[]> {
    const url = `${this.embeddingConfig.baseUrl}/embeddings`;
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${this.embeddingConfig.apiKey}`
      },
      body: JSON.stringify({
        model: this.embeddingConfig.model,
        input: text,
        dimensions: this.embeddingConfig.dimensions
      }),
      signal: signal ?? null
    });

    if (!response.ok) {
      throw new Error(`Embedding API error: HTTP ${response.status}`);
    }

    const data = await response.json() as {
      data: Array<{ embedding: number[] }>;
    };

    return data.data[0]?.embedding ?? [];
  }

  /**
   * 批量向量化（减少 API 调用次数）
   */
  private async embedBatch(texts: string[]): Promise<number[][]> {
    if (texts.length === 0) return [];

    // 分批处理，每批最多 20 条
    const batchSize = 20;
    const results: number[][] = [];

    for (let i = 0; i < texts.length; i += batchSize) {
      const batch = texts.slice(i, i + batchSize);
      const url = `${this.embeddingConfig.baseUrl}/embeddings`;

      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.embeddingConfig.apiKey}`
        },
        body: JSON.stringify({
          model: this.embeddingConfig.model,
          input: batch,
          dimensions: this.embeddingConfig.dimensions
        })
      });

      if (!response.ok) {
        throw new Error(`Embedding batch failed: HTTP ${response.status}`);
      }

      const data = await response.json() as {
        data: Array<{ embedding: number[] }>;
      };

      for (const item of data.data) {
        results.push(item.embedding);
      }
    }

    return results;
  }

  private buildExcerpt(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return text.slice(0, maxLength) + "...";
  }

  private cleanMetadata(metadata: Record<string, unknown>): Record<string, string> {
    const cleaned: Record<string, string> = {};
    for (const [key, value] of Object.entries(metadata)) {
      if (value != null) {
        cleaned[key] = String(value);
      }
    }
    return cleaned;
  }
}
