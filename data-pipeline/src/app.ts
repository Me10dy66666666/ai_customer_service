import cors from "@fastify/cors";
import multipart from "@fastify/multipart";
import Fastify, { type FastifyInstance } from "fastify";
import { timingSafeEqual } from "node:crypto";
import { ZodError } from "zod";

import type { PipelineConfig } from "./config.js";
import { DocumentChunkingService } from "./chunking/documentChunkingService.js";
import {
  OpenAiCompatibleEmbeddingClient,
  type EmbeddingClient
} from "./embedding/embeddingClient.js";
import { PgVectorStore } from "./vector/pgVectorStore.js";
import type { VectorStore } from "./vector/vectorStore.js";
import { KnowledgeBaseManager } from "./services/knowledgeBaseManager.js";
import type { FileParser } from "./parsing/fileParser.js";
import { createDefaultFileParser } from "./parsing/createDefaultFileParser.js";
import {
  ingestRequestSchema,
  searchRequestSchema,
  documentStatusRequestSchema,
  reindexRequestSchema
} from "./types.js";

export interface AppDependencies {
  config: PipelineConfig;
  embedding?: EmbeddingClient;
  vectorStore?: VectorStore;
  fileParser?: FileParser;
}

function readQueryValue(query: unknown, name: string): string | undefined {
  if (query && typeof query === "object") {
    const value = (query as Record<string, unknown>)[name];
    if (typeof value === "string") return value;
  }
  return undefined;
}

/**
 * 构建 data-pipeline Fastify 应用。
 * embedding / vectorStore / fileParser 允许注入，便于测试与后续切换。
 */
export async function buildApp(deps: AppDependencies): Promise<FastifyInstance> {
  const config = deps.config;
  const fastify = Fastify({ bodyLimit: 64 * 1024, logger: { level: config.logLevel } });

  await fastify.register(cors, { origin: config.security.allowedOrigins });
  await fastify.register(multipart, { limits: { fileSize: 20 * 1024 * 1024 } });

  fastify.addHook("onRequest", async (request, reply) => {
    if (request.url === "/health" || request.url === "/ready") return;
    const authorization = request.headers.authorization;
    const supplied = authorization?.startsWith("Bearer ") ? authorization.slice(7) : "";
    const expected = config.security.serviceToken;
    const suppliedBuffer = Buffer.from(supplied);
    const expectedBuffer = Buffer.from(expected);
    const authorized = suppliedBuffer.length === expectedBuffer.length
      && timingSafeEqual(suppliedBuffer, expectedBuffer);
    if (!authorized) {
      return reply.code(401).send({ message: "Unauthorized" });
    }
  });

  const chunking = new DocumentChunkingService({
    chunkSize: config.chunking.chunkSize,
    chunkOverlap: config.chunking.chunkOverlap,
    pdrParentChunkSize: config.chunking.pdrParentChunkSize,
    pdrParentOverlap: config.chunking.pdrParentOverlap,
    pdrChildChunkSize: config.chunking.pdrChildChunkSize,
    pdrChildOverlap: config.chunking.pdrChildOverlap
  });

  const embedding = deps.embedding ?? new OpenAiCompatibleEmbeddingClient({
    baseUrl: config.embedding.baseUrl,
    apiKey: config.embedding.apiKey,
    model: config.embedding.model,
    dimensions: config.embedding.dimensions,
    timeoutMs: config.embedding.timeoutMs,
    retryAttempts: config.embedding.retryAttempts,
    retryDelayMs: config.embedding.retryDelayMs
  });

  const vectorStore = deps.vectorStore ?? new PgVectorStore({
    connectionString: config.postgres.connectionString,
    dimensions: config.embedding.dimensions,
    poolMax: config.postgres.poolMax,
    idleTimeoutMs: config.postgres.idleTimeoutMs,
    connectionTimeoutMs: config.postgres.connectionTimeoutMs,
    statementTimeoutMs: config.postgres.statementTimeoutMs,
    retryAttempts: config.postgres.retryAttempts,
    retryDelayMs: config.postgres.retryDelayMs
  });
  await vectorStore.initialize?.();
  fastify.addHook("onClose", async () => {
    await vectorStore.close?.();
  });

  const fileParser = deps.fileParser ?? createDefaultFileParser();

  const manager = new KnowledgeBaseManager({ chunking, embedding, vectorStore });

  fastify.get("/health", async () => ({ status: "ok" }));

  fastify.get("/ready", async (_request, reply) => {
    try {
      await vectorStore.health?.();
      return { status: "ready", vectorStore: "postgresql+pgvector" };
    } catch (error) {
      fastify.log.warn({ err: error instanceof Error ? error.message : String(error) }, "data-pipeline is not ready");
      return reply.code(503).send({ status: "not_ready" });
    }
  });

  fastify.post("/ingest", async (request, reply) => {
    const body = ingestRequestSchema.parse(request.body);
    const result = await manager.ingest(body);
    return reply.code(201).send(result);
  });

  fastify.post("/ingest/file", async (request, reply) => {
    const file = await request.file();
    if (!file) {
      return reply.code(400).send({ message: "缺少上传文件" });
    }
    const datasetId = readQueryValue(request.query, "datasetId") ?? "default";
    const buffer = await file.toBuffer();
    const content = await fileParser.parse(file.filename, buffer);
    const result = await manager.ingest({ filename: file.filename, content, datasetId });
    return reply.code(201).send(result);
  });

  fastify.post("/search", async (request) => {
    const body = searchRequestSchema.parse(request.body);
    const sources = await manager.search({
      query: body.query,
      limit: body.limit ?? config.searchLimit,
      datasetId: body.datasetId,
      knowledgeDomain: body.knowledgeDomain,
      roles: body.roles
    });
    return { sources };
  });

  fastify.get("/documents", async (request) => {
    const datasetId = readQueryValue(request.query, "datasetId") ?? "default";
    const page = Math.max(1, Number(readQueryValue(request.query, "page") ?? "1"));
    const limit = Math.min(100, Math.max(1, Number(readQueryValue(request.query, "limit") ?? "20")));
    const data = await manager.listDocuments(datasetId, Number.isFinite(page) ? Math.trunc(page) : 1,
      Number.isFinite(limit) ? Math.trunc(limit) : 20);
    return { data, has_more: data.length === limit };
  });

  fastify.delete("/documents/:documentId", async (request) => {
    const { documentId } = request.params as { documentId: string };
    await manager.deleteDocument(documentId);
    return { deleted: true };
  });

  fastify.post("/documents/:documentId/status", async (request) => {
    const { documentId } = request.params as { documentId: string };
    const body = documentStatusRequestSchema.parse(request.body);
    await manager.updateDocumentStatus(documentId, body.enabled);
    return { documentId, enabled: body.enabled };
  });

  fastify.post("/reindex", async (request) => {
    const body = reindexRequestSchema.parse(request.body);
    const reindexedCount = await manager.reindex(body.documents);
    return { reindexedCount };
  });

  fastify.setErrorHandler((error, request, reply) => {
    if (error instanceof ZodError) {
      return reply.code(400).send({ message: "请求参数不合法", issues: error.issues });
    }
    const err = error as Error;
    request.log.error({ err: err.message, requestId: request.id }, "data-pipeline request failed");
    return reply.status(500).send({ message: "数据处理服务暂时不可用" });
  });

  return fastify;
}
