import cors from "@fastify/cors";
import Fastify, { type FastifyInstance } from "fastify";

import {
  CustomerAgentModule,
  consoleLogger,
  type ChatModel,
  type CustomerAgent,
  type KnowledgeRetriever,
  type Logger
} from "@enterprise-webagent/core";

import { InMemoryKnowledgeBase } from "./adapters/inMemoryKnowledgeBase.js";
import {
  MockChatModel,
  OpenAiCompatibleChatModel
} from "./adapters/openAiCompatibleChatModel.js";
import type { ServerConfig } from "./config.js";
import { registerMessageRoute } from "./routes/messageRoute.js";

export interface AppDependencies {
  config: ServerConfig;
  logger?: Logger;
  knowledgeRetriever?: KnowledgeRetriever;
  chatModel?: ChatModel;
}

function isOriginAllowed(origin: string | undefined, allowedOrigins: string[]): boolean {
  if (!origin) {
    return true;
  }

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

function buildCustomerAgent(dependencies: AppDependencies): CustomerAgent {
  const logger = dependencies.logger ?? consoleLogger;

  return new CustomerAgentModule({
    knowledgeRetriever: dependencies.knowledgeRetriever ?? new InMemoryKnowledgeBase(),
    chatModel: dependencies.chatModel ?? selectChatModel(dependencies.config, logger),
    logger
  });
}

export async function buildApp(dependencies: AppDependencies): Promise<FastifyInstance> {
  const fastify = Fastify({
    bodyLimit: 64 * 1024,
    logger: false
  });
  const logger = dependencies.logger ?? consoleLogger;
  const customerAgent = buildCustomerAgent(dependencies);

  await fastify.register(cors, {
    origin(origin, callback) {
      const allowed = isOriginAllowed(origin, dependencies.config.allowedOrigins);
      callback(allowed ? null : new Error("Origin not allowed"), allowed);
    }
  });

  fastify.get("/health", async () => ({ status: "ok" }));

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

  registerMessageRoute(fastify, customerAgent);

  return fastify;
}
