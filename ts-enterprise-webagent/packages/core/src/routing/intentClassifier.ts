/**
 * 意图分类器 - 混合策略
 *
 * 第一阶段：快速关键词/正则匹配（< 1ms）
 * 第二阶段：语义兜底（标记为需要 LLM 进一步判断）
 *
 * 分类结果：
 * - "faq"         → RAG 快速通道
 * - "order"       → Function Calling 通道（查订单）
 * - "work_order"  → Function Calling 通道（创建工单）
 * - "transfer"    → 人工转接通道
 * - "chitchat"    → 兜底策略
 * - "unknown"     → 默认走 RAG 快速通道
 */
export type IntentLabel =
  | "faq"
  | "order"
  | "work_order"
  | "transfer"
  | "chitchat"
  | "unknown";

export interface IntentResult {
  label: IntentLabel;
  confidence: number; // 0-1
  matchedPattern?: string;
  needsLlmRefinement: boolean;
}

// ============================================================
// 关键词规则库
// ============================================================
const INTENT_PATTERNS: Array<{
  label: IntentLabel;
  patterns: RegExp[];
  confidence: number;
}> = [
  {
    label: "transfer",
    patterns: [
      /人工|转接.*客服|真人|坐席|转人工|找.?人|活人|在线客服|电话.*客服|联系.*客服/i,
      /(?:投诉|举报|维权|曝光|律师|12315)/i
    ],
    confidence: 0.9
  },
  {
    label: "work_order",
    patterns: [
      /工单|报修|售后|退货|换货|退款|维修|故障|坏[了掉]|不能.*用|无法.*使用|申请.*退/i,
      /质量问题|缺[少损]|发错|漏发|破损|损坏|不.*满意.*退/i
    ],
    confidence: 0.85
  },
  {
    label: "order",
    patterns: [
      /订单|查.*单|购买记录|买[了的过]|下单|物流|快递|发货|配送|已购|我的.*单/i,
      /(?:什么|多久|几时|啥时候).*到|到货|收货/i
    ],
    confidence: 0.8
  },
  {
    label: "chitchat",
    patterns: [
      /^(?:你好|hi|hello|嗨|在吗|在不在|早上好|下午好|晚上好|谢谢|感谢|再见|拜拜)$/i,
      /^[?!！？。.,，、\s]*$/,
      /天气|吃饭|聊天|无聊|讲.*笑话|你是谁/i
    ],
    confidence: 0.95
  }
];

// ============================================================
// 分类函数
// ============================================================
export function classifyIntent(input: string): IntentResult {
  const trimmed = input.trim();
  if (trimmed.length === 0) {
    return {
      label: "chitchat",
      confidence: 1.0,
      needsLlmRefinement: false
    };
  }

  // 第一阶段：关键词/正则匹配
  for (const rule of INTENT_PATTERNS) {
    for (const pattern of rule.patterns) {
      if (pattern.test(trimmed)) {
        // 高频售后词需要 LLM 确认（可能是 FAQ 询问政策，而非真的需要工单）
        const needsLlmRefinement =
          rule.label === "work_order" && /怎么|如何|政策|规定|流程/.test(trimmed);

        return {
          label: rule.label,
          confidence: rule.confidence,
          matchedPattern: pattern.source,
          needsLlmRefinement
        };
      }
    }
  }

  // 默认：FAQ 类（走 RAG 快速通道）
  return {
    label: "faq",
    confidence: 0.5,
    needsLlmRefinement: false
  };
}

/**
 * 判断意图是否需要走 Function Calling 通道
 */
export function requiresFunctionCalling(intent: IntentLabel): boolean {
  return intent === "order" || intent === "work_order" || intent === "transfer";
}

/**
 * 判断意图是否可以走 RAG 快速通道
 */
export function canUseRagFastPath(intent: IntentLabel): boolean {
  return intent === "faq" || intent === "chitchat" || intent === "unknown";
}
