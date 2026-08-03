import type {
  AgentMessageRequest,
  KnowledgeSource
} from "@enterprise-webagent/shared";

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
  reply(request: AgentMessageRequest): Promise<import("@enterprise-webagent/shared").AgentMessageResponse>;
}
