import { describe, expect, it } from "vitest";
import { buildApp } from "./app.js";
import type { PipelineConfig } from "./config.js";
import type { EmbeddingClient } from "./embedding/embeddingClient.js";
import type { VectorStore } from "./vector/vectorStore.js";
import type { FileParser } from "./parsing/fileParser.js";

const config: PipelineConfig = {
  port: 3002,
  security: {
    serviceToken: "test-service-token",
    allowedOrigins: ["http://localhost:5173"]
  },
  embedding: { baseUrl: "http://unused", apiKey: "", model: "test", dimensions: 2 },
  chromadb: { url: "http://unused", collection: "test" },
  chunking: {
    strategy: "pdr",
    chunkSize: 800,
    chunkOverlap: 150,
    pdrParentChunkSize: 2000,
    pdrParentOverlap: 200,
    pdrChildChunkSize: 400,
    pdrChildOverlap: 50
  },
  searchLimit: 5,
  logLevel: "error"
};

const embedding: EmbeddingClient = {
  embedTexts: async (texts) => texts.map(() => [1, 0])
};

const vectorStore: VectorStore = {
  search: async () => [],
  add: async () => undefined,
  deleteByDocument: async () => undefined,
  setEnabled: async () => undefined,
  clear: async () => undefined
};

const fileParser: FileParser = {
  parse: async () => "parsed"
};

describe("data-pipeline service authentication", () => {
  it("keeps health public but protects knowledge APIs", async () => {
    const app = await buildApp({ config, embedding, vectorStore, fileParser });

    const health = await app.inject({ method: "GET", url: "/health" });
    const unauthorized = await app.inject({
      method: "POST",
      url: "/search",
      payload: { query: "refund" }
    });
    const authorized = await app.inject({
      method: "POST",
      url: "/search",
      headers: { authorization: "Bearer test-service-token" },
      payload: { query: "refund" }
    });

    expect(health.statusCode).toBe(200);
    expect(unauthorized.statusCode).toBe(401);
    expect(authorized.statusCode).toBe(200);
    await app.close();
  });
});
