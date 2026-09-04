import { describe, it, expect } from "vitest";
import { DocumentChunkingService } from "../chunking/documentChunkingService.js";
import type { EmbeddingClient } from "../embedding/embeddingClient.js";
import type { VectorStore, VectorSearchResult, StoredDocument } from "../vector/vectorStore.js";
import { KnowledgeBaseManager } from "./knowledgeBaseManager.js";

/** 确定性伪 Embedding：按字符编码生成 4 维向量，便于无外部服务测试闭环。 */
class FakeEmbeddingClient implements EmbeddingClient {
  public async embedTexts(texts: string[]): Promise<number[][]> {
    return texts.map((text) => {
      let sum = 0;
      for (const char of text) {
        sum += char.charCodeAt(0);
      }
      return [sum % 10, (sum * 2) % 10, (sum * 3) % 10, 1];
    });
  }
}

/** 内存向量库：用余弦相似度做最近邻检索，并遵循 enabled 过滤语义。 */
class InMemoryVectorStore implements VectorStore {
  private items: Array<StoredDocument & { embedding: number[] }> = [];

  public async search(queryEmbedding: number[], limit: number): Promise<VectorSearchResult[]> {
    return this.items
      .filter((item) => item.metadata.enabled !== "false")
      .map((item) => ({ item, score: this.cosine(queryEmbedding, item.embedding) }))
      .sort((a, b) => b.score - a.score)
      .slice(0, limit)
      .map(({ item }) => ({
        id: item.id,
        content: item.content,
        metadata: item.metadata,
        distance: 0
      }));
  }

  public async add(documents: StoredDocument[], embeddings: number[][]): Promise<void> {
    documents.forEach((doc, index) => {
      this.items.push({ ...doc, embedding: embeddings[index] ?? [] });
    });
  }

  public async deleteByDocument(documentId: string): Promise<void> {
    this.items = this.items.filter((item) => item.metadata.docId !== documentId);
  }

  public async setEnabled(documentId: string, enabled: boolean): Promise<void> {
    this.items = this.items.map((item) =>
      item.metadata.docId === documentId
        ? { ...item, metadata: { ...item.metadata, enabled: String(enabled) } }
        : item
    );
  }

  public async clear(): Promise<void> {
    this.items = [];
  }

  private cosine(a: number[], b: number[]): number {
    let dot = 0;
    for (let i = 0; i < a.length; i++) {
      dot += (a[i] ?? 0) * (b[i] ?? 0);
    }
    return dot;
  }
}

function buildManager(): KnowledgeBaseManager {
  return new KnowledgeBaseManager({
    chunking: new DocumentChunkingService(),
    embedding: new FakeEmbeddingClient(),
    vectorStore: new InMemoryVectorStore()
  });
}

describe("KnowledgeBaseManager 闭环", () => {
  it("ingest 后 search 能命中语义相近内容并带来源标题", async () => {
    const manager = buildManager();

    await manager.ingest({
      filename: "退货政策.md",
      content: "# 退货政策\n\n用户可在七天内申请无理由退货。",
      datasetId: "default"
    });

    const sources = await manager.search({ query: "退货政策", limit: 5 });
    expect(sources.length).toBeGreaterThan(0);
    expect(sources[0]?.title).toBe("退货政策.md");
  });

  it("ingest 返回 documentId 与 chunkCount", async () => {
    const manager = buildManager();

    const result = await manager.ingest({
      filename: "a.md",
      content: "内容。".repeat(100),
      datasetId: "default"
    });

    expect(result.documentId).toContain("a.md");
    expect(result.chunkCount).toBeGreaterThan(0);
  });

  it("停用文档后检索不再命中", async () => {
    const manager = buildManager();
    const { documentId } = await manager.ingest({
      filename: "政策.md",
      content: "# 政策\n退货七天内。",
      datasetId: "default"
    });

    await manager.updateDocumentStatus(documentId, false);

    const sources = await manager.search({ query: "退货", limit: 5 });
    expect(sources).toHaveLength(0);
  });

  it("删除文档后检索不再命中", async () => {
    const manager = buildManager();
    const { documentId } = await manager.ingest({
      filename: "政策.md",
      content: "# 政策\n退货七天内。",
      datasetId: "default"
    });

    await manager.deleteDocument(documentId);

    const sources = await manager.search({ query: "退货", limit: 5 });
    expect(sources).toHaveLength(0);
  });

  it("reindex 清空后仅保留给定文档", async () => {
    const manager = buildManager();
    await manager.ingest({
      filename: "旧.md",
      content: "旧内容。",
      datasetId: "default"
    });

    const count = await manager.reindex([
      { filename: "新.md", content: "新内容。", datasetId: "default" }
    ]);

    expect(count).toBe(1);
    const sources = await manager.search({ query: "新内容", limit: 5 });
    expect(sources).toHaveLength(1);
    expect(sources[0]?.title).toBe("新.md");
  });
});
