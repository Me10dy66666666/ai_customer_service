import { Type } from "@earendil-works/pi-ai";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import type { Static } from "typebox";
import type { JavaGateway, RuntimeContext } from "./java-gateway.js";
import { asToolText } from "./java-gateway.js";

interface ToolContext {
  getRuntimeContext: () => RuntimeContext;
  getRoles: () => string[];
  gateway: JavaGateway;
}

export function createCustomerServiceTools(context: ToolContext): AgentTool[] {
  const searchKnowledgeParameters = Type.Object({
    query: Type.String({ minLength: 1, maxLength: 500 }),
    limit: Type.Optional(Type.Integer({ minimum: 1, maximum: 10 })),
  });
  const lookupOrderParameters = Type.Object({});
  const workOrderParameters = Type.Object({
    title: Type.String({ minLength: 1, maxLength: 120 }),
    description: Type.String({ minLength: 1, maxLength: 2000 }),
    type: Type.Optional(Type.String({ maxLength: 40 })),
    priority: Type.Optional(Type.String({ maxLength: 20 })),
  });

  const searchKnowledge: AgentTool<typeof searchKnowledgeParameters> = {
      name: "search_knowledge",
      label: "Search knowledge",
      description: "Search approved customer-service knowledge. Use this for policy, product, and process questions.",
      parameters: searchKnowledgeParameters,
      execute: async (_toolCallId, params: Static<typeof searchKnowledgeParameters>, signal) => {
        const result = await context.gateway.searchKnowledge(
          params.query,
          params.limit ?? 5,
          context.getRoles(),
          signal,
        );
        return { content: asToolText(result), details: { source: "data-pipeline" } };
      },
    };
  const lookupOrder: AgentTool<typeof lookupOrderParameters> = {
      name: "lookup_order",
      label: "Look up own orders",
      description: "Read orders belonging to the authenticated customer. The customer identity is supplied by Java, never by the model.",
      parameters: lookupOrderParameters,
      execute: async (_toolCallId, _params, signal) => {
        const result = await context.gateway.lookupOwnOrders(context.getRuntimeContext(), signal);
        return { content: asToolText(result), details: { source: "java-domain" } };
      },
    };
  const createWorkOrderProposal: AgentTool<typeof workOrderParameters> = {
      name: "create_work_order_proposal",
      label: "Create work-order proposal",
      description: "Create a proposal for the customer to review. This never creates a durable work order; UI confirmation is required.",
      parameters: workOrderParameters,
      execute: async (_toolCallId, params: Static<typeof workOrderParameters>, signal) => {
        const result = await context.gateway.proposeWorkOrder(context.getRuntimeContext(), params, signal);
        return {
          content: asToolText(result),
          details: { source: "java-domain", requiresConfirmation: true },
        };
      },
    };
  return [searchKnowledge, lookupOrder, createWorkOrderProposal];
}
