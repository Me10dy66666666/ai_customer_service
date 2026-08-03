import type { ChatRequest, ChatResponse } from "./types";

const DEFAULT_TIMEOUT_MS = 60_000;

/** 防御性 HTTP 客户端，仅封装 webagent 单次调用。 */
export async function sendChatMessage(
  apiEndpoint: string,
  body: ChatRequest,
  signal?: AbortSignal,
): Promise<ChatResponse> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

  // 外部传入的 signal 与内部超时联动
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
      // 容错：如果不是标准格式，尝试把 reply 取出来
      if (
        typeof data === "object" &&
        data !== null &&
        "reply" in data
      ) {
        return { reply: String((data as Record<string, unknown>).reply) };
      }
      throw new Error("[webagent] API 返回了非预期格式的响应");
    }

    return data;
  } finally {
    clearTimeout(timeoutId);
  }
}

function isChatResponse(value: unknown): value is ChatResponse {
  if (typeof value !== "object" || value === null) return false;
  const obj = value as Record<string, unknown>;
  return typeof obj.reply === "string";
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
