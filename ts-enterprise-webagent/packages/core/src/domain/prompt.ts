import type { AgentMessageRequest, KnowledgeSource } from "@enterprise-webagent/shared";

function serializeSourcesForPrompt(
  userType: number,
  sources: KnowledgeSource[]
): string {
  if (sources.length === 0) {
    return "当前没有命中的知识库记录。";
  }

  return sources
    .map((source, index) => {
      const detailBlock =
        userType === 0
          ? source.excerpt
          : `${source.excerpt}\n元数据：${JSON.stringify(source.metadata, null, 2)}`;

      return [
        `【结果 ${index + 1}】`,
        `标题：${source.title}`,
        `来源类型：${source.sourceType}`,
        detailBlock
      ].join("\n");
    })
    .join("\n\n");
}

export function buildCustomerSystemPrompt(
  request: AgentMessageRequest,
  sources: KnowledgeSource[]
): string {
  const historyOrders = request.historyOrders.length > 0
    ? request.historyOrders.join("；")
    : "暂无历史订单";

  const sourceBlock = serializeSourcesForPrompt(request.userType, sources);

  return `
你是一名专业的企业智能客服助手。你必须严格依据提供的知识库内容回答，不允许编造。

## 用户信息
- 用户身份类型：${request.userType}（0 为游客，非 0 为会员）
- 历史购买记录：${historyOrders}

## 回答约束
1. 只允许依据知识库内容作答，严禁使用训练数据补全缺失事实。
2. 如果知识库没有足够信息，统一回复：“抱歉，目前的知识库暂未收录该特定参数，建议联系专属人工顾问”。
3. 使用简洁、礼貌、专业的中文回答。
4. 输出使用 Markdown，关键信息使用加粗，适合网页内嵌展示。
5. 游客只展示通用信息，不暴露内部条款；会员可以展示更完整的参数和内部条款。
6. 如果用户明确表达“提交工单”“报修”“售后服务”“退换货”，允许在正文中说明已识别为工单诉求，但不要伪造后台执行结果。

## 知识库上下文
${sourceBlock}

请直接回答用户问题。
  `.trim();
}
