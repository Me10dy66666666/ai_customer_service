import type {
  AgentMessageRequest,
  AgentMessageResponse,
  KnowledgeSource
} from "@enterprise-webagent/shared";
import type { IAgent, ICustomAgent, IDifyAgent } from "../agents/IAgent.js";
import { isCustomAgent, isDifyAgent } from "../agents/IAgent.js";
import type { AgentRegistry } from "../agents/AgentRegistry.js";
import { classifyIntent, requiresFunctionCalling, canUseRagFastPath } from "./intentClassifier.js";
import type { IntentLabel } from "./intentClassifier.js";
import type { Logger } from "../adapters/contracts.js";

// ============================================================
// 路由调度器
//
// 根据意图分类结果，将请求路由到：
// 1. RAG 快速通道（FAQ/闲聊，使用自定义 Agent）
// 2. Function Calling 通道（订单/工单/转人工，使用自定义 Agent 或 Dify Agent）
// 3. 兜底策略（无可用 Agent）
// ============================================================

export interface RouteContext {
  channel: "rag_fast" | "function_calling" | "fallback";
  intent: IntentLabel;
  agentId: string;
}

export class RouteDispatcher {
  public constructor(
    private readonly registry: AgentRegistry,
    private readonly logger: Logger
  ) {}

  /**
   * 核心调度方法
   * 根据用户输入意图选择合适的 Agent 处理
   */
  public async dispatch(
    request: AgentMessageRequest,
    abortSignal?: AbortSignal
  ): Promise<AgentMessageResponse> {
    const intentResult = classifyIntent(request.userInput);
    const routeCtx = this.resolveRoute(intentResult.label);

    this.logger.info("Route resolved", {
      sessionId: request.sessionId,
      intent: intentResult.label,
      confidence: intentResult.confidence,
      channel: routeCtx.channel,
      agentId: routeCtx.agentId
    });

    const agent = this.registry.get(routeCtx.agentId);
    if (!agent) {
      return this.buildFallbackResponse(request);
    }

    try {
      const response = await agent.reply(request, abortSignal);
      // 附加路由元信息
      return {
        ...response,
        routeMeta: {
          channel: routeCtx.channel,
          intent: intentResult.label,
          agentId: routeCtx.agentId
        }
      };
    } catch (error) {
      this.logger.error("Agent reply failed, attempting fallback", {
        agentId: routeCtx.agentId,
        error: error instanceof Error ? error.message : String(error)
      });

      // 尝试降级到备用 Agent
      return this.tryFallbackAgent(request, routeCtx, abortSignal);
    }
  }

  /**
   * 使用指定 Agent 进行工单分析（对齐 Backend ChatSummaryService 的工单分析功能）
   */
  public async analyzeWorkOrder(
    chatHistory: string[],
    workOrderInfo: { title: string; type: string; description: string }
  ): Promise<Record<string, unknown> | null> {
    // 优先使用 Dify 工单分析 Agent
    const difyAgent = this.registry.get("dify-workorder-agent");
    if (difyAgent && isDifyAgent(difyAgent)) {
      return difyAgent.analyzeWorkOrder(chatHistory, workOrderInfo);
    }

    // 降级：使用自定义 Agent 做基础分析
    const customAgent = this.registry.get("custom-customer-agent");
    if (customAgent && isCustomAgent(customAgent)) {
      const sentiment = await customAgent.analyzeSentiment(
        `用户问题：${workOrderInfo.title}\n${workOrderInfo.description}`,
        chatHistory
      );

      const isUrgent = sentiment.emotionLevel === "angry" || sentiment.emotionLevel === "negative";
      return {
        priority: isUrgent ? "high" : "medium",
        summary: workOrderInfo.title,
        emotionLevel: sentiment.emotionLevel,
        dispatchConfidence: sentiment.confidence
      };
    }

    return null;
  }

  /**
   * 使用指定 Agent 进行对话摘要（对齐 Backend ChatSummaryService 的转人工总结功能）
   */
  public async summarizeConversation(
    chatHistory: string[],
    sessionId: string
  ): Promise<Record<string, unknown> | null> {
    const difyAgent = this.registry.get("dify-transfer-agent");
    if (difyAgent && isDifyAgent(difyAgent)) {
      return difyAgent.summarizeConversation(chatHistory, sessionId);
    }

    // 降级：使用自定义 Agent 做基础摘要
    const customAgent = this.registry.get("custom-customer-agent");
    if (customAgent && isCustomAgent(customAgent)) {
      const sentiment = await customAgent.analyzeSentiment(
        chatHistory.join("\n"),
        []
      );
      const urgencyLevel = sentiment.emotionLevel === "angry" || sentiment.emotionLevel === "negative"
        ? "high" : "medium";

      return {
        priority: urgencyLevel,
        summary: `对话共 ${chatHistory.length} 轮，用户情绪：${sentiment.emotionLevel}`,
        tags: sentiment.keywords.join(",")
      };
    }

    return null;
  }

  // ============================================================
  // 私有方法
  // ============================================================

  /**
   * 根据意图解析路由目标
   */
  private resolveRoute(intent: IntentLabel): RouteContext {
    if (canUseRagFastPath(intent)) {
      // RAG 快速通道：优先使用自定义 Agent
      const customAgent = this.registry.get("custom-customer-agent");
      if (customAgent?.metadata.enabled) {
        return {
          channel: "rag_fast",
          intent,
          agentId: customAgent.metadata.id
        };
      }
    }

    if (requiresFunctionCalling(intent)) {
      // Function Calling 通道：优先尝试 Dify Agent，降级到自定义 Agent
      if (intent === "transfer" || intent === "work_order") {
        const difyAgent = this.registry.get("dify-chat-agent");
        if (difyAgent?.metadata.enabled) {
          return {
            channel: "function_calling",
            intent,
            agentId: difyAgent.metadata.id
          };
        }
      }

      const customAgent = this.registry.get("custom-customer-agent");
      if (customAgent?.metadata.enabled) {
        return {
          channel: "function_calling",
          intent,
          agentId: customAgent.metadata.id
        };
      }
    }

    // 最终兜底：任意可用 Agent
    const fallbackAgent = this.registry.getEnabled()[0];
    if (fallbackAgent) {
      return {
        channel: "rag_fast",
        intent,
        agentId: fallbackAgent.metadata.id
      };
    }

    return {
      channel: "fallback",
      intent,
      agentId: "none"
    };
  }

  /**
   * 尝试使用备用 Agent 处理
   */
  private async tryFallbackAgent(
    request: AgentMessageRequest,
    originalRoute: RouteContext,
    abortSignal?: AbortSignal
  ): Promise<AgentMessageResponse> {
    const enabledAgents = this.registry.getEnabled();
    for (const agent of enabledAgents) {
      if (agent.metadata.id === originalRoute.agentId) continue;
      try {
        const response = await agent.reply(request, abortSignal);
        return {
          ...response,
          routeMeta: {
            channel: "fallback",
            intent: originalRoute.intent,
            agentId: agent.metadata.id
          }
        };
      } catch {
        // 继续尝试下一个
      }
    }

    return this.buildFallbackResponse(request);
  }

  /**
   * 构建兜底响应
   */
  private buildFallbackResponse(
    request: AgentMessageRequest
  ): AgentMessageResponse {
    return {
      sessionId: request.sessionId ?? crypto.randomUUID(),
      answer: "抱歉，当前所有智能客服均暂时不可用，请稍后重试或联系人工客服。",
      sources: [],
      actions: [],
      generatedAt: new Date().toISOString()
    };
  }
}
