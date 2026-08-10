import { z } from "zod";
import type { AgentMetadata } from "@enterprise-webagent/shared";

// ============================================================
// 敏感配置项 Schema（所有大模型/第三方密钥集中管理）
// ============================================================
export const sensitiveConfigSchema = z.object({
  // OpenAI / 兼容接口（LLM 和 Embedding 共用）
  OPENAI_API_KEY: z.string().trim().default(""),
  OPENAI_BASE_URL: z.string().trim().url().default("https://api.openai.com/v1"),
  OPENAI_MODEL: z.string().trim().default("gpt-4.1-mini"),

  // Embedding 模型配置（向量数据库专用）
  EMBEDDING_MODEL: z.string().trim().default("text-embedding-ada-002"),
  EMBEDDING_DIMENSIONS: z.coerce.number().int().min(128).max(4096).default(1536),

  // ChromaDB 向量数据库配置
  CHROMADB_URL: z.string().trim().url().default("http://localhost:8000"),
  CHROMADB_COLLECTION: z.string().trim().default("customer_service_knowledge"),

  // Dify 平台密钥
  DIFY_BASE_URL: z.string().trim().url().default("https://api.dify.ai/v1"),
  DIFY_CHAT_API_KEY: z.string().trim().default(""),
  DIFY_KNOWLEDGE_API_KEY: z.string().trim().default(""),
  DIFY_DATASET_ID: z.string().trim().default(""),
  DIFY_INTERVENTION_API_KEY: z.string().trim().default(""),
  DIFY_WORKORDER_API_KEY: z.string().trim().default(""),
  DIFY_TRANSFER_WORKFLOW_ENDPOINT: z.string().trim().default("/workflows/run"),
  DIFY_WORKORDER_WORKFLOW_ENDPOINT: z.string().trim().default("/workflows/run"),

  // 服务配置
  PORT: z.coerce.number().int().min(1).max(65535).default(3001),
  AGENT_MODEL_MODE: z.enum(["mock", "openai-compatible", "dify"]).default("mock"),
  ALLOWED_ORIGINS: z.string().trim().default("*"),

  // 知识库配置
  KNOWLEDGE_SEARCH_LIMIT: z.coerce.number().int().min(1).max(50).default(5),
  KNOWLEDGE_CACHE_TTL_SECONDS: z.coerce.number().int().min(0).default(3600),

  // Agent 会话配置
  SESSION_TTL_SECONDS: z.coerce.number().int().min(60).default(86400),
  MAX_CONCURRENT_AGENTS: z.coerce.number().int().min(1).max(100).default(10),

  // 日志级别
  LOG_LEVEL: z.enum(["debug", "info", "warn", "error"]).default("info")
});

export type SensitiveConfig = z.infer<typeof sensitiveConfigSchema>;

// ============================================================
// Agent 配置聚合
// ============================================================
export interface AgentSystemConfig {
  sensitive: SensitiveConfig;
  agents: AgentMetadata[];
}

export function loadSensitiveConfig(source: NodeJS.ProcessEnv = process.env): SensitiveConfig {
  return sensitiveConfigSchema.parse(source);
}

// ============================================================
// 默认 Agent 列表（可通过环境变量或数据库覆盖）
// ============================================================
export function buildDefaultAgents(config: SensitiveConfig): AgentMetadata[] {
  const now = new Date().toISOString();
  const agents: AgentMetadata[] = [];

  // 自定义 Agent（当前 ts-enterprise-webagent 自带）
  agents.push({
    id: "custom-customer-agent",
    name: "智能客服 Agent (自定义)",
    type: "custom",
    description: "基于 Function Calling + RAG 混合架构的自研智能客服 Agent",
    customConfig: {
      modelMode: config.AGENT_MODEL_MODE,
      modelName: config.OPENAI_MODEL
    },
    enabled: true,
    createdAt: now,
    updatedAt: now
  });

  // Dify Agent（如果有配置密钥则启用）
  if (config.DIFY_CHAT_API_KEY) {
    agents.push({
      id: "dify-chat-agent",
      name: "Dify 对话 Agent",
      type: "dify",
      description: "对接 Dify 平台的 Chat API，支持流式对话与知识检索",
      difyConfig: {
        apiKey: config.DIFY_CHAT_API_KEY,
        baseUrl: config.DIFY_BASE_URL,
        workflowEndpoint: "/chat-messages"
      },
      enabled: true,
      createdAt: now,
      updatedAt: now
    });
  }

  if (config.DIFY_INTERVENTION_API_KEY) {
    agents.push({
      id: "dify-transfer-agent",
      name: "Dify 转人工分析 Agent",
      type: "dify",
      description: "调用 Dify 转人工总结工作流，生成对话摘要与优先级评估",
      difyConfig: {
        apiKey: config.DIFY_INTERVENTION_API_KEY,
        baseUrl: config.DIFY_BASE_URL,
        workflowEndpoint: config.DIFY_TRANSFER_WORKFLOW_ENDPOINT
      },
      enabled: true,
      createdAt: now,
      updatedAt: now
    });
  }

  if (config.DIFY_WORKORDER_API_KEY) {
    agents.push({
      id: "dify-workorder-agent",
      name: "Dify 工单分析 Agent",
      type: "dify",
      description: "调用 Dify 工单分析工作流，评估优先级、标签、情绪与分派置信度",
      difyConfig: {
        apiKey: config.DIFY_WORKORDER_API_KEY,
        baseUrl: config.DIFY_BASE_URL,
        workflowEndpoint: config.DIFY_WORKORDER_WORKFLOW_ENDPOINT
      },
      enabled: true,
      createdAt: now,
      updatedAt: now
    });
  }

  return agents;
}
