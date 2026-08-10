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

import { InMemoryKnowledgeBase } from "./adapters/inMemoryKnowledgeBase.js";
import { ChromadbKnowledgeBase } from "./adapters/chromadbKnowledgeBase.js";
import { ChromadbKnowledgeBaseManager } from "./services/chromadbKnowledgeBaseManager.js";
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
  const fastify = Fastify({ bodyLimit: 64 * 1024, logger: false });
  const logger = dependencies.logger ?? consoleLogger;

  // 1. 创建 Agent 注册中心
  const registry = new AgentRegistry();

  // 2. 从配置构建默认 Agent 列表
  const sensitiveConfig = {
    ...dependencies.config,
    ...dependencies.config.dify,
    ALLOWED_ORIGINS: dependencies.config.allowedOrigins.join(",")
  } as Record<string, unknown>;

  const agentMetadataList = buildDefaultAgents({
    AGENT_MODEL_MODE: dependencies.config.modelMode,
    OPENAI_API_KEY: dependencies.config.openAiApiKey,
    OPENAI_BASE_URL: dependencies.config.openAiBaseUrl,
    OPENAI_MODEL: dependencies.config.openAiModel,
    EMBEDDING_MODEL: dependencies.config.embedding.model,
    EMBEDDING_DIMENSIONS: dependencies.config.embedding.dimensions,
    CHROMADB_URL: dependencies.config.chromadb.url,
    CHROMADB_COLLECTION: dependencies.config.chromadb.collection,
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

  // 3. 创建知识库（优先 ChromaDB，降级内存）
  let knowledgeRetriever = dependencies.knowledgeRetriever;
  let knowledgeBaseManager: import("@enterprise-webagent/core").KnowledgeBaseManager | null = null;

  if (dependencies.config.chromadb.url !== "http://localhost:8000" || dependencies.config.openAiApiKey) {
    try {
      const chromadbKB = new ChromadbKnowledgeBase({
        chromadbUrl: dependencies.config.chromadb.url,
        collection: dependencies.config.chromadb.collection,
        embeddingApiKey: dependencies.config.openAiApiKey,
        embeddingBaseUrl: dependencies.config.openAiBaseUrl,
        embeddingModel: dependencies.config.embedding.model,
        embeddingDimensions: dependencies.config.embedding.dimensions
      });
      knowledgeRetriever = chromadbKB;
      knowledgeBaseManager = new ChromadbKnowledgeBaseManager(chromadbKB, dependencies.config);
      logger.info("Using ChromaDB knowledge base", { url: dependencies.config.chromadb.url });
    } catch (error) {
      logger.warn("ChromaDB init failed, falling back to in-memory knowledge base", {
        error: error instanceof Error ? error.message : String(error)
      });
    }
  }

  // 4. 注册各 Agent 实例
  for (const meta of agentMetadataList) {
    if (meta.type === "custom") {
      const chatModel = dependencies.chatModel ?? selectChatModel(dependencies.config, logger);
      const kbRetriever = knowledgeRetriever ?? new InMemoryKnowledgeBase();

      const agent = new CustomCustomerAgent(meta, {
        knowledgeRetriever: kbRetriever,
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
