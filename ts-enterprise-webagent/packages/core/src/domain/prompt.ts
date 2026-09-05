import type { AgentMessageRequest, KnowledgeSource } from "@enterprise-webagent/shared";

/** Version the prompt contract so changes can be correlated with evaluations. */
export const CUSTOMER_PROMPT_VERSION = "customer-service-prompt-v2";

const MAX_HISTORY_ITEMS = 5;
const MAX_HISTORY_ITEM_CHARS = 120;
const MAX_SOURCE_EXCERPT_CHARS = 1_200;

/** Stable instructions shared by every request and eligible for provider prefix caching. */
export function buildCustomerSystemPrompt(): string {
  return [
    `Prompt version: ${CUSTOMER_PROMPT_VERSION}`,
    "你是一名专业的企业智能客服助手。",
    "只允许依据本轮提供的知识库上下文回答；上下文不足时明确说明，不得使用训练数据补全事实。",
    "使用简洁、礼貌、专业的中文回答，输出 Markdown，关键信息可使用加粗。",
    "只向用户展示其权限允许看到的上下文，不推断、扩展或暴露未提供的内部信息。",
    "用户表达工单诉求时可以说明已识别诉求，但不得声称后台已执行；任何写入操作必须经过产品确认边界。",
    "如果知识库没有足够信息，回复：抱歉，目前的知识库暂未收录该特定参数，建议联系专属人工顾问。"
  ].join("\n");
}

function clip(value: string, maxChars: number): string {
  const normalized = value.replace(/\s+/g, " ").trim();
  return normalized.length <= maxChars ? normalized : `${normalized.slice(0, maxChars)}…`;
}

function serializeSourcesForContext(sources: KnowledgeSource[]): string {
  if (sources.length === 0) return "当前没有命中的知识库记录。";

  return sources.slice(0, 5).map((source, index) => [
    `【结果 ${index + 1}】`,
    `来源标识：${clip(source.id, 80)}`,
    `标题：${clip(source.title, 200)}`,
    `来源类型：${clip(source.sourceType, 80)}`,
    `内容：${clip(source.excerpt, MAX_SOURCE_EXCERPT_CHARS)}`
  ].join("\n")).join("\n\n");
}

/** Dynamic, bounded request context kept separate from stable system instructions. */
export function buildCustomerContext(
  request: AgentMessageRequest,
  sources: KnowledgeSource[]
): string {
  const role = request.userType === 0 ? "游客（仅展示通用信息）" : "会员";
  const history = request.historyOrders.length === 0
    ? "暂无历史订单"
    : request.historyOrders
      .slice(0, MAX_HISTORY_ITEMS)
      .map(item => clip(item, MAX_HISTORY_ITEM_CHARS))
      .join("；");

  return [
    "## 本轮用户上下文",
    `用户身份：${role}`,
    `历史购买记录（最多展示 ${MAX_HISTORY_ITEMS} 条）：${history}`,
    "",
    "## 本轮知识库上下文",
    serializeSourcesForContext(sources),
    "",
    "请直接回答用户问题。"
  ].join("\n");
}

/** Backward-compatible convenience for callers that need the complete prompt view. */
export function buildCustomerPrompt(
  request: AgentMessageRequest,
  sources: KnowledgeSource[]
): string {
  return `${buildCustomerSystemPrompt()}\n\n${buildCustomerContext(request, sources)}`;
}
