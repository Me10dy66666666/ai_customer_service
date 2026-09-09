import { Agent, type AgentTool } from "@earendil-works/pi-agent-core";
import {
  contentText,
  createModels,
  uuidv7,
  type Model,
} from "@earendil-works/pi-ai";
import { deepseekProvider } from "@earendil-works/pi-ai/providers/deepseek";
import { openaiProvider } from "@earendil-works/pi-ai/providers/openai";
import type { RuntimeConfig } from "./config.js";
import { JavaGateway, type RuntimeContext } from "./java-gateway.js";
import { createCustomerServiceTools } from "./tools.js";

export interface AgentRequest {
  query: string;
  conversation_id?: string;
  inputs?: Record<string, unknown>;
}

export interface AgentEvent {
  type: "text-delta" | "tool-call";
  text?: string;
  tool?: string;
}

export interface AgentResponse {
  answer: string;
  conversation_id: string;
  runtime: "pi";
  tool_calls: string[];
}

export class RuntimeError extends Error {
  constructor(
    public readonly code: "RUNTIME_UNAVAILABLE" | "SESSION_BUSY" | "INVALID_REQUEST",
    message: string,
    public readonly status = 503,
  ) {
    super(message);
    this.name = "RuntimeError";
  }
}

interface Session {
  id: string;
  agent: Agent;
  context: RuntimeContext;
  roles: string[];
  busy: boolean;
  lastUsedAt: number;
}

const SYSTEM_PROMPT = [
  "You are the customer service agent for the product.",
  "Answer clearly and briefly in the user's language.",
  "Use search_knowledge for policy, product, and process facts instead of inventing them.",
  "Use lookup_order only when the user asks about their own orders. Never ask for, infer, or pass a user ID.",
  "Use create_work_order_proposal only when a work order is appropriate. It creates a proposal only; tell the user that UI confirmation is required.",
  "Java is the authority for identity, permissions, order data, proposals, and durable writes.",
  "Never claim that a durable write happened unless the Java gateway confirms it.",
].join("\n");

export class CustomerServiceAgentRuntime {
  private readonly sessions = new Map<string, Session>();
  private readonly models = createModels();
  private readonly provider;
  private readonly model: Model<any>;

  constructor(
    private readonly config: RuntimeConfig,
    private readonly gateway = new JavaGateway(config),
  ) {
    if (config.modelProvider !== "deepseek" && config.modelProvider !== "openai") {
      throw new RuntimeError("RUNTIME_UNAVAILABLE", "unsupported model provider");
    }
    this.provider = config.modelProvider === "openai" ? openaiProvider() : deepseekProvider();
    this.models.setProvider(this.provider);
    const catalogModel = this.provider.getModels().find((candidate: Model<any>) => candidate.id === config.modelName)
      ?? this.provider.getModels()[0];
    if (!catalogModel) {
      throw new RuntimeError("RUNTIME_UNAVAILABLE", "configured model is unavailable");
    }
    this.model = {
      ...catalogModel,
      id: config.modelName,
      name: config.modelName,
      ...(config.modelBaseUrl ? { baseUrl: config.modelBaseUrl } : {}),
    };
  }

  async run(
    request: AgentRequest,
    capabilityToken: string,
    onEvent?: (event: AgentEvent) => void,
  ): Promise<AgentResponse> {
    if (!request.query?.trim()) {
      throw new RuntimeError("INVALID_REQUEST", "query is required", 400);
    }
    if (!capabilityToken) {
      throw new RuntimeError("INVALID_REQUEST", "agent capability is required", 401);
    }

    const sessionId = request.conversation_id?.trim() || uuidv7();
    const session = this.getOrCreateSession(sessionId, capabilityToken, request.inputs);
    if (session.busy) {
      throw new RuntimeError("SESSION_BUSY", "agent session is already processing a request", 409);
    }
    session.busy = true;
    session.lastUsedAt = Date.now();
    session.context = { ...session.context, capabilityToken };
    session.roles = readRoles(request.inputs?.roles);

    const toolCalls: string[] = [];
    let streamedText = "";
    const unsubscribe = session.agent.subscribe((event) => {
      if (event.type === "message_update" && event.assistantMessageEvent.type === "text_delta") {
        streamedText += event.assistantMessageEvent.delta;
        onEvent?.({ type: "text-delta", text: event.assistantMessageEvent.delta });
      }
      if (event.type === "tool_execution_start") {
        toolCalls.push(event.toolName);
        onEvent?.({ type: "tool-call", tool: event.toolName });
      }
    });

    try {
      await session.agent.prompt(request.query.trim());
      const assistant = [...session.agent.state.messages]
        .reverse()
        .find((message) => message.role === "assistant");
      if (!assistant || assistant.role !== "assistant") {
        throw new RuntimeError("RUNTIME_UNAVAILABLE", "agent returned no assistant response");
      }
      if (assistant.stopReason === "error" || assistant.stopReason === "aborted") {
        throw new RuntimeError("RUNTIME_UNAVAILABLE", "model request failed");
      }
      const answer = streamedText || contentText(assistant.content);
      if (!answer) {
        throw new RuntimeError("RUNTIME_UNAVAILABLE", "agent returned an empty response");
      }
      return { answer, conversation_id: sessionId, runtime: "pi", tool_calls: toolCalls };
    } catch (error) {
      if (error instanceof RuntimeError) throw error;
      throw new RuntimeError("RUNTIME_UNAVAILABLE", "agent runtime is unavailable");
    } finally {
      unsubscribe();
      session.busy = false;
      session.lastUsedAt = Date.now();
      this.evictIdleSessions();
    }
  }

  private getOrCreateSession(
    sessionId: string,
    capabilityToken: string,
    inputs: Record<string, unknown> | undefined,
  ): Session {
    const existing = this.sessions.get(sessionId);
    if (existing) return existing;

    const context: RuntimeContext = {
      sessionId,
      capabilityToken,
      roles: readRoles(inputs?.roles),
    };
    let session!: Session;
    const tools: AgentTool[] = createCustomerServiceTools({
      gateway: this.gateway,
      getRuntimeContext: () => session.context,
      getRoles: () => session.roles,
    });
    const agent = new Agent({
      sessionId: `pi-${sessionId}`,
      initialState: {
        systemPrompt: SYSTEM_PROMPT,
        model: this.model,
        thinkingLevel: "off",
        tools,
        messages: [],
      },
      streamFn: (model, context, options) => this.models.streamSimple(model, context, {
        ...options,
        timeoutMs: this.config.modelTimeoutMs,
      }),
      getApiKey: () => this.config.modelApiKey,
      maxRetryDelayMs: 10_000,
      toolExecution: "sequential",
    });
    session = { id: sessionId, agent, context, roles: context.roles, busy: false, lastUsedAt: Date.now() };
    this.sessions.set(sessionId, session);
    this.evictIdleSessions();
    return session;
  }

  private evictIdleSessions(): void {
    if (this.sessions.size <= this.config.maxSessions) return;
    const idle = [...this.sessions.values()]
      .filter((session) => !session.busy)
      .sort((left, right) => left.lastUsedAt - right.lastUsedAt);
    while (this.sessions.size > this.config.maxSessions && idle.length > 0) {
      this.sessions.delete(idle.shift()!.id);
    }
  }
}

function readRoles(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((role): role is string => typeof role === "string").slice(0, 20)
    : [];
}
