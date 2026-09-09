import type { RuntimeConfig } from "./config.js";

export interface RuntimeContext {
  sessionId: string;
  capabilityToken: string;
  roles: string[];
}

export class GatewayError extends Error {
  constructor(
    public readonly code: "GATEWAY_UNAVAILABLE" | "GATEWAY_REJECTED",
    message: string,
    public readonly status?: number,
  ) {
    super(message);
    this.name = "GatewayError";
  }
}

interface GatewayEnvelope<T> {
  code?: number;
  message?: string;
  data?: T;
}

async function readJson<T>(response: Response): Promise<T> {
  const body = (await response.json()) as GatewayEnvelope<T> | T;
  if (!response.ok) {
    const message = typeof body === "object" && body !== null && "message" in body
      ? String((body as GatewayEnvelope<T>).message ?? "gateway request rejected")
      : "gateway request rejected";
    throw new GatewayError("GATEWAY_REJECTED", message, response.status);
  }
  if (typeof body === "object" && body !== null && "data" in body) {
    return (body as GatewayEnvelope<T>).data as T;
  }
  return body as T;
}

export class JavaGateway {
  constructor(private readonly config: RuntimeConfig) {}

  async searchKnowledge(
    query: string,
    limit: number,
    roles: string[],
    signal?: AbortSignal,
  ): Promise<unknown> {
    try {
      const response = await fetch(`${this.config.pipelineBaseUrl}/search`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.config.pipelineServiceToken}`,
        },
        body: JSON.stringify({ query, limit, roles }),
        signal,
      });
      return await readJson(response);
    } catch (error) {
      if (error instanceof GatewayError) throw error;
      throw new GatewayError("GATEWAY_UNAVAILABLE", "knowledge service is unavailable");
    }
  }

  async lookupOwnOrders(context: RuntimeContext, signal?: AbortSignal): Promise<unknown> {
    return this.callBackend("/api/agent/tools/orders", context, { method: "GET", signal });
  }

  async proposeWorkOrder(
    context: RuntimeContext,
    proposal: { title: string; description: string; type?: string; priority?: string },
    signal?: AbortSignal,
  ): Promise<unknown> {
    return this.callBackend("/api/agent/tools/work-orders/proposals", context, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(proposal),
      signal,
    });
  }

  private async callBackend(path: string, context: RuntimeContext, init: RequestInit): Promise<unknown> {
    try {
      const response = await fetch(`${this.config.backendBaseUrl}${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${context.capabilityToken}`,
          "X-Agent-Session-Id": context.sessionId,
          ...(init.headers ?? {}),
        },
      });
      return await readJson(response);
    } catch (error) {
      if (error instanceof GatewayError) throw error;
      throw new GatewayError("GATEWAY_UNAVAILABLE", "Java capability gateway is unavailable");
    }
  }
}

export function asToolText(value: unknown): { type: "text"; text: string }[] {
  return [{ type: "text", text: JSON.stringify(value) }];
}
