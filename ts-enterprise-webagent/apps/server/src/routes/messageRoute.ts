import type { FastifyInstance } from "fastify";
import type { Logger } from "@enterprise-webagent/core";
import {
  agentMessageRequestSchema,
  agentMessageResponseSchema,
  type AgentMetadata
} from "@enterprise-webagent/shared";
import type { RouteDispatcher } from "@enterprise-webagent/core";
import type { AgentRegistry } from "@enterprise-webagent/core";
import type { InMemorySessionStore } from "../adapters/inMemorySessionStore.js";
import type { ServerConfig } from "../config.js";

// ============================================================
// 核心消息路由（对齐 Backend ChatController）
// ============================================================
export function registerMessageRoute(
  fastify: FastifyInstance,
  dispatcher: RouteDispatcher,
  logger: Logger
): void {
  fastify.post("/api/v1/customer-agent/messages", async (request, reply) => {
    const parsedBody = agentMessageRequestSchema.safeParse(request.body);
    if (!parsedBody.success) {
      return reply.status(400).send({
        message: "请求参数校验失败",
        issues: parsedBody.error.issues
      });
    }

    try {
      const response = await dispatcher.dispatch(parsedBody.data);
      const parsedResponse = agentMessageResponseSchema.parse(response);
      return reply.status(200).send(parsedResponse);
    } catch (error) {
      logger.error("Agent dispatch failed", {
        error: error instanceof Error ? error.message : String(error)
      });
      return reply.status(500).send({
        message: "Agent 调度失败，请稍后重试。"
      });
    }
  });
}

// ============================================================
// Agent 管理路由（管理界面 API）
// ============================================================
export function registerAgentManagementRoutes(
  fastify: FastifyInstance,
  registry: AgentRegistry,
  sessionStore: InMemorySessionStore,
  logger: Logger,
  config: ServerConfig
): void {
  // 获取所有 Agent 列表
  fastify.get("/api/v1/management/agents", async () => {
    return {
      agents: registry.getAllMetadata(),
      total: registry.count,
      enabled: registry.enabledCount
    };
  });

  // 获取所有 Agent 指标
  fastify.get("/api/v1/management/agents/metrics", async () => {
    return {
      metrics: registry.getAllMetrics(),
      collectedAt: new Date().toISOString()
    };
  });

  // 获取单个 Agent 指标历史
  fastify.get<{ Params: { agentId: string } }>(
    "/api/v1/management/agents/:agentId/metrics/history",
    async (request) => {
      const { agentId } = request.params;
      const agent = registry.get(agentId);
      if (!agent) {
        return { metrics: null, history: registry.getMetricsHistory(agentId) };
      }
      return { metrics: agent.getMetrics(), history: registry.getMetricsHistory(agentId) };
    }
  );

  // 健康检查所有 Agent
  fastify.get("/api/v1/management/agents/health", async () => {
    const healthResults = await registry.healthCheckAll();
    const results: Record<string, unknown> = {};
    for (const [id, result] of healthResults) {
      results[id] = result;
    }
    return { health: results, checkedAt: new Date().toISOString() };
  });

  // 获取会话列表
  fastify.get("/api/v1/management/sessions", async () => {
    const sessions = sessionStore.getAllSessions();
    return { sessions, total: sessions.length };
  });

  // 获取配置摘要（脱敏）
  fastify.get("/api/v1/management/config", async () => {
    return {
      port: config.port,
      modelMode: config.modelMode,
      openAiModel: config.openAiModel,
      openAiApiKey: config.openAiApiKey ? "***configured***" : "not set",
      difyBaseUrl: config.dify.baseUrl,
      difyApiKeys: {
        chat: config.dify.chatApiKey ? "configured" : "not set",
        knowledge: config.dify.knowledgeApiKey ? "configured" : "not set",
        intervention: config.dify.interventionApiKey ? "configured" : "not set",
        workorder: config.dify.workorderApiKey ? "configured" : "not set"
      },
      knowledge: config.knowledge,
      session: config.session,
      log: config.log
    };
  });

  // 采集指标（手动触发）
  fastify.post("/api/v1/management/metrics/collect", async () => {
    registry.collectMetrics();
    return { message: "Metrics collected", collectedAt: new Date().toISOString() };
  });

  logger.info("Agent management routes registered");
}
