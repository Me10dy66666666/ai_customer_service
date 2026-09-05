import { describe, expect, it } from "vitest";
import type { AgentMessageRequest, KnowledgeSource } from "@enterprise-webagent/shared";
import {
  buildCustomerContext,
  buildCustomerSystemPrompt,
  CUSTOMER_PROMPT_VERSION
} from "../domain/prompt.js";

const source: KnowledgeSource = {
  id: "source-1",
  title: "退款规则",
  excerpt: "x".repeat(2_000),
  sourceType: "knowledge_base",
  metadata: { internal_table: "must-not-enter-prompt" }
};

const request: AgentMessageRequest = {
  userInput: "能退款吗",
  userType: 1,
  historyOrders: Array.from({ length: 20 }, (_, index) => `订单-${index}-${"y".repeat(180)}`)
};

describe("customer prompt contract", () => {
  it("keeps stable instructions separate from request context", () => {
    const system = buildCustomerSystemPrompt();
    const context = buildCustomerContext(request, [source]);

    expect(system).toContain(CUSTOMER_PROMPT_VERSION);
    expect(system).not.toContain("订单-0");
    expect(context).toContain("订单-0");
    expect(context).not.toContain("must-not-enter-prompt");
  });

  it("bounds history and retrieved excerpts before model invocation", () => {
    const context = buildCustomerContext(request, [source]);

    expect(context).toContain("订单-4");
    expect(context).not.toContain("订单-5");
    expect(context).toContain(`${"x".repeat(1_200)}…`);
    expect(context).not.toContain("x".repeat(1_201));
  });
});
