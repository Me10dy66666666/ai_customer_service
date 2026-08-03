import type { WorkOrderAction } from "@enterprise-webagent/shared";

const HIGH_PRIORITY_PATTERNS = ["无法使用", "无法启动", "严重", "紧急"];
const AFTER_SALES_PATTERNS = ["报修", "售后", "退货", "换货", "退款", "故障"];
const WORK_ORDER_PATTERNS = ["提交工单", "工单", "报修", "售后", "退货", "换货", "退款"];

function containsAnyKeyword(input: string, patterns: string[]): boolean {
  return patterns.some((pattern) => input.includes(pattern));
}

export function detectWorkOrderAction(userInput: string): WorkOrderAction[] {
  const normalizedInput = userInput.trim();
  if (!normalizedInput || !containsAnyKeyword(normalizedInput, WORK_ORDER_PATTERNS)) {
    return [];
  }

  const type = containsAnyKeyword(normalizedInput, AFTER_SALES_PATTERNS) ? "售后" : "售前";
  const priority = containsAnyKeyword(normalizedInput, HIGH_PRIORITY_PATTERNS) ? "high" : "medium";

  return [
    {
      action: "create_work_order",
      data: {
        title: normalizedInput.slice(0, 40),
        description: normalizedInput.slice(0, 300),
        type,
        priority
      }
    }
  ];
}
