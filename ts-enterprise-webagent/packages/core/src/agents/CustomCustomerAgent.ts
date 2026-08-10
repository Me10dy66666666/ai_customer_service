import {
  agentMessageRequestSchema,
  type AgentMessageRequest,
  type AgentMessageResponse,
  type KnowledgeSource,
  type AgentMetadata,
  type AgentMetrics,
  type SentimentResult
} from "@enterprise-webagent/shared";

import type { ICustomAgent } from "./IAgent.js";
import type { CustomerAgentDependencies } from "../adapters/contracts.js";
import { buildCustomerSystemPrompt } from "../domain/prompt.js";
import { detectWorkOrderAction } from "../domain/workOrderIntent.js";
import { SentimentAnalysisService } from "../services/sentimentAnalysis.js";
import { CustomerAgentError } from "../domain/errors.js";

const KNOWLEDGE_LIMIT = 5;
const FALLBACK_MESSAGE = "抱歉，目前的知识库暂未收录该特定参数，建议联系专属人工顾问。";

/**
 * 自定义智能客服 Agent
 *
 * 实现 ICustomAgent 接口，基于 RAG + Function Calling 混合架构
 * 对齐 Backend 的 AiChatPort + DifyAdapter 能力
 */
export class CustomCustomerAgent implements ICustomAgent {
  private readonly now: () => Date;
  private readonly sentimentService: SentimentAnalysisService;

  // 指标
  private totalRequests = 0;
  private successCount = 0;
  private fallbackCount = 0;
  private totalResponseTimeMs = 0;
  private lastActiveAt: string | null = null;

  public constructor(
    public readonly metadata: AgentMetadata,
    private readonly dependencies: CustomerAgentDependencies
  ) {
    this.now = dependencies.now ?? (() => new Date());
    this.sentimentService = new SentimentAnalysisService();
  }

  // ============================================================
  // IAgent 接口实现
  // ============================================================

  public get metrics(): AgentMetrics {
    return {
      agentId: this.metadata.id,
      totalRequests: this.totalRequests,
      successCount: this.successCount,
      fallbackCount: this.fallbackCount,
      avgResponseTimeMs:
        this.totalRequests > 0
          ? Math.round(this.totalResponseTimeMs / this.totalRequests)
          : 0,
      lastActiveAt: this.lastActiveAt ?? undefined
    };
  }

  public async reply(
    rawRequest: AgentMessageRequest,
    abortSignal?: AbortSignal
  ): Promise<AgentMessageResponse> {
    const startTime = Date.now();
    this.totalRequests++;
    this.lastActiveAt = this.now().toISOString();

    try {
      const result = await this.processReply(rawRequest, abortSignal);
      this.successCount++;
      return result;
    } catch (error) {
      this.fallbackCount++;
      throw error;
    } finally {
      this.totalResponseTimeMs += Date.now() - startTime;
    }
  }

  public async healthCheck(): Promise<{ healthy: boolean; reason?: string }> {
    try {
      // 快速检查：尝试一次简单的知识检索
      const sources = await this.dependencies.knowledgeRetriever.search({
        query: "health_check",
        userType: 0,
        limit: 1
      });
      return { healthy: true };
    } catch (error) {
      return {
        healthy: false,
        reason: error instanceof Error ? error.message : "Unknown error"
      };
    }
  }

  public getMetrics(): AgentMetrics {
    return this.metrics;
  }

  public resetMetrics(): void {
    this.totalRequests = 0;
    this.successCount = 0;
    this.fallbackCount = 0;
    this.totalResponseTimeMs = 0;
    this.lastActiveAt = null;
  }

  // ============================================================
  // ICustomAgent 接口实现
  // ============================================================

  public async analyzeSentiment(
    userInput: string,
    chatHistory?: string[]
  ): Promise<SentimentResult> {
    return this.sentimentService.analyze(userInput, chatHistory);
  }

  public detectWorkOrderIntent(userInput: string): boolean {
    const actions = detectWorkOrderAction(userInput);
    return actions.length > 0;
  }

  public setEnabled(enabled: boolean): void {
    this.metadata.enabled = enabled;
    this.metadata.updatedAt = this.now().toISOString();
  }

  public updateModelConfig(config: { modelMode: string; modelName: string }): void {
    if (this.metadata.customConfig) {
      this.metadata.customConfig.modelMode = config.modelMode as "mock" | "openai-compatible";
      this.metadata.customConfig.modelName = config.modelName;
      this.metadata.updatedAt = this.now().toISOString();
    }
  }

  // ============================================================
  // 核心处理逻辑
  // ============================================================

  private async processReply(
    rawRequest: AgentMessageRequest,
    abortSignal?: AbortSignal
  ): Promise<AgentMessageResponse> {
    const request = agentMessageRequestSchema.parse(rawRequest);
    const sessionId = request.sessionId ?? crypto.randomUUID();
    const actions = detectWorkOrderAction(request.userInput);

    // 知识检索
    const searchInput: Parameters<typeof this.dependencies.knowledgeRetriever.search>[0] = {
      query: request.userInput,
      userType: request.userType,
      limit: KNOWLEDGE_LIMIT
    };
    if (abortSignal) {
      searchInput.signal = abortSignal;
    }
    const sources = await this.dependencies.knowledgeRetriever.search(searchInput);

    if (sources.length === 0) {
      this.dependencies.logger.warn("Knowledge lookup returned no documents", {
        sessionId,
        userType: request.userType
      });

      return {
        sessionId,
        answer: FALLBACK_MESSAGE,
        sources: [],
        actions,
        fallbackReason: "knowledge_not_found",
        generatedAt: this.now().toISOString()
      };
    }

    // 构建 Prompt 并调用大模型
    const systemPrompt = buildCustomerSystemPrompt(request, sources);

    try {
      const generateInput: Parameters<typeof this.dependencies.chatModel.generate>[0] = {
        systemPrompt,
        userMessage: request.userInput,
        sources
      };
      if (abortSignal) {
        generateInput.signal = abortSignal;
      }
      const answer = await this.dependencies.chatModel.generate(generateInput);

      return {
        sessionId,
        answer,
        sources,
        actions,
        generatedAt: this.now().toISOString()
      };
    } catch (error) {
      this.dependencies.logger.error("Model generation failed", {
        sessionId,
        error: error instanceof Error ? error.message : String(error)
      });

      return {
        sessionId,
        answer: `${FALLBACK_MESSAGE}\n\n当前模型服务暂不可用，已为您切换为保守回复模式。`,
        sources,
        actions,
        fallbackReason: "model_unavailable",
        generatedAt: this.now().toISOString()
      };
    }
  }
}
