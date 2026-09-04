import { ChromaClient } from "chromadb";
import type { VectorStore, VectorSearchResult, StoredDocument } from "./vectorStore.js";

export interface ChromaVectorStoreOptions {
  url: string;
  collection: string;
}

/**
 * ChromaDB 向量存储实现（MVP 默认）。
 * 使用余弦距离空间（hnsw:space=cosine）；文档按 metadata 的 docId/enabled 管理。
 */
export class ChromaVectorStore implements VectorStore {
  private readonly client: ChromaClient;
  private readonly collectionName: string;

  public constructor(options: ChromaVectorStoreOptions) {
    this.collectionName = options.collection;
    this.client = new ChromaClient({ path: options.url });
  }

  public async search(queryEmbedding: number[], limit: number): Promise<VectorSearchResult[]> {
    const collection = await this.getOrCreateCollection();
    const results = await collection.query({
      queryEmbeddings: [queryEmbedding],
      nResults: limit,
      where: { enabled: "true" },
      include: ["metadatas", "documents", "distances"]
    });

    if (!results.ids || results.ids.length === 0 || !results.ids[0]) {
      return [];
    }

    return results.ids[0].map((id, idx) => {
      const metadata = results.metadatas?.[0]?.[idx] ?? {};
      const document = results.documents?.[0]?.[idx] ?? "";
      const distance = results.distances?.[0]?.[idx] ?? null;

      return {
        id: id ?? `doc-${idx}`,
        content: document,
        metadata: this.cleanMetadata(metadata),
        distance
      };
    });
  }

  public async add(documents: StoredDocument[], embeddings: number[][]): Promise<void> {
    const collection = await this.getOrCreateCollection();
    await collection.add({
      ids: documents.map((d) => d.id),
      embeddings,
      documents: documents.map((d) => d.content),
      metadatas: documents.map((d) => d.metadata)
    });
  }

  public async deleteByDocument(documentId: string): Promise<void> {
    try {
      const collection = await this.getOrCreateCollection();
      await collection.delete({ where: { docId: documentId } });
    } catch {
      // 忽略不存在的文档
    }
  }

  public async setEnabled(documentId: string, enabled: boolean): Promise<void> {
    try {
      const collection = await this.getOrCreateCollection();
      const existing = await collection.get({ where: { docId: documentId } });
      const ids = existing.ids;
      if (ids.length === 0) return;
      await collection.update({
        ids,
        metadatas: ids.map(() => ({ enabled: String(enabled) }))
      });
    } catch {
      // 忽略错误
    }
  }

  public async clear(): Promise<void> {
    try {
      await this.client.deleteCollection({ name: this.collectionName });
    } catch {
      // 集合可能不存在
    }
  }

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
