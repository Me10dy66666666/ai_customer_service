export type LogLevel = "debug" | "info" | "warn" | "error";

export interface PipelineConfig {
  port: number;
  security: {
    serviceToken: string;
    allowedOrigins: string[];
  };
  postgres: {
    connectionString: string;
    poolMax: number;
    idleTimeoutMs: number;
    connectionTimeoutMs: number;
    statementTimeoutMs: number;
    retryAttempts: number;
    retryDelayMs: number;
  };
  embedding: {
    baseUrl: string;
    apiKey: string;
    model: string;
    dimensions: number;
    timeoutMs: number;
    retryAttempts: number;
    retryDelayMs: number;
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
  const configuredToken = source.PIPELINE_SERVICE_TOKEN?.trim();
  if (!configuredToken) {
    throw new Error("PIPELINE_SERVICE_TOKEN is required");
  }
  const connectionString = source.VECTOR_DATABASE_URL
    ?? source.DATABASE_URL
    ?? "postgresql://postgres:postgres@localhost:5432/ai_customer_service_vectors";
  if (source.NODE_ENV === "production"
      && !source.VECTOR_DATABASE_URL
      && !source.DATABASE_URL) {
    throw new Error("VECTOR_DATABASE_URL or DATABASE_URL is required in production");
  }
  return {
    port: toInt(source.PORT, 3002),
    security: {
      serviceToken: configuredToken,
      allowedOrigins: (source.CORS_ALLOWED_ORIGINS ?? "http://localhost:5173")
        .split(",")
        .map((origin) => origin.trim())
        .filter(Boolean)
    },
    postgres: {
      connectionString,
      poolMax: toInt(source.VECTOR_DB_POOL_MAX, 10),
      idleTimeoutMs: toInt(source.VECTOR_DB_IDLE_TIMEOUT_MS, 30_000),
      connectionTimeoutMs: toInt(source.VECTOR_DB_CONNECTION_TIMEOUT_MS, 5_000),
      statementTimeoutMs: toInt(source.VECTOR_DB_STATEMENT_TIMEOUT_MS, 15_000),
      retryAttempts: toInt(source.VECTOR_DB_RETRY_ATTEMPTS, 3),
      retryDelayMs: toInt(source.VECTOR_DB_RETRY_DELAY_MS, 100)
    },
    embedding: {
      baseUrl: source.EMBEDDING_BASE_URL ?? "https://api.openai.com/v1",
      apiKey: source.EMBEDDING_API_KEY ?? "",
      model: source.EMBEDDING_MODEL ?? "text-embedding-v3",
      dimensions: toInt(source.EMBEDDING_DIMENSIONS, 1024),
      timeoutMs: toInt(source.EMBEDDING_TIMEOUT_MS, 15_000),
      retryAttempts: toInt(source.EMBEDDING_RETRY_ATTEMPTS, 3),
      retryDelayMs: toInt(source.EMBEDDING_RETRY_DELAY_MS, 250)
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
