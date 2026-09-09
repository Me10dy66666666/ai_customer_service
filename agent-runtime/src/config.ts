export interface RuntimeConfig {
  port: number;
  serviceToken: string;
  backendBaseUrl: string;
  pipelineBaseUrl: string;
  pipelineServiceToken: string;
  modelProvider: string;
  modelName: string;
  modelApiKey: string;
  modelBaseUrl?: string;
  modelTimeoutMs: number;
  maxSessions: number;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): RuntimeConfig {
  const value = (name: string, fallback = "") => env[name]?.trim() || fallback;
  const number = (name: string, fallback: number) => {
    const parsed = Number.parseInt(value(name), 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
  };

  return {
    port: number("PI_RUNTIME_PORT", 3001),
    serviceToken: value("PI_RUNTIME_SERVICE_TOKEN"),
    backendBaseUrl: value("BACKEND_BASE_URL", "http://localhost:8081").replace(/\/$/, ""),
    pipelineBaseUrl: value("DATA_PIPELINE_URL", "http://localhost:3002").replace(/\/$/, ""),
    pipelineServiceToken: value("PIPELINE_SERVICE_TOKEN"),
    modelProvider: value("MODEL_PROVIDER", "deepseek"),
    modelName: value("MODEL_NAME", "deepseek-chat"),
    modelApiKey: value("MODEL_API_KEY"),
    modelBaseUrl: value("MODEL_BASE_URL") || undefined,
    modelTimeoutMs: number("MODEL_TIMEOUT_MS", 120_000),
    maxSessions: number("PI_RUNTIME_MAX_SESSIONS", 1000),
  };
}

export function assertProductionConfig(config: RuntimeConfig): void {
  if (!config.serviceToken) {
    throw new Error("PI_RUNTIME_SERVICE_TOKEN must be configured");
  }
  if (!config.modelApiKey) {
    throw new Error("MODEL_API_KEY must be configured");
  }
}
