import cors from "@fastify/cors";
import multipart from "@fastify/multipart";
import Fastify, { type FastifyInstance } from "fastify";

import {
  CustomCustomerAgent,
  AgentRegistry,
  RouteDispatcher,
  buildDefaultAgents,
  consoleLogger,
  type ChatModel,
  type KnowledgeRetriever,
  type Logger
} from "@enterprise-webagent/core";

import { DataPipelineKnowledgeBase } from "./adapters/dataPipelineKnowledgeBase.js";
import {
  MockChatModel,
  OpenAiCompatibleChatModel
} from "./adapters/openAiCompatibleChatModel.js";
import { DifyAgentAdapter } from "./adapters/difyAgentAdapter.js";
import type { ServerConfig } from "./config.js";
import { registerMessageRoute, registerAgentManagementRoutes } from "./routes/messageRoute.js";
import { registerBackendCompatibleRoutes } from "./routes/backendCompatibleRoutes.js";
import { InMemorySessionStore } from "./adapters/inMemorySessionStore.js";

export interface AppDependencies {
  config: ServerConfig;
  logger?: Logger;
  knowledgeRetriever?: KnowledgeRetriever;
  chatModel?: ChatModel;
}

function isOriginAllowed(origin: string | undefined, allowedOrigins: string[]): boolean {
  if (!origin) return true;
  return allowedOrigins.includes("*") || allowedOrigins.includes(origin);
}

function selectChatModel(config: ServerConfig, logger: Logger): ChatModel {
  if (config.modelMode === "mock") {
    return new MockChatModel();
  }
  if (!config.openAiApiKey) {
    throw new Error("OPENAI_API_KEY is required when AGENT_MODEL_MODE=openai-compatible");
  }
  return new OpenAiCompatibleChatModel({
    baseUrl: config.openAiBaseUrl,
    apiKey: config.openAiApiKey,
    model: config.openAiModel,
    logger
  });
}

