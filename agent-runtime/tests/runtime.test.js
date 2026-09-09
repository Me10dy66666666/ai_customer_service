import { afterEach, describe, expect, it, vi } from "vitest";
import { loadConfig } from "../src/config.js";
import { createRuntimeServer } from "../src/server.js";
const config = {
    ...loadConfig({
        PI_RUNTIME_SERVICE_TOKEN: "service-token",
        MODEL_API_KEY: "model-key",
    }),
    port: 0,
};
const servers = [];
afterEach(async () => {
    await Promise.all(servers.splice(0).map((server) => new Promise((resolve) => server.close(() => resolve()))));
});
async function start(runtime) {
    const server = createRuntimeServer(config, runtime);
    servers.push(server);
    await new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve()));
    const address = server.address();
    if (!address || typeof address === "string")
        throw new Error("server did not bind");
    return `http://127.0.0.1:${address.port}`;
}
describe("Pi runtime HTTP boundary", () => {
    it("exposes an unauthenticated health check", async () => {
        const baseUrl = await start({ run: vi.fn() });
        const response = await fetch(`${baseUrl}/health`);
        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ status: "ok", runtime: "pi" });
    });
    it("requires the runtime service token before touching the agent", async () => {
        const run = vi.fn();
        const baseUrl = await start({ run });
        const response = await fetch(`${baseUrl}/api/v1/customer-service/messages`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ query: "hello" }),
        });
        expect(response.status).toBe(401);
        expect(run).not.toHaveBeenCalled();
    });
    it("keeps the Java BFF response contract for blocking requests", async () => {
        const run = vi.fn().mockResolvedValue({
            answer: "hello",
            conversation_id: "session-1",
            runtime: "pi",
            tool_calls: [],
        });
        const baseUrl = await start({ run });
        const response = await fetch(`${baseUrl}/api/v1/customer-service/messages`, {
            method: "POST",
            headers: {
                Authorization: "Bearer service-token",
                "X-Agent-Capability-Token": "java-capability",
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ query: "hello", conversation_id: "session-1" }),
        });
        expect(response.status).toBe(200);
        expect(await response.json()).toMatchObject({ answer: "hello", conversation_id: "session-1" });
        expect(run).toHaveBeenCalledWith({ query: "hello", conversation_id: "session-1", inputs: undefined, response_mode: undefined }, "java-capability");
    });
    it("maps text deltas to the stable SSE message vocabulary", async () => {
        const run = vi.fn().mockImplementation(async (_request, _capability, onEvent) => {
            onEvent({ type: "text-delta", text: "hello" });
            return { answer: "hello", conversation_id: "session-1", runtime: "pi", tool_calls: [] };
        });
        const baseUrl = await start({ run });
        const response = await fetch(`${baseUrl}/api/v1/customer-service/messages/streaming`, {
            method: "POST",
            headers: {
                Authorization: "Bearer service-token",
                "X-Agent-Capability-Token": "java-capability",
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ query: "hello", conversation_id: "session-1", response_mode: "streaming" }),
        });
        const body = await response.text();
        expect(response.status).toBe(200);
        expect(body).toContain('"event":"message"');
        expect(body).toContain('"answer":"hello"');
    });
});
//# sourceMappingURL=runtime.test.js.map