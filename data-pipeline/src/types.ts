import { z } from "zod";

// ============================================================
// 数据处理服务对外契约（HTTP API）
// ============================================================

export const ingestRequestSchema = z.object({
  filename: z.string().trim().min(1).max(255),
  content: z.string().min(1),
  datasetId: z.string().trim().max(64).default("default"),
  documentId: z.string().trim().max(128).optional(),
  documentVersion: z.number().int().min(1).max(1_000_000).default(1),
  embeddingModel: z.string().trim().max(128).default("configured"),
  knowledgeDomain: z.string().trim().max(128).default("customer-service"),
  allowedRoles: z.array(z.string().trim().min(1).max(64)).max(16).default(["PUBLIC"]),
  expiresAt: z.string().datetime({ offset: true }).optional()
});

export const ingestResponseSchema = z.object({
  documentId: z.string(),
  chunkCount: z.number().int().nonnegative()
});

export const searchRequestSchema = z.object({
  query: z.string().trim().min(1).max(2_000),
  limit: z.number().int().min(1).max(50).optional(),
  datasetId: z.string().trim().max(64).optional(),
  knowledgeDomain: z.string().trim().max(128).optional(),
  roles: z.array(z.string().trim().min(1).max(64)).max(16).optional()
});

export const knowledgeSourceSchema = z.object({
  id: z.string(),
  title: z.string(),
  excerpt: z.string(),
  sourceType: z.enum(["knowledge_base", "policy", "faq"]),
  metadata: z.record(z.string(), z.string()).default({})
});

export const searchResponseSchema = z.object({
  sources: z.array(knowledgeSourceSchema)
});

export const documentStatusRequestSchema = z.object({
  enabled: z.boolean()
});

export const reindexRequestSchema = z.object({
  documents: z.array(ingestRequestSchema).min(1)
});

export const reindexResponseSchema = z.object({
  reindexedCount: z.number().int().nonnegative()
});

export type IngestRequest = z.infer<typeof ingestRequestSchema>;
export type IngestResponse = z.infer<typeof ingestResponseSchema>;
export type SearchRequest = z.infer<typeof searchRequestSchema>;
export type KnowledgeSource = z.infer<typeof knowledgeSourceSchema>;
export type SearchResponse = z.infer<typeof searchResponseSchema>;
export type DocumentStatusRequest = z.infer<typeof documentStatusRequestSchema>;
export type ReindexRequest = z.infer<typeof reindexRequestSchema>;
export type ReindexResponse = z.infer<typeof reindexResponseSchema>;
