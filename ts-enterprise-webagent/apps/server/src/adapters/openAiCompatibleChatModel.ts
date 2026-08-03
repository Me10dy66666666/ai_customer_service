import type { ChatModel, Logger } from "@enterprise-webagent/core";
import type { KnowledgeSource } from "@enterprise-webagent/shared";

export class MockChatModel implements ChatModel {
  public async generate(input: {
    systemPrompt: string;
    userMessage: string;
    sources: KnowledgeSource[];
    signal?: AbortSignal;
  }): Promise<string> {
    if (input.signal?.aborted) {
      throw new DOMException("Model generation aborted", "AbortError");
    }

    const summarizedSources = input.sources
      .slice(0, 3)
      .map((source) => `- **${source.title}**：${source.excerpt}`)
      .join("\n");

    return [
      "根据当前知识库，我为您整理如下：",
      summarizedSources,
      "",
      `针对您的问题“${input.userMessage}”，建议优先按照上述规则处理；如需人工继续跟进，可直接提交工单。`
    ].join("\n");
  }
}

export class OpenAiCompatibleChatModel implements ChatModel {
  public constructor(
    private readonly options: {
      baseUrl: string;
      apiKey: string;
      model: string;
      logger: Logger;
    }
  ) {}

  public async generate(input: {
    systemPrompt: string;
    userMessage: string;
    sources: KnowledgeSource[];
    signal?: AbortSignal;
  }): Promise<string> {
    const response = await fetch(`${this.options.baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${this.options.apiKey}`
      },
      signal: input.signal ?? null as AbortSignal | null,
      body: JSON.stringify({
        model: this.options.model,
        temperature: 0.2,
        messages: [
          { role: "system", content: input.systemPrompt },
          { role: "user", content: input.userMessage }
        ]
      })
    });

    if (!response.ok) {
      const details = await response.text();
      this.options.logger.error("OpenAI compatible model returned non-2xx", {
        statusCode: response.status,
        details
      });
      throw new Error(`Model request failed with status ${response.status}`);
    }

    const payload = await response.json() as {
      choices?: Array<{ message?: { content?: string } }>;
    };

    const answer = payload.choices?.[0]?.message?.content?.trim();
    if (!answer) {
      throw new Error("Model payload did not contain answer content");
    }

    return answer;
  }
}
