import type {
  AgentMessageRequest,
  AgentMessageResponse,
  AgentMetadata,
  AgentMetrics,
  WorkOrderAnalysisResult,
  SummaryResult,
  SentimentResult
} from "@enterprise-webagent/shared";

// ============================================================
// 标准化 Agent 调用接口
// 无论是 Dify 侧对接的 Agent 还是自研自定义 Agent，都实现此接口
// 业务层可无感知切换
// ============================================================

/**
 * Agent 核心对话接口
 * 所有 Agent 实现必须实现此接口
 */
export interface IAgent {
  /** Agent 元信息 */
  readonly metadata: AgentMetadata;

  /** 当前运行指标 */
  readonly metrics: AgentMetrics;

  /**
   * 发送消息并获取回复（核心对话方法）
   * @param request 用户请求
   * @param abortSignal 可选的取消信号
   */
  reply(request: AgentMessageRequest, abortSignal?: AbortSignal): Promise<AgentMessageResponse>;

  /**
   * 健康检查：Agent 是否可用
   */
  healthCheck(): Promise<{ healthy: boolean; reason?: string }>;

  /**
   * 获取当前运行指标快照
   */
  getMetrics(): AgentMetrics;

  /**
   * 重置指标计数器
   */
  resetMetrics(): void;
}

/**
 * Dify 平台 Agent 扩展接口
 * Dify 特有的能力通过此接口暴露
 */
export interface IDifyAgent extends IAgent {
  /**
   * 流式发送消息（Dify SSE 流式响应）
   * @param request 用户请求
   * @param onData 流式数据回调
   * @param onError 错误回调
   * @param abortSignal 取消信号
   */
  sendStreamingMessage(
    request: AgentMessageRequest,
    onData: (chunk: string) => void,
    onError: (error: string) => void,
    abortSignal?: AbortSignal
  ): Promise<void>;

  /**
   * 工单分析工作流
   * @param chatHistory 会话对话历史
   * @param workOrderInfo 工单基本信息
   */
  analyzeWorkOrder(
    chatHistory: string[],
    workOrderInfo: { title: string; type: string; description: string }
  ): Promise<WorkOrderAnalysisResult | null>;

  /**
   * 转人工总结工作流
   * @param chatHistory 会话对话历史
   * @param sessionId 会话ID
   */
  summarizeConversation(
    chatHistory: string[],
    sessionId: string
  ): Promise<SummaryResult | null>;
}

/**
 * 自定义 Agent 扩展接口
 * 自研 Agent 的特有能力
 */
export interface ICustomAgent extends IAgent {
  /**
   * 情绪分析
   * @param userInput 用户输入文本
   * @param chatHistory 可选的对话历史
   */
  analyzeSentiment(
    userInput: string,
    chatHistory?: string[]
  ): Promise<SentimentResult>;

  /**
   * 工单意图检测
   * @param userInput 用户输入
   */
  detectWorkOrderIntent(userInput: string): boolean;

  /**
   * 设置启用/禁用状态
   */
  setEnabled(enabled: boolean): void;

  /**
   * 更新模型配置
   */
  updateModelConfig(config: { modelMode: string; modelName: string }): void;
}

/**
 * 类型守卫：判断是否为 Dify Agent
 */
export function isDifyAgent(agent: IAgent): agent is IDifyAgent {
  return agent.metadata.type === "dify";
}

/**
 * 类型守卫：判断是否为自定义 Agent
 */
export function isCustomAgent(agent: IAgent): agent is ICustomAgent {
  return agent.metadata.type === "custom";
}
