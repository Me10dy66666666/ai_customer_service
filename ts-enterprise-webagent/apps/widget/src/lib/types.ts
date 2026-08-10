// Widget 内部类型定义
// 对齐 @enterprise-webagent/shared 的 Schema 定义
// 使用 camelCase 命名约定，与 Server 端保持一致

export interface AgentMessage {
  id: string;
  role: "user" | "agent" | "system";
  content: string;
  timestamp: number;
  /** 附带的来源引用 */
  sources?: KnowledgeSource[];
  /** 工单触发时由 agent 返回的 action */
  action?: WorkOrderAction | null;
}

export interface KnowledgeSource {
  id: string;
  title: string;
  excerpt: string;
  sourceType: "knowledge_base" | "policy" | "faq";
  metadata: Record<string, string>;
}

export interface WorkOrderAction {
  action: "create_work_order";
  data: {
    title: string;
    description: string;
    type: "售前" | "售后";
    priority: "high" | "medium" | "low";
  };
}

/**
 * 发送给 Server 的请求体
 * 对齐 agentMessageRequestSchema (camelCase)
 */
export interface ChatRequest {
  userInput: string;
  historyOrders: string[];
  userType: number;
  sessionId?: string;
  locale?: string;
}

/**
 * Server 返回的响应体
 * 对齐 agentMessageResponseSchema (camelCase)
 */
export interface ChatResponse {
  sessionId: string;
  answer: string;            // 对齐 shared schema 的 answer 字段
  sources: KnowledgeSource[];
  actions: WorkOrderAction[];
  fallbackReason?: "knowledge_not_found" | "model_unavailable" | "agent_unavailable";
  generatedAt: string;
  routeMeta?: {
    channel: "rag_fast" | "function_calling" | "fallback";
    intent?: string;
    agentId?: string;
  };
}

export interface WidgetConfig {
  /** 服务端 API 地址 */
  apiEndpoint?: string;
  /** 占位提示文本 */
  placeholder?: string;
  /** 欢迎语 */
  welcomeMessage?: string;
  /** 用户类型 */
  userType?: number;
  /** 主题色 */
  themeColor?: string;
  /** 历史购买记录（逗号分隔字符串，向后兼容旧用法） */
  historyOrders?: string;
}
