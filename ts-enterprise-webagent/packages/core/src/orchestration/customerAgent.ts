import {
  agentMessageRequestSchema,
  type AgentMessageRequest,
  type AgentMessageResponse
} from "@enterprise-webagent/shared";

import type { CustomerAgent, CustomerAgentDependencies } from "../adapters/contracts.js";
import { buildCustomerSystemPrompt } from "../domain/prompt.js";
import { detectWorkOrderAction } from "../domain/workOrderIntent.js";

const KNOWLEDGE_LIMIT = 5;
const FALLBACK_MESSAGE =
  "抱歉，目前的知识库暂未收录该特定参数，建议联系专属人工顾问。";

export class CustomerAgentModule implements CustomerAgent {
  private readonly now: () => Date;

  public constructor(private readonly dependencies: CustomerAgentDependencies) {
    this.now = dependencies.now ?? (() => new Date());
  }

  public async reply(rawRequest: AgentMessageRequest): Promise<AgentMessageResponse> {
    const request = agentMessageRequestSchema.parse(rawRequest);
    const sessionId = request.sessionId ?? crypto.randomUUID();
    const actions = detectWorkOrderAction(request.userInput);

    const sources = await this.dependencies.knowledgeRetriever.search({
      query: request.userInput,
      userType: request.userType,
      limit: KNOWLEDGE_LIMIT
    });

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

    const systemPrompt = buildCustomerSystemPrompt(request, sources);

    try {
      const answer = await this.dependencies.chatModel.generate({
        systemPrompt,
        userMessage: request.userInput,
        sources
      });

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
