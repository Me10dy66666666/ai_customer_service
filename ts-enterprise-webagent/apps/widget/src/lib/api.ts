import type { ChatRequest, ChatResponse } from "./types.js";

const DEFAULT_TIMEOUT_MS = 60_000;
/** 服务端默认端点 */
const DEFAULT_API_ENDPOINT = "/api/v1/customer-agent/messages";

/** 防御性 HTTP 客户端，对齐 Server 的 camelCase 契约 */
export async function sendChatMessage(
  apiEndpoint: string,
  body: ChatRequest,
  signal?: AbortSignal,
): Promise<ChatResponse> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

  const linkedSignal = signal
    ? combineAbortSignals(signal, controller.signal)
    : controller.signal;

  try {
    const response = await fetch(apiEndpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: linkedSignal,
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => "");
      throw new Error(
        `[webagent] API 返回 ${response.status} ${response.statusText}${errorText ? `: ${errorText}` : ""}`,
      );
    }

    const data: unknown = await response.json();

    if (!isChatResponse(data)) {
      throw new Error("[webagent] API 返回了非预期格式的响应");
    }

    return data;
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * 类型守卫：检查响应是否符合 ChatResponse 格式
 * 对齐 agentMessageResponseSchema
 */
function isChatResponse(value: unknown): value is ChatResponse {
  if (typeof value !== "object" || value === null) return false;
  const obj = value as Record<string, unknown>;
  return typeof obj.answer === "string" && typeof obj.sessionId === "string";
}

/** 合并两个 AbortSignal：任一触发则整体 abort。 */
function combineAbortSignals(
  a: AbortSignal,
  b: AbortSignal,
): AbortSignal {
  const controller = new AbortController();
  const onAbort = () => controller.abort();
  a.addEventListener("abort", onAbort, { once: true });
  b.addEventListener("abort", onAbort, { once: true });
  if (a.aborted || b.aborted) controller.abort();
  return controller.signal;
}

export { DEFAULT_API_ENDPOINT };
