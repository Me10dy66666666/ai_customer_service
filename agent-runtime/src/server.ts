import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { timingSafeEqual } from "node:crypto";
import type { RuntimeConfig } from "./config.js";
import { CustomerServiceAgentRuntime, RuntimeError, type AgentRequest } from "./agent.js";

const MAX_BODY_BYTES = 1_000_000;

export function createRuntimeServer(
  config: RuntimeConfig,
  runtime = new CustomerServiceAgentRuntime(config),
): Server {
  return createServer(async (request, response) => {
    try {
      await route(request, response, config, runtime);
    } catch (error) {
      writeError(response, error);
    }
  });
}

async function route(
  request: IncomingMessage,
  response: ServerResponse,
  config: RuntimeConfig,
  runtime: CustomerServiceAgentRuntime,
): Promise<void> {
  const path = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`).pathname;
  if (request.method === "GET" && path === "/health") {
    writeJson(response, 200, { status: "ok", runtime: "pi" });
    return;
  }

  if (request.method !== "POST" || !path.startsWith("/api/v1/customer-service/messages")) {
    writeJson(response, 404, { code: "NOT_FOUND", message: "route not found" });
    return;
  }
  if (!hasServiceAuthorization(request, config.serviceToken)) {
    writeJson(response, 401, { code: "SERVICE_UNAUTHORIZED", message: "service authorization required" });
    return;
  }

  const body = parseRequest(await readBody(request));
  const capabilityToken = readHeaderToken(request.headers["x-agent-capability-token"]);
  const isStreaming = path.endsWith("/streaming") || body.response_mode === "streaming";
  if (!isStreaming) {
    const result = await runtime.run(body, capabilityToken);
    writeJson(response, 200, result);
    return;
  }

  response.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive",
  });
  try {
    const result = await runtime.run(body, capabilityToken, (event) => {
      if (event.type === "text-delta") {
        response.write(`data: ${JSON.stringify({
          ...event,
          event: "message",
          answer: event.text ?? "",
          conversation_id: body.conversation_id,
        })}\n\n`);
      }
    });
    response.write(`data: ${JSON.stringify({
      type: "message",
      event: "message",
      answer: "",
      conversation_id: result.conversation_id,
    })}\n\n`);
  } catch (error) {
    const normalized = normalizeError(error);
    response.write(`data: ${JSON.stringify({ type: "error", code: normalized.code, message: normalized.message })}\n\n`);
  } finally {
    response.end();
  }
}

function parseRequest(raw: string): AgentRequest & { response_mode?: string } {
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new RuntimeError("INVALID_REQUEST", "request body must be valid JSON", 400);
  }
  if (!value || typeof value !== "object") {
    throw new RuntimeError("INVALID_REQUEST", "request body must be an object", 400);
  }
  const body = value as Record<string, unknown>;
  return {
    query: typeof body.query === "string" ? body.query : "",
    conversation_id: typeof body.conversation_id === "string" ? body.conversation_id : undefined,
    inputs: isRecord(body.inputs) ? body.inputs : undefined,
    response_mode: typeof body.response_mode === "string" ? body.response_mode : undefined,
  };
}

function readBody(request: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let total = 0;
    const chunks: Buffer[] = [];
    request.on("data", (chunk: Buffer) => {
      total += chunk.length;
      if (total > MAX_BODY_BYTES) {
        reject(new RuntimeError("INVALID_REQUEST", "request body is too large", 413));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function hasServiceAuthorization(request: IncomingMessage, expected: string): boolean {
  if (!expected) return false;
  const provided = readBearer(request.headers.authorization);
  if (!provided) return false;
  const providedBytes = Buffer.from(provided);
  const expectedBytes = Buffer.from(expected);
  return providedBytes.length === expectedBytes.length && timingSafeEqual(providedBytes, expectedBytes);
}

function readBearer(value: string | string[] | undefined): string {
  const header = Array.isArray(value) ? value[0] : value;
  return header?.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : "";
}

function readHeaderToken(value: string | string[] | undefined): string {
  const header = Array.isArray(value) ? value[0] : value;
  return header?.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : header?.trim() ?? "";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function normalizeError(error: unknown): { code: string; message: string; status: number } {
  if (error instanceof RuntimeError) return error;
  return { code: "RUNTIME_UNAVAILABLE", message: "agent runtime is unavailable", status: 503 };
}

function writeError(response: ServerResponse, error: unknown): void {
  if (response.headersSent) {
    response.end();
    return;
  }
  const normalized = normalizeError(error);
  writeJson(response, normalized.status, { code: normalized.code, message: normalized.message });
}

function writeJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(body));
}
