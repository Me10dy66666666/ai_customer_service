import { z } from "zod";

// ============================================================
// 基础类型
// ============================================================
export const userTypeSchema = z.coerce.number().int().min(0);

// ============================================================
// 消息请求
// ============================================================
export const agentMessageRequestSchema = z.object({
  sessionId: z.string().trim().min(1).max(64).optional(),
  userInput: z.string().trim().min(1).max(2_000),
  historyOrders: z.array(z.string().trim().min(1).max(200)).max(20).default([]),
  userType: userTypeSchema.default(0),
  locale: z.string().trim().min(2).max(10).default("zh-CN")
});

// ============================================================
// 知识源
// ============================================================
export const knowledgeSourceSchema = z.object({
  id: z.string().trim().min(1).max(128),
  title: z.string().trim().min(1).max(120),
  excerpt: z.string().trim().min(1).max(2_000),
  sourceType: z.enum(["knowledge_base", "policy", "faq"]),
  metadata: z.record(z.string(), z.string()).default({})
});

// ============================================================
// 工单动作
// ============================================================
export const workOrderActionSchema = z.object({
  action: z.literal("create_work_order"),
  data: z.object({
    title: z.string().trim().min(1).max(80),
    description: z.string().trim().min(1).max(600),
    type: z.enum(["售前", "售后"]),
    priority: z.enum(["high", "medium", "low"])
  })
});

// ============================================================
// 消息响应
// ============================================================
export const agentMessageResponseSchema = z.object({
  sessionId: z.string().trim().min(1).max(64),
  answer: z.string().trim().min(1).max(8_000),
  sources: z.array(knowledgeSourceSchema).max(10),
  actions: z.array(workOrderActionSchema).max(3).default([]),
  fallbackReason: z.enum(["knowledge_not_found", "model_unavailable", "agent_unavailable"]).optional(),
  generatedAt: z.string().datetime(),
  // 扩展字段：路由元信息
  routeMeta: z.object({
    channel: z.enum(["rag_fast", "function_calling", "fallback"]),
    intent: z.string().optional(),
    agentId: z.string().optional()
  }).optional()
});

// ============================================================
// 情绪分析
// ============================================================
export const sentimentResultSchema = z.object({
  emotionLevel: z.enum(["positive", "neutral", "negative", "angry"]),
  confidence: z.number().min(0).max(1),
  keywords: z.array(z.string()).default([]),
  suggestion: z.string().optional()
});

// ============================================================
// 工单分析结果（对齐 Backend WorkOrderAnalysisResult）
// ============================================================
export const workOrderAnalysisResultSchema = z.object({
  priority: z.enum(["high", "medium", "low"]),
  tags: z.string().optional(),
  summary: z.string().optional(),
  bizTag: z.string().optional(),
  emotionLevel: z.string().optional(),
  dispatchConfidence: z.number().min(0).max(1).optional()
});

// ============================================================
// 对话摘要结果（对齐 Backend SummaryResult）
// ============================================================
export const summaryResultSchema = z.object({
  priority: z.enum(["high", "medium", "low"]).optional(),
  summary: z.string().optional(),
  tags: z.string().optional()
});

// ============================================================
// Agent 元信息
// ============================================================
export const agentMetadataSchema = z.object({
  id: z.string().trim().min(1).max(64),
  name: z.string().trim().min(1).max(50),
  type: z.enum(["dify", "custom"]),
  description: z.string().trim().max(200).default(""),
  // Dify 专属配置
  difyConfig: z.object({
    apiKey: z.string().trim().default(""),
    baseUrl: z.string().trim().url().default(""),
    workflowEndpoint: z.string().trim().default("")
  }).optional(),
  // 自定义 Agent 专属配置
  customConfig: z.object({
    modelMode: z.enum(["mock", "openai-compatible", "dify"]),
    modelName: z.string().trim(),
    systemPromptTemplate: z.string().optional()
  }).optional(),
  enabled: z.boolean().default(true),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime()
});

// ============================================================
// Agent 运行指标
// ============================================================
export const agentMetricsSchema = z.object({
  agentId: z.string().trim().min(1),
  totalRequests: z.number().int().min(0),
  successCount: z.number().int().min(0),
  fallbackCount: z.number().int().min(0),
  avgResponseTimeMs: z.number().min(0),
  avgTokenUsage: z.number().min(0).optional(),
  lastActiveAt: z.string().datetime().optional()
});

// ============================================================
// Widget 配置
// ============================================================
export const widgetBootstrapOptionsSchema = z.object({
  apiBaseUrl: z.string().trim().url(),
  launcherLabel: z.string().trim().min(1).max(20).default("智能客服"),
  title: z.string().trim().min(1).max(30).default("企业智能客服"),
  userType: userTypeSchema.default(0),
  historyOrders: z.array(z.string().trim().min(1).max(200)).max(20).default([]),
  locale: z.string().trim().min(2).max(10).default("zh-CN"),
  welcomeMessage: z.string().trim().min(1).max(200).default("您好，我是您的智能客服助手。")
});

// ============================================================
// 导出类型
// ============================================================
export type AgentMessageRequest = z.infer<typeof agentMessageRequestSchema>;
export type KnowledgeSource = z.infer<typeof knowledgeSourceSchema>;
export type WorkOrderAction = z.infer<typeof workOrderActionSchema>;
export type AgentMessageResponse = z.infer<typeof agentMessageResponseSchema>;
export type SentimentResult = z.infer<typeof sentimentResultSchema>;
export type WorkOrderAnalysisResult = z.infer<typeof workOrderAnalysisResultSchema>;
export type SummaryResult = z.infer<typeof summaryResultSchema>;
export type AgentMetadata = z.infer<typeof agentMetadataSchema>;
export type AgentMetrics = z.infer<typeof agentMetricsSchema>;
export type WidgetBootstrapOptions = z.infer<typeof widgetBootstrapOptionsSchema>;
