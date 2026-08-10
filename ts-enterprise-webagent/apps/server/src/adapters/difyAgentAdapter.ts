import type {
  AgentMessageRequest,
  AgentMessageResponse,
  KnowledgeSource,
  AgentMetadata,
  AgentMetrics,
  WorkOrderAnalysisResult,
  SummaryResult
} from "@enterprise-webagent/shared";
import type { IDifyAgent } from "@enterprise-webagent/core";

/**
 * Dify 平台 Agent 适配器
 *
 * 封装 Dify API 调用，实现 IAgent 和 IDifyAgent 接口
 * 业务层通过此适配器与 Dify 平台交互，无需感知底层实现差异
 *
 * 对齐 Backend 的 DifyAdapter + DifyClient
 */
export class DifyAgentAdapter implements IDifyAgent {
  private readonly baseUrl: string;
  private readonly apiKey: string;

  private totalRequests = 0;
  private successCount = 0;
  private fallbackCount = 0;
  private totalResponseTimeMs = 0;
  private lastActiveAt: string | null = null;

  public constructor(public readonly metadata: AgentMetadata) {
    this.baseUrl = metadata.difyConfig?.baseUrl ?? "";
    this.apiKey = metadata.difyConfig?.apiKey ?? "";
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
    request: AgentMessageRequest,
    abortSignal?: AbortSignal
  ): Promise<AgentMessageResponse> {
    const startTime = Date.now();
    this.totalRequests++;
    this.lastActiveAt = new Date().toISOString();

    try {
      const result = await this.sendBlockingMessage(request, abortSignal);
      this.successCount++;
      return {
        sessionId: request.sessionId ?? crypto.randomUUID(),
        answer: result.answer,
        sources: [],
        actions: [],
        generatedAt: new Date().toISOString()
      };
    } catch (error) {
      this.fallbackCount++;
      return {
        sessionId: request.sessionId ?? crypto.randomUUID(),
        answer: "抱歉，Dify 平台目前不可用，请稍后重试。",
        sources: [],
        actions: [],
        fallbackReason: "model_unavailable",
        generatedAt: new Date().toISOString()
      };
    } finally {
      this.totalResponseTimeMs += Date.now() - startTime;
    }
  }

  public async healthCheck(): Promise<{ healthy: boolean; reason?: string }> {
    if (!this.baseUrl || !this.apiKey) {
      return { healthy: false, reason: "Dify baseUrl or apiKey not configured" };
    }

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 5000);

      const response = await fetch(`${this.baseUrl}/parameters`, {
        method: "GET",
        headers: {
          Authorization: `Bearer ${this.apiKey}`
        },
        signal: controller.signal
      });

      clearTimeout(timeout);
      const ok = response.ok;
      if (ok) {
        return { healthy: true };
      }
      return { healthy: false, reason: `HTTP ${response.status}` };
    } catch (error) {
      return {
        healthy: false,
        reason: error instanceof Error ? error.message : "Connection failed"
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
  // IDifyAgent 接口实现
  // ============================================================

  public async sendStreamingMessage(
    request: AgentMessageRequest,
    onData: (chunk: string) => void,
    onError: (error: string) => void,
    abortSignal?: AbortSignal
  ): Promise<void> {
    const url = `${this.baseUrl}${this.metadata.difyConfig?.workflowEndpoint ?? "/chat-messages"}`;

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.apiKey}`
        },
        body: JSON.stringify({
          inputs: {
            userType: request.userType,
            historyOrders: request.historyOrders.join(","),
            locale: request.locale
          },
          query: request.userInput,
          response_mode: "streaming",
          conversation_id: request.sessionId ?? "",
          user: `user_${request.userType}`
        }),
        signal: abortSignal ?? null
      });

      if (!response.ok) {
        onError(`Dify API error: HTTP ${response.status}`);
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        onError("No response body");
        return;
      }

      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";

        for (const line of lines) {
          if (line.startsWith("data: ")) {
            const data = line.substring(6);
            if (data === "[DONE]") continue;
            try {
              const parsed = JSON.parse(data);
              if (parsed.answer) {
                onData(String(parsed.answer));
              }
            } catch {
              onData(data);
            }
          }
        }
      }
    } catch (error) {
      onError(error instanceof Error ? error.message : "Stream error");
    }
  }

  public async analyzeWorkOrder(
    chatHistory: string[],
    workOrderInfo: { title: string; type: string; description: string }
  ): Promise<WorkOrderAnalysisResult | null> {
    if (!this.apiKey || !this.baseUrl) return null;

    const url = `${this.baseUrl}/workflows/run`;

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.apiKey}`
        },
        body: JSON.stringify({
          inputs: {
            chat_history: chatHistory,
            work_order_title: workOrderInfo.title,
            work_order_type: workOrderInfo.type,
            work_order_description: workOrderInfo.description
          },
          response_mode: "blocking",
          user: "system"
        }),
        signal: AbortSignal.timeout(45_000)
      });

      if (!response.ok) return null;

      const data = await response.json();
      const outputs = data?.data?.outputs ?? data;

      return {
        priority: outputs?.priority ?? "medium",
        tags: outputs?.tags,
        summary: outputs?.summary,
        bizTag: outputs?.bizTag,
        emotionLevel: outputs?.emotionLevel,
        dispatchConfidence: outputs?.dispatchConfidence
      };
    } catch {
      return null;
    }
  }

  public async summarizeConversation(
    chatHistory: string[],
    sessionId: string
  ): Promise<SummaryResult | null> {
    if (!this.apiKey || !this.baseUrl) return null;

    const url = `${this.baseUrl}/workflows/run`;

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.apiKey}`
        },
        body: JSON.stringify({
          inputs: {
            chat_history: chatHistory,
            session_id: sessionId
          },
          response_mode: "blocking",
          user: "system"
        }),
        signal: AbortSignal.timeout(45_000)
      });

      if (!response.ok) return null;

      const data = await response.json();
      const outputs = data?.data?.outputs ?? data;

      return {
        priority: outputs?.priority,
        summary: outputs?.summary,
        tags: outputs?.tags
      };
    } catch {
      return null;
    }
  }

  // ============================================================
  // 私有方法
  // ============================================================

  private async sendBlockingMessage(
    request: AgentMessageRequest,
    abortSignal?: AbortSignal
  ): Promise<{ answer: string; conversationId: string }> {
    const url = `${this.baseUrl}/chat-messages`;

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${this.apiKey}`
      },
      body: JSON.stringify({
        inputs: {
          userType: request.userType,
          historyOrders: request.historyOrders.join(","),
          locale: request.locale
        },
        query: request.userInput,
        response_mode: "blocking",
        conversation_id: request.sessionId ?? "",
        user: `user_${request.userType}`
      }),
      signal: abortSignal ?? null
    });

    if (!response.ok) {
      throw new Error(`Dify API error: HTTP ${response.status}`);
    }

    const data = await response.json();
    return {
      answer: data.answer ?? "抱歉，未能获取到有效回复。",
      conversationId: data.conversation_id ?? ""
    };
  }
}