export async function buildApp(dependencies: AppDependencies): Promise<FastifyInstance> {
  const fastify = Fastify({ bodyLimit: 64 * 1024, logger: { level: dependencies.config.log.level } });
  const logger = dependencies.logger ?? consoleLogger;

  // 1. 创建 Agent 注册中心
  const registry = new AgentRegistry();

  // 2. 从配置构建默认 Agent 列表
  const agentMetadataList = buildDefaultAgents({
    AGENT_MODEL_MODE: dependencies.config.modelMode,
    OPENAI_API_KEY: dependencies.config.openAiApiKey,
    OPENAI_BASE_URL: dependencies.config.openAiBaseUrl,
    OPENAI_MODEL: dependencies.config.openAiModel,
    EMBEDDING_MODEL: dependencies.config.embedding.model,
    EMBEDDING_DIMENSIONS: dependencies.config.embedding.dimensions,
    DATA_PIPELINE_URL: dependencies.config.dataPipeline.baseUrl,
    PIPELINE_SERVICE_TOKEN: dependencies.config.dataPipeline.serviceToken,
    DATA_PIPELINE_TIMEOUT_MS: dependencies.config.dataPipeline.timeoutMs,
    DATA_PIPELINE_RETRY_ATTEMPTS: dependencies.config.dataPipeline.retryAttempts,
    DATA_PIPELINE_RETRY_DELAY_MS: dependencies.config.dataPipeline.retryDelayMs,
    DIFY_BASE_URL: dependencies.config.dify.baseUrl,
    DIFY_CHAT_API_KEY: dependencies.config.dify.chatApiKey,
    DIFY_KNOWLEDGE_API_KEY: dependencies.config.dify.knowledgeApiKey,
    DIFY_DATASET_ID: dependencies.config.dify.datasetId,
    DIFY_INTERVENTION_API_KEY: dependencies.config.dify.interventionApiKey,
    DIFY_WORKORDER_API_KEY: dependencies.config.dify.workorderApiKey,
    DIFY_TRANSFER_WORKFLOW_ENDPOINT: dependencies.config.dify.transferWorkflowEndpoint,
    DIFY_WORKORDER_WORKFLOW_ENDPOINT: dependencies.config.dify.workorderWorkflowEndpoint,
    PORT: dependencies.config.port,
    ALLOWED_ORIGINS: dependencies.config.allowedOrigins.join(","),
    KNOWLEDGE_SEARCH_LIMIT: dependencies.config.knowledge.searchLimit,
    KNOWLEDGE_CACHE_TTL_SECONDS: dependencies.config.knowledge.cacheTtlSeconds,
    SESSION_TTL_SECONDS: dependencies.config.session.ttlSeconds,
    MAX_CONCURRENT_AGENTS: dependencies.config.session.maxConcurrentAgents,
    LOG_LEVEL: dependencies.config.log.level
  });

  // 3. KnowledgeRetriever 只依赖 data-pipeline 的 HTTP 契约；不在 Agent 进程
  // 内保留内存/向量数据库实现，避免生产环境静默降级造成数据不一致。
  const dataPipeline = new DataPipelineKnowledgeBase({
    baseUrl: dependencies.config.dataPipeline.baseUrl,
    serviceToken: dependencies.config.dataPipeline.serviceToken,
    timeoutMs: dependencies.config.dataPipeline.timeoutMs,
    retryAttempts: dependencies.config.dataPipeline.retryAttempts,
    retryDelayMs: dependencies.config.dataPipeline.retryDelayMs,
    embeddingModel: dependencies.config.embedding.model,
    embeddingDimensions: dependencies.config.embedding.dimensions
  });
  const knowledgeRetriever = dependencies.knowledgeRetriever ?? dataPipeline;
  let knowledgeBaseManager: import("@enterprise-webagent/core").KnowledgeBaseManager | null = null;
  if (knowledgeRetriever === dataPipeline) knowledgeBaseManager = dataPipeline;

  // 4. 注册各 Agent 实例
  for (const meta of agentMetadataList) {
    if (meta.type === "custom") {
      const chatModel = dependencies.chatModel ?? selectChatModel(dependencies.config, logger);
      const agent = new CustomCustomerAgent(meta, {
        knowledgeRetriever,
        chatModel,
        logger
      });
      registry.register(agent);
      logger.info("Registered custom agent", { agentId: meta.id });
    } else if (meta.type === "dify") {
      const agent = new DifyAgentAdapter(meta);
      registry.register(agent);
      logger.info("Registered Dify agent", { agentId: meta.id });
    }
  }

  // 4. 创建路由调度器
  const dispatcher = new RouteDispatcher(registry, logger);

  // 5. 创建会话存储
  const sessionStore = new InMemorySessionStore(dependencies.config.session.ttlSeconds);

  // 6. 装配 Fastify
  await fastify.register(cors, {
    origin(origin, callback) {
      const allowed = isOriginAllowed(origin, dependencies.config.allowedOrigins);
      callback(allowed ? null : new Error("Origin not allowed"), allowed);
    }
  });
  await fastify.register(multipart, {
    limits: { fileSize: 10 * 1024 * 1024 } // 10MB
  });

  fastify.get("/health", async () => ({
    status: "ok",
    agents: registry.enabledCount,
    total: registry.count
  }));

  fastify.setErrorHandler((error, request, reply) => {
    const err = error as Error;
    logger.error("Unhandled server error", {
      requestId: request.id,
      path: request.url,
      message: err.message
    });
    reply.status(500).send({
      message: "服务端发生未处理异常，请稍后重试。"
    });
  });

  // 注册路由
  registerMessageRoute(fastify, dispatcher, logger);
  registerBackendCompatibleRoutes(fastify, dispatcher, knowledgeBaseManager, logger);
  registerAgentManagementRoutes(fastify, registry, sessionStore, logger, dependencies.config);

  return fastify;
}
