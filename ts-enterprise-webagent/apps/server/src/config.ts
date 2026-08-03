import { z } from "zod";

const envSchema = z.object({
  PORT: z.coerce.number().int().min(1).max(65_535).default(3001),
  AGENT_MODEL_MODE: z.enum(["mock", "openai-compatible"]).default("mock"),
  OPENAI_BASE_URL: z.string().trim().url().default("https://api.openai.com/v1"),
  OPENAI_MODEL: z.string().trim().min(1).max(120).default("gpt-4.1-mini"),
  OPENAI_API_KEY: z.string().trim().default(""),
  ALLOWED_ORIGINS: z.string().trim().default("*")
});

export interface ServerConfig {
  port: number;
  modelMode: "mock" | "openai-compatible";
  openAiBaseUrl: string;
  openAiModel: string;
  openAiApiKey: string;
  allowedOrigins: string[];
}

export function loadServerConfig(source: NodeJS.ProcessEnv = process.env): ServerConfig {
  const parsedEnv = envSchema.parse(source);
  const allowedOrigins = parsedEnv.ALLOWED_ORIGINS === "*"
    ? ["*"]
    : parsedEnv.ALLOWED_ORIGINS.split(",").map((item) => item.trim()).filter(Boolean);

  return {
    port: parsedEnv.PORT,
    modelMode: parsedEnv.AGENT_MODEL_MODE,
    openAiBaseUrl: parsedEnv.OPENAI_BASE_URL,
    openAiModel: parsedEnv.OPENAI_MODEL,
    openAiApiKey: parsedEnv.OPENAI_API_KEY,
    allowedOrigins
  };
}
