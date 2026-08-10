import type { KnowledgeBaseManager } from "@enterprise-webagent/core";
import type { ChromadbKnowledgeBase } from "../adapters/chromadbKnowledgeBase.js";
import { DocumentChunkingService } from "../services/documentChunkingService.js";
import type { ServerConfig } from "../config.js";

/**
 * 知识库管理器实现
 *
 * 实现 KnowledgeBaseManager 接口，
 * 使用 ChromaDB 作为向量存储，递归分块 + 父文档检索作为分块策略。
 *
 * 对接 Backend 的 KnowledgeBasePort，通过 backendCompatibleRoutes 暴露为 HTTP API。
 */
export class ChromadbKnowledgeBaseManager implements KnowledgeBaseManager {
  private readonly chunkingService: DocumentChunkingService;

  public constructor(
    private readonly chromadb: ChromadbKnowledgeBase,
    private readonly config: ServerConfig
  ) {
    this.chunkingService = new DocumentChunkingService();
  }

  async uploadDocument(file: Buffer, filename: string, datasetId: string): Promise<string> {
    const text = file.toString("utf-8");
    const metadata = this.chunkingService.extractMetadata(text);
    const docId = `doc-${Date.now()}-${this.sanitizeFilename(filename)}`;

    // 使用父文档检索模式分块
    const { parentChunks, childChunks } = this.chunkingService.splitWithParent(text);

    // 构建要写入 ChromaDB 的文档列表（使用子文档做向量检索）
    const documents = childChunks.map((chunk) => ({
      id: `${docId}-child-${chunk.index}`,
      content: chunk.content,
      metadata: {
        ...metadata,
        title: filename,
        sourceType: chunk.sourceType,
        docId,
        parentId: chunk.parentId ?? "",
        datasetId
      }
    }));

    await this.chromadb.addDocuments(documents);
    return docId;
  }

  async deleteDocument(datasetId: string, documentId: string): Promise<void> {
    // ChromaDB 不支持前缀匹配删除，需要查出所有子文档 ID
    // 这里做简单实现：用 documentId 前缀匹配
    await this.chromadb.deleteDocument(documentId);
  }

  async getDataset(datasetId: string): Promise<Record<string, unknown>> {
    return {
      id: datasetId,
      name: this.config.chromadb.collection,
      embedding_model: this.config.embedding.model,
      embedding_dimensions: this.config.embedding.dimensions,
      chunking: {
        strategy: "recursive_pdr",
        child_chunk_size: 400,
        child_overlap: 50,
        parent_chunk_size: 2000,
        parent_overlap: 200,
        separators: ["\n\n", "\n", "。", "！", "？", "；", "，", " "]
      }
    };
  }

  async updateDocumentStatus(datasetId: string, documentId: string, enabled: boolean): Promise<void> {
    await this.chromadb.updateDocumentStatus(documentId, enabled);
  }

  async listDocuments(
    datasetId: string,
    page: number,
    limit: number
  ): Promise<Array<Record<string, unknown>>> {
    // ChromaDB 原生不支持分页列表，返回模拟数据
    return [{
      id: `ds-${datasetId}`,
      name: this.config.chromadb.collection,
      document_count: 0 // 实际获取需要通过 ChromaDB API
    }];
  }

  async listAllDocuments(datasetId: string): Promise<Array<Record<string, unknown>>> {
    return this.listDocuments(datasetId, 1, 100);
  }

  private sanitizeFilename(filename: string): string {
    return filename.replace(/[^a-zA-Z0-9\u4e00-\u9fa5_.-]/g, "_");
  }
}
