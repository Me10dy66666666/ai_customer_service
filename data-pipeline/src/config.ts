export type LogLevel = "debug" | "info" | "warn" | "error";

export interface PipelineConfig {
  port: number;
  security: {
    serviceToken: string;
    allowedOrigins: string[];
  };
  embedding: {
    baseUrl: string;
    apiKey: string;
    model: string;
    dimensions: number;
  };
  chromadb: {
    url: string;
    collection: string;
  };
  chunking: {
    strategy: "recursive" | "pdr";
    chunkSize: number;
    chunkOverlap: number;
    pdrParentChunkSize: number;
    pdrParentOverlap: number;
    pdrChildChunkSize: number;
    pdrChildOverlap: number;
  };
  searchLimit: number;
  logLevel: LogLevel;
}

function toInt(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizeLogLevel(value: string | undefined): LogLevel {
  if (value === "debug" || value === "warn" || value === "error" || value === "info") {
    return value;
  }
  return "info";
}

/**
 * 从环境变量加载数据处理服务配置。
 * 所有敏感信息（如 Embedding API Key）均通过环境变量注入，不硬编码。
 */
export function loadPipelineConfig(source: NodeJS.ProcessEnv = process.env): PipelineConfig {
  const configuredToken = source.PIPELINE_SERVICE_TOKEN;
  if (source.NODE_ENV === "production" && !configuredToken) {
    throw new Error("PIPELINE_SERVICE_TOKEN is required in production");
  }
  return {
    port: toInt(source.PORT, 3002),
    security: {
      serviceToken: configuredToken ?? "dev-only-change-this-pipeline-token",
      allowedOrigins: (source.CORS_ALLOWED_ORIGINS ?? "http://localhost:5173")
        .split(",")
        .map((origin) => origin.trim())
        .filter(Boolean)
    },
    embedding: {
      baseUrl: source.EMBEDDING_BASE_URL ?? "https://api.openai.com/v1",
      apiKey: source.EMBEDDING_API_KEY ?? "",
      model: source.EMBEDDING_MODEL ?? "text-embedding-v3",
      dimensions: toInt(source.EMBEDDING_DIMENSIONS, 1024)
    },
    chromadb: {
      url: source.CHROMADB_URL ?? "http://localhost:8000",
      collection: source.CHROMADB_COLLECTION ?? "customer_service_knowledge"
    },
    chunking: {
      strategy: source.CHUNKING_STRATEGY === "recursive" ? "recursive" : "pdr",
      chunkSize: toInt(source.CHUNK_SIZE, 800),
      chunkOverlap: toInt(source.CHUNK_OVERLAP, 150),
      pdrParentChunkSize: toInt(source.PDR_PARENT_CHUNK_SIZE, 2000),
      pdrParentOverlap: toInt(source.PDR_PARENT_OVERLAP, 200),
      pdrChildChunkSize: toInt(source.PDR_CHILD_CHUNK_SIZE, 400),
      pdrChildOverlap: toInt(source.PDR_CHILD_OVERLAP, 50)
    },
    searchLimit: toInt(source.SEARCH_LIMIT, 5),
    logLevel: normalizeLogLevel(source.LOG_LEVEL)
  };
}
