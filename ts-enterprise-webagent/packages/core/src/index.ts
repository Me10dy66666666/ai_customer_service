// Adapter contracts
export type {
  KnowledgeRetriever,
  ChatModel,
  Logger,
  CustomerAgentDependencies,
  CustomerAgent,
  KnowledgeBaseManager,
  SessionStateStore,
  SessionState,
  ChatMessageRecord,
  WorkOrderPort
} from "./adapters/contracts.js";

// Domain models
export { buildCustomerSystemPrompt } from "./domain/prompt.js";
export { detectWorkOrderAction } from "./domain/workOrderIntent.js";
export { CustomerAgentError } from "./domain/errors.js";

// Agents
export type { IAgent, IDifyAgent, ICustomAgent } from "./agents/IAgent.js";
export { isDifyAgent, isCustomAgent } from "./agents/IAgent.js";
export { AgentRegistry } from "./agents/AgentRegistry.js";
export { CustomCustomerAgent } from "./agents/CustomCustomerAgent.js";

// Routing
export type { IntentLabel, IntentResult } from "./routing/intentClassifier.js";
export { classifyIntent, requiresFunctionCalling, canUseRagFastPath } from "./routing/intentClassifier.js";
export type { RouteContext } from "./routing/routeDispatcher.js";
export { RouteDispatcher } from "./routing/routeDispatcher.js";

// Config
export type { SensitiveConfig, AgentSystemConfig } from "./config/agentConfig.js";
export { sensitiveConfigSchema, loadSensitiveConfig, buildDefaultAgents } from "./config/agentConfig.js";

// Services
export { ChatSummaryService } from "./services/chatSummary.js";
export { SentimentAnalysisService } from "./services/sentimentAnalysis.js";

// Utilities
export { consoleLogger } from "./utils/logger.js";

// Re-export old CustomerAgentModule for backward compatibility
export { CustomerAgentModule } from "./orchestration/customerAgent.js";
