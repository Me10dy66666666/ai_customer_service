import type { KnowledgeRetriever } from "@enterprise-webagent/core";
import type { KnowledgeSource } from "@enterprise-webagent/shared";

const DEFAULT_KNOWLEDGE_BASE: KnowledgeSource[] = [
  {
    id: "refund-policy",
    title: "订单退款政策",
    excerpt: "会员与游客均支持 7 天无理由退款；定制化设备、已激活软件许可与人为损坏商品不适用。",
    sourceType: "policy",
    metadata: {
      category: "退款",
      rowId: "policy-001"
    }
  },
  {
    id: "member-service",
    title: "会员服务等级",
    excerpt: "会员用户可享受优先人工响应、详细技术文档、升级兼容性建议；游客仅展示公开参数。",
    sourceType: "knowledge_base",
    metadata: {
      category: "会员权益",
      rowId: "kb-102"
    }
  },
  {
    id: "after-sales-sla",
    title: "售后工单时效",
    excerpt: "售后工单提交后 2 小时内完成首次响应；严重故障将自动提升为高优先级并转人工。",
    sourceType: "faq",
    metadata: {
      category: "工单",
      rowId: "faq-204"
    }
  },
  {
    id: "order-status",
    title: "订单状态说明",
    excerpt: "订单状态包含待支付、已支付、已发货、运输中、已完成、退款中。已支付后可在个人中心查看物流节点。",
    sourceType: "knowledge_base",
    metadata: {
      category: "订单",
      rowId: "kb-016"
    }
  }
];

function extractTerms(input: string): string[] {
  return input.match(/[\u4e00-\u9fa5]{2,}|[a-z0-9]+/gi) ?? [];
}

function scoreDocument(queryTerms: string[], source: KnowledgeSource): number {
  const haystack = `${source.title} ${source.excerpt} ${Object.values(source.metadata).join(" ")}`.toLowerCase();

  return queryTerms.reduce((score, term) => {
    return haystack.includes(term.toLowerCase()) ? score + 1 : score;
  }, 0);
}

export class InMemoryKnowledgeBase implements KnowledgeRetriever {
  public constructor(private readonly sources: KnowledgeSource[] = DEFAULT_KNOWLEDGE_BASE) {}

  public async search(input: {
    query: string;
    limit: number;
    userType: number;
    signal?: AbortSignal;
  }): Promise<KnowledgeSource[]> {
    if (input.signal?.aborted) {
      throw new DOMException("Knowledge search aborted", "AbortError");
    }

    const queryTerms = extractTerms(input.query.trim());
    if (queryTerms.length === 0) {
      return [];
    }

    return this.sources
      .map((source) => ({ source, score: scoreDocument(queryTerms, source) }))
      .filter((item) => item.score > 0)
      .sort((left, right) => right.score - left.score)
      .slice(0, input.limit)
      .map((item) => item.source);
  }
}
