import { z } from "zod";

export const userTypeSchema = z.coerce.number().int().min(0);

export const agentMessageRequestSchema = z.object({
  sessionId: z.string().trim().min(1).max(64).optional(),
  userInput: z.string().trim().min(1).max(2_000),
  historyOrders: z.array(z.string().trim().min(1).max(200)).max(20).default([]),
  userType: userTypeSchema.default(0),
  locale: z.string().trim().min(2).max(10).default("zh-CN")
});

export const knowledgeSourceSchema = z.object({
  id: z.string().trim().min(1).max(128),
  title: z.string().trim().min(1).max(120),
  excerpt: z.string().trim().min(1).max(2_000),
  sourceType: z.enum(["knowledge_base", "policy", "faq"]),
  metadata: z.record(z.string(), z.string()).default({})
});

export const workOrderActionSchema = z.object({
  action: z.literal("create_work_order"),
  data: z.object({
    title: z.string().trim().min(1).max(80),
    description: z.string().trim().min(1).max(600),
    type: z.enum(["售前", "售后"]),
    priority: z.enum(["high", "medium", "low"])
  })
});

export const agentMessageResponseSchema = z.object({
  sessionId: z.string().trim().min(1).max(64),
  answer: z.string().trim().min(1).max(8_000),
  sources: z.array(knowledgeSourceSchema).max(10),
  actions: z.array(workOrderActionSchema).max(3).default([]),
  fallbackReason: z.enum(["knowledge_not_found", "model_unavailable"]).optional(),
  generatedAt: z.string().datetime()
});

export const widgetBootstrapOptionsSchema = z.object({
  apiBaseUrl: z.string().trim().url(),
  launcherLabel: z.string().trim().min(1).max(20).default("智能客服"),
  title: z.string().trim().min(1).max(30).default("企业智能客服"),
  userType: userTypeSchema.default(0),
  historyOrders: z.array(z.string().trim().min(1).max(200)).max(20).default([]),
  locale: z.string().trim().min(2).max(10).default("zh-CN"),
  welcomeMessage: z.string().trim().min(1).max(200).default("您好，我是您的智能客服助手。")
});

export type AgentMessageRequest = z.infer<typeof agentMessageRequestSchema>;
export type KnowledgeSource = z.infer<typeof knowledgeSourceSchema>;
export type WorkOrderAction = z.infer<typeof workOrderActionSchema>;
export type AgentMessageResponse = z.infer<typeof agentMessageResponseSchema>;
export type WidgetBootstrapOptions = z.infer<typeof widgetBootstrapOptionsSchema>;
