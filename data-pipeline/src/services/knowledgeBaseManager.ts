import { createHash } from "node:crypto";
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
  documentId?: string | undefined;
  documentVersion?: number | undefined;
  embeddingModel?: string | undefined;
  knowledgeDomain?: string | undefined;
  allowedRoles?: string[] | undefined;
  expiresAt?: string | undefined;
  metadata?: Record<string, string> | undefined;
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
    const {
      filename,
      content,
      datasetId,
      documentVersion = 1,
      embeddingModel = "configured",
      knowledgeDomain = "customer-service",
      allowedRoles = ["PUBLIC"],
      metadata: inputMetadata = {}
    } = input;
    const extractedMetadata = this.deps.chunking.extractMetadata(content);
    const sourceHash = createHash("sha256").update(content, "utf8").digest("hex");
    const documentId = input.documentId
      ?? `doc-${Date.now()}-${this.sanitizeFilename(filename)}-${sourceHash.slice(0, 12)}`;

    // 父文档检索模式：父块必须落库，子块只负责召回，随后回查父块生成上下文。
    const { parentChunks, childChunks } = this.deps.chunking.splitWithParent(content);
    const allowedRoleMetadata = this.normalizeRoles(allowedRoles);
    const customMetadata = Object.fromEntries(
      Object.entries(inputMetadata).filter(([key]) => ![
        "title",
        "datasetId",
        "docId",
        "documentVersion",
        "sourceHash",
        "embeddingModel",
        "knowledgeDomain",
        "allowedRoles",
        "enabled",
        "expiresAt",
        "chunkKind",
        "chunkIndex",
        "parentId"
      ].includes(key))
    );
    const commonMetadata = {
      ...extractedMetadata,
      ...customMetadata,
      title: filename,
      datasetId,
      docId: documentId,
      documentVersion: String(documentVersion),
      sourceHash,
      embeddingModel,
      knowledgeDomain,
      allowedRoles: allowedRoleMetadata,
      enabled: "true",
      ...(input.expiresAt ? { expiresAt: input.expiresAt } : {})
    };

    const parentDocuments: StoredDocument[] = parentChunks.map((chunk) => ({
      id: `${documentId}-parent-${chunk.index}`,
      content: chunk.content,
      documentId,
      chunkId: `parent-${chunk.index}`,
      metadata: {
        ...commonMetadata,
        chunkKind: "parent",
        chunkIndex: String(chunk.index),
        parentId: ""
      }
    }));

    const childDocuments: StoredDocument[] = childChunks.map((chunk) => ({
      id: `${documentId}-child-${chunk.index}`,
      content: chunk.content,
      documentId,
      chunkId: `child-${chunk.index}`,
      metadata: {
        ...commonMetadata,
        sourceType: chunk.sourceType,
        chunkKind: "child",
        chunkIndex: String(chunk.index),
        parentId: chunk.parentId ? `${documentId}-parent-${this.chunkIndex(chunk.parentId)}` : ""
      }
    }));
    const documents = [...parentDocuments, ...childDocuments];

    const embeddings = await this.deps.embedding.embedTexts(documents.map((d) => d.content));
    await this.deps.vectorStore.add(documents, embeddings);

    return { documentId, chunkCount: childDocuments.length };
  }

  /**
   * 语义检索：查询向量化 → 向量库检索 → 映射为知识来源。
   */
  public async search(input: {
    query: string;
    limit: number;
    datasetId?: string | undefined;
    roles?: string[] | undefined;
    knowledgeDomain?: string | undefined;
  }): Promise<KnowledgeSource[]> {
    const [queryEmbedding] = await this.deps.embedding.embedTexts([input.query]);
    if (!queryEmbedding) return [];

    // 先扩大候选集，再在服务端过滤 ACL/版本/过期状态，避免把客户端过滤当成安全边界。
    const candidateLimit = Math.min(Math.max(input.limit * 5, input.limit), 50);
    const results = await this.deps.vectorStore.search(queryEmbedding, candidateLimit, {
      datasetId: input.datasetId,
      knowledgeDomain: input.knowledgeDomain,
      roles: input.roles,
      chunkKind: "child",
      excludeExpired: true
    });
    const visibleResults = results
      .filter((result) => result.metadata.chunkKind !== "parent")
      .filter((result) => !input.datasetId || result.metadata.datasetId === input.datasetId)
      .filter((result) => !input.knowledgeDomain || result.metadata.knowledgeDomain === input.knowledgeDomain)
      .filter((result) => this.isVisibleToRoles(result.metadata.allowedRoles, input.roles ?? []))
      .filter((result) => this.isNotExpired(result.metadata.expiresAt))
      .slice(0, input.limit);

    const parentIds = visibleResults
      .map((result) => result.metadata.parentId)
      .filter((parentId): parentId is string => Boolean(parentId));
    const parents = await this.deps.vectorStore.getByIds(parentIds);
    const parentById = new Map(parents.map((parent) => [parent.id, parent]));

    return visibleResults.map((r) => {
      const sourceType = r.metadata.sourceType as KnowledgeSource["sourceType"] | undefined;
      const parent = r.metadata.parentId ? parentById.get(r.metadata.parentId) : undefined;
      const citationMetadata = {
        ...r.metadata,
        citationId: r.id,
        parentRetrieved: String(Boolean(parent))
      };
      return {
        id: r.id,
        title: r.metadata.title ?? "未命名文档",
        excerpt: this.buildExcerpt(parent?.content ?? r.content, 500),
        sourceType: sourceType ?? "knowledge_base",
        metadata: citationMetadata
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

  public async listDocuments(datasetId: string, page: number, limit: number): Promise<Array<Record<string, unknown>>> {
    const summaries = await this.deps.vectorStore.listDocuments?.(datasetId, page, limit);
    return (summaries ?? []).map((summary) => ({
      id: summary.documentId,
      name: summary.title,
      document_count: summary.chunkCount,
      enabled: summary.enabled,
      updated_at: summary.updatedAt
    }));
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

  private normalizeRoles(roles: string[]): string {
    const normalized = [...new Set(roles.map((role) => role.trim().toUpperCase()).filter(Boolean))];
    return normalized.length > 0 ? normalized.join(",") : "PUBLIC";
  }

  private sanitizeFilename(filename: string): string {
    return filename.replace(/[^a-zA-Z0-9\u4e00-\u9fa5_.-]/g, "_");
  }

  private isVisibleToRoles(allowedRoles: string | undefined, roles: string[]): boolean {
    const allowed = (allowedRoles ?? "PUBLIC").split(",").map((role) => role.trim().toUpperCase());
    if (allowed.includes("PUBLIC") || allowed.includes("*")) return true;
    const principalRoles = new Set(roles.map((role) => role.trim().toUpperCase()));
    return allowed.some((role) => principalRoles.has(role));
  }

  private isNotExpired(expiresAt: string | undefined): boolean {
    if (!expiresAt) return true;
    const expiry = Date.parse(expiresAt);
    return !Number.isNaN(expiry) && expiry > Date.now();
  }

  private chunkIndex(parentId: string): string {
    const match = parentId.match(/(\d+)$/);
    return match?.[1] ?? "0";
  }
}
