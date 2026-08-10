/**
 * 对话摘要服务
 *
 * 对齐 Backend ChatSummaryService 的转人工总结能力
 * 支持基于规则的基础摘要和 LLM 驱动的深度摘要两种模式
 */
export class ChatSummaryService {
  /**
   * 基础规则摘要（不依赖 LLM，用于降级场景）
   */
  public generateBasicSummary(
    chatHistory: string[],
    sessionId: string
  ): {
    priority: string;
    summary: string;
    tags: string;
  } {
    const messageCount = chatHistory.length;

    // 检测关键词
    const combinedText = chatHistory.join(" ");
    const hasUrgentKeywords = /投诉|维权|退款|故障|报修|紧急|尽快/i.test(combinedText);
    const hasOrderKeywords = /订单|物流|发货|配送/i.test(combinedText);
    const hasProductKeywords = /产品|商品|参数|规格|型号/i.test(combinedText);

    // 确定优先级
    const priority = hasUrgentKeywords ? "high" : "medium";

    // 生成摘要
    const summaryParts: string[] = [
      `会话 ${sessionId} 共 ${messageCount} 条消息`
    ];
    if (hasUrgentKeywords) summaryParts.push("包含紧急诉求");
    if (hasOrderKeywords) summaryParts.push("涉及订单相关问题");
    if (hasProductKeywords) summaryParts.push("涉及产品咨询");

    // 生成标签
    const tags: string[] = [];
    if (hasUrgentKeywords) tags.push("紧急");
    if (hasOrderKeywords) tags.push("订单");
    if (hasProductKeywords) tags.push("产品咨询");

    return {
      priority,
      summary: summaryParts.join("，") + "。",
      tags: tags.length > 0 ? tags.join(",") : "一般咨询"
    };
  }

  /**
   * 格式化对话历史为标准格式（供 LLM 使用）
   */
  public formatChatHistory(
    messages: Array<{ role: string; content: string; timestamp?: string }>
  ): string[] {
    return messages.map((msg) => {
      const roleLabel = msg.role === "user" ? "用户" :
                        msg.role === "agent" ? "坐席" :
                        msg.role === "ai" ? "AI" : msg.role;
      return `[${roleLabel}] ${msg.content}`;
    });
  }
}
