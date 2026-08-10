import { z } from "zod";
import { loadSensitiveConfig } from "@enterprise-webagent/core";
import type { SensitiveConfig } from "@enterprise-webagent/core";

/**
 * 服务端统一配置
 * 基于 packages/core/config/agentConfig 的敏感配置扩展
 * 所有敏感信息通过环境变量注入，不在代码中硬编码
 */
export interface ServerConfig {
  port: number;
  modelMode: "mock" | "openai-compatible" | "dify";
  openAiBaseUrl: string;
  openAiModel: string;
  openAiApiKey: string;
  allowedOrigins: string[];
  // Embedding + 向量数据库
  embedding: {
    model: string;
    dimensions: number;
  };
  chromadb: {
    url: string;
    collection: string;
  };
  // 扩展配置
  dify: {
    baseUrl: string;
    chatApiKey: string;
    knowledgeApiKey: string;
    datasetId: string;
    interventionApiKey: string;
    workorderApiKey: string;
    transferWorkflowEndpoint: string;
    workorderWorkflowEndpoint: string;
  };
  knowledge: {
    searchLimit: number;
    cacheTtlSeconds: number;
  };
  session: {
    ttlSeconds: number;
    maxConcurrentAgents: number;
  };
  log: {
    level: "debug" | "info" | "warn" | "error";
  };
}

export function loadServerConfig(source: NodeJS.ProcessEnv = process.env): ServerConfig {
  const sensitive = loadSensitiveConfig(source);

  const allowedOrigins = sensitive.ALLOWED_ORIGINS === "*"
    ? ["*"]
    : sensitive.ALLOWED_ORIGINS.split(",").map((item) => item.trim()).filter(Boolean);

  return {
    port: sensitive.PORT,
    modelMode: sensitive.AGENT_MODEL_MODE,
    openAiBaseUrl: sensitive.OPENAI_BASE_URL,
    openAiModel: sensitive.OPENAI_MODEL,
    openAiApiKey: sensitive.OPENAI_API_KEY,
    allowedOrigins,
    embedding: {
      model: sensitive.EMBEDDING_MODEL,
      dimensions: sensitive.EMBEDDING_DIMENSIONS
    },
    chromadb: {
      url: sensitive.CHROMADB_URL,
      collection: sensitive.CHROMADB_COLLECTION
    },
    dify: {
      baseUrl: sensitive.DIFY_BASE_URL,
      chatApiKey: sensitive.DIFY_CHAT_API_KEY,
      knowledgeApiKey: sensitive.DIFY_KNOWLEDGE_API_KEY,
      datasetId: sensitive.DIFY_DATASET_ID,
      interventionApiKey: sensitive.DIFY_INTERVENTION_API_KEY,
      workorderApiKey: sensitive.DIFY_WORKORDER_API_KEY,
      transferWorkflowEndpoint: sensitive.DIFY_TRANSFER_WORKFLOW_ENDPOINT,
      workorderWorkflowEndpoint: sensitive.DIFY_WORKORDER_WORKFLOW_ENDPOINT
    },
    knowledge: {
      searchLimit: sensitive.KNOWLEDGE_SEARCH_LIMIT,
      cacheTtlSeconds: sensitive.KNOWLEDGE_CACHE_TTL_SECONDS
    },
    session: {
      ttlSeconds: sensitive.SESSION_TTL_SECONDS,
      maxConcurrentAgents: sensitive.MAX_CONCURRENT_AGENTS
    },
    log: {
      level: sensitive.LOG_LEVEL
    }
  };
}
