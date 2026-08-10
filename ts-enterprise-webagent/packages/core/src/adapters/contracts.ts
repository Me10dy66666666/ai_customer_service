import type {
  KnowledgeSource
} from "@enterprise-webagent/shared";

// ============================================================
// 核心适配器接口（保持向后兼容）
// ============================================================

export interface KnowledgeRetriever {
  search(input: {
    query: string;
    userType: number;
    limit: number;
    signal?: AbortSignal;
  }): Promise<KnowledgeSource[]>;
}

export interface ChatModel {
  generate(input: {
    systemPrompt: string;
    userMessage: string;
    sources: KnowledgeSource[];
    signal?: AbortSignal;
  }): Promise<string>;
}

export interface Logger {
  info(message: string, context?: Record<string, unknown>): void;
  warn(message: string, context?: Record<string, unknown>): void;
  error(message: string, context?: Record<string, unknown>): void;
}

export interface CustomerAgentDependencies {
  knowledgeRetriever: KnowledgeRetriever;
  chatModel: ChatModel;
  logger: Logger;
  now?: () => Date;
}

export interface CustomerAgent {
  reply(request: import("@enterprise-webagent/shared").AgentMessageRequest): Promise<import("@enterprise-webagent/shared").AgentMessageResponse>;
}

// ============================================================
// 增强型适配器接口（对齐 Backend Dify AI 能力）
// ============================================================

/**
 * 知识库管理适配器（对齐 Backend KnowledgeBasePort）
 */
export interface KnowledgeBaseManager {
  /** 上传文档到知识库 */
  uploadDocument(file: Buffer, filename: string, datasetId: string): Promise<string>;
  /** 删除文档 */
  deleteDocument(datasetId: string, documentId: string): Promise<void>;
  /** 获取数据集信息 */
  getDataset(datasetId: string): Promise<Record<string, unknown>>;
  /** 更新文档启用/禁用状态 */
  updateDocumentStatus(datasetId: string, documentId: string, enabled: boolean): Promise<void>;
  /** 分页列出文档 */
  listDocuments(datasetId: string, page: number, limit: number): Promise<Array<Record<string, unknown>>>;
  /** 列出所有文档 */
  listAllDocuments(datasetId: string): Promise<Array<Record<string, unknown>>>;
}

/**
 * 会话状态适配器（对齐 Backend SessionStatePort）
 */
export interface SessionStateStore {
  /** 获取会话状态 */
  getSession(sessionId: string): Promise<SessionState | null>;
  /** 保存会话状态 */
  saveSession(state: SessionState): Promise<void>;
  /** 删除会话 */
  deleteSession(sessionId: string): Promise<void>;
  /** 设置 AI 阻断状态（转人工后阻断 AI 回复） */
  setAiBlocked(sessionId: string, blocked: boolean): Promise<void>;
  /** 检查 AI 是否被阻断 */
  isAiBlocked(sessionId: string): Promise<boolean>;
}

export interface SessionState {
  sessionId: string;
  userId: string;
  userType: number;
  conversationId?: string;
  chatHistory: ChatMessageRecord[];
  aiBlocked: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessageRecord {
  role: "user" | "ai" | "agent";
  content: string;
  timestamp: string;
}

/**
 * 工单操作适配器
 */
export interface WorkOrderPort {
  /** 创建工单 */
  createWorkOrder(data: {
    title: string;
    description: string;
    type: string;
    priority: string;
    userId: string;
  }): Promise<{ workOrderId: string }>;
  /** 更新工单 AI 分析结果 */
  updateAiAnalysis(workOrderId: string, analysis: {
    priority?: string;
    tags?: string;
    summary?: string;
    bizTag?: string;
    emotionLevel?: string;
    dispatchConfidence?: number;
  }): Promise<void>;
}
