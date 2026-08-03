/**
 * @enterprise-webagent/core 单元测试
 *
 * 验证 CustomerAgentModule 编排的正确性。
 */
import { describe, it, expect } from "vitest";
import { CustomerAgentModule } from "../orchestration/customerAgent";
import type { ChatModel, KnowledgeRetriever, Logger } from "../adapters/contracts";
import type { KnowledgeSource } from "@enterprise-webagent/shared";

/* ── 桩实现 ── */

function createNullLogger(): Logger {
  return {
    info: () => {},
    error: () => {},
    warn: () => {},
  };
}

function createMockModel(reply: string): ChatModel {
  return {
    generate: async () => reply,
  };
}

function createMockRetriever(sources: KnowledgeSource[]): KnowledgeRetriever {
  return {
    search: async () => sources,
  };
}

const SEED_SOURCE: KnowledgeSource = {
  id: "doc-1",
  title: "订单退款政策",
  excerpt: "支持 7 天无理由退款。",
  sourceType: "knowledge_base",
  metadata: { table_name: "policies", row_id: "42" },
};

/* ── 测试 ── */

describe("CustomerAgentModule", () => {
  it("should return a knowledge-rich reply when retriever match found", async () => {
    const agent = new CustomerAgentModule({
      chatModel: createMockModel("桩回复：已根据知识库解答。"),
      knowledgeRetriever: createMockRetriever([SEED_SOURCE]),
      logger: createNullLogger(),
    });

    const result = await agent.reply({
      userInput: "我的订单能退款吗",
      userType: 1,
      historyOrders: ["无线耳机"],
    });

    expect(result.answer).toContain("桩回复");
    expect(result.sources).toHaveLength(1);
    expect(result.sources[0].title).toBe("订单退款政策");
  });

  it("should include user history and type in prompt for members", async () => {
    let capturedPrompt = "";
    const model: ChatModel = {
      generate: async (input) => {
        capturedPrompt = input.systemPrompt;
        return "会员专属回复";
      },
    };

    const agent = new CustomerAgentModule({
      chatModel: model,
      knowledgeRetriever: createMockRetriever([SEED_SOURCE]),
      logger: createNullLogger(),
    });

    await agent.reply({
      userInput: "我的订单状态",
      userType: 1,
      historyOrders: ["无线耳机"],
    });

    expect(capturedPrompt).toContain("无线耳机");
    expect(capturedPrompt).toContain("会员");
  });

  it("should use guest prompt when userType is 0", async () => {
    let capturedPrompt = "";
    const model: ChatModel = {
      generate: async (input) => {
        capturedPrompt = input.systemPrompt;
        return "游客回复";
      },
    };

    const agent = new CustomerAgentModule({
      chatModel: model,
      knowledgeRetriever: createMockRetriever([SEED_SOURCE]),
      logger: createNullLogger(),
    });

    await agent.reply({
      userInput: "你好",
      userType: 0,
      historyOrders: [],
    });

    expect(capturedPrompt).toContain("0 为游客");
  });

  it("should extract work order action from user input keywords", async () => {
    const agent = new CustomerAgentModule({
      chatModel: createMockModel("桩回复"),
      knowledgeRetriever: createMockRetriever([SEED_SOURCE]),
      logger: createNullLogger(),
    });

    const result = await agent.reply({
      userInput: "耳机严重故障报修需要提交工单",
      userType: 1,
      historyOrders: ["无线耳机"],
    });

    expect(result.actions).toHaveLength(1);
    expect(result.actions[0].action).toBe("create_work_order");
    expect(result.actions[0].data.type).toBe("售后");
    expect(result.actions[0].data.priority).toBe("high");
  });

  it("should return no actions when user input does not mention work orders", async () => {
    const agent = new CustomerAgentModule({
      chatModel: createMockModel("桩回复"),
      knowledgeRetriever: createMockRetriever([SEED_SOURCE]),
      logger: createNullLogger(),
    });

    const result = await agent.reply({
      userInput: "今天天气怎么样",
      userType: 0,
      historyOrders: [],
    });

    expect(result.actions).toHaveLength(0);
  });

  it("should fall back gracefully when knowledge base is empty", async () => {
    const agent = new CustomerAgentModule({
      chatModel: createMockModel("不会走到这里"),
      knowledgeRetriever: createMockRetriever([]),
      logger: createNullLogger(),
    });

    const result = await agent.reply({
      userInput: "奇怪的句子",
      userType: 0,
      historyOrders: [],
    });

    expect(result.answer).toContain("暂未收录");
    expect(result.answer).toContain("人工顾问");
    expect(result.sources).toHaveLength(0);
    expect(result.fallbackReason).toBe("knowledge_not_found");
  });
});
