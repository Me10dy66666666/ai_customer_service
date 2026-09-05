import { describe, expect, it, vi } from "vitest";

import { PgVectorStore, VectorStoreError, type PgVectorStoreOptions } from "./pgVectorStore.js";

type QueryRow = Record<string, unknown>;

function createPool() {
  const client = {
    query: vi.fn(async (_text: string, _values?: readonly unknown[]) => ({ rows: [] as QueryRow[] })),
    release: vi.fn()
  };
  const pool = {
    query: vi.fn(async (text: string, _values?: readonly unknown[]) => {
      if (text.includes("format_type")) return { rows: [{ type_name: "vector(3)" }] };
      if (text.includes("embedding <=>")) {
        return {
          rows: [{
            id: "doc-1-child-0",
            document_id: "doc-1",
            chunk_id: "child-0",
            content: "退货政策",
            metadata: { title: "policy.md", allowedRoles: "PUBLIC" },
            enabled: true,
            distance: "0.125"
          }]
        };
      }
      if (text.includes("array_position")) {
        return {
          rows: [{
            id: "doc-1-parent-0",
            document_id: "doc-1",
            chunk_id: "parent-0",
            content: "完整退货政策",
            metadata: { chunkKind: "parent" },
            enabled: true
          }]
        };
      }
      return { rows: [] as QueryRow[] };
    }),
    connect: vi.fn(async () => client),
    end: vi.fn(async () => undefined)
  };
  return { client, pool };
}

function createStore(): { store: PgVectorStore; client: ReturnType<typeof createPool>["client"]; pool: ReturnType<typeof createPool>["pool"] } {
  const { client, pool } = createPool();
  const options: PgVectorStoreOptions = {
    connectionString: "postgresql://test",
    dimensions: 3,
    pool: pool as unknown as NonNullable<PgVectorStoreOptions["pool"]>,
    retryAttempts: 1,
    retryDelayMs: 0
  };
  return { store: new PgVectorStore(options), client, pool };
}

describe("PgVectorStore", () => {
  it("upserts chunks in one transaction and applies database identities", async () => {
    const { store, client } = createStore();

    await store.add([{
      id: "doc-1-child-0",
      documentId: "doc-1",
      chunkId: "child-0",
      content: "退货政策",
      metadata: { docId: "doc-1", enabled: "true" }
    }], [[0.1, 0.2, 0.3]]);

    expect(client.query).toHaveBeenCalledWith("BEGIN");
    expect(client.query).toHaveBeenCalledWith(
      expect.stringContaining("ON CONFLICT (id) DO UPDATE"),
      ["doc-1-child-0", "doc-1", "child-0", "退货政策", "[0.1,0.2,0.3]", expect.any(String), true]
    );
    expect(client.query).toHaveBeenCalledWith("COMMIT");
    expect(client.release).toHaveBeenCalledOnce();
  });

  it("pushes metadata predicates into cosine search and maps pg rows", async () => {
    const { store, pool } = createStore();

    const results = await store.search([0.1, 0.2, 0.3], 5, {
      datasetId: "default",
      knowledgeDomain: "customer-service",
      roles: ["USER"],
      chunkKind: "child",
      excludeExpired: true
    });

    const searchCall = pool.query.mock.calls.find(([text]) => text.includes("embedding <=>"));
    expect(searchCall?.[0]).toContain("embedding <=> $1::vector");
    expect(searchCall?.[0]).toContain("metadata->>'datasetId'");
    expect(searchCall?.[0]).toContain("metadata->>'knowledgeDomain'");
    expect(searchCall?.[0]).toContain("expiresAt");
    expect(searchCall?.[1]).toEqual([
      "[0.1,0.2,0.3]",
      "default",
      "customer-service",
      "child",
      ["USER"],
      5
    ]);
    expect(results).toEqual([{
      id: "doc-1-child-0",
      content: "退货政策",
      metadata: { title: "policy.md", allowedRoles: "PUBLIC", enabled: "true", docId: "doc-1" },
      distance: 0.125
    }]);
  });

  it("rejects wrong vector dimensions and mismatched database schemas", async () => {
    const { store, pool } = createStore();

    expect(() => PgVectorStore.toPgVectorLiteral([1, 2], 3)).toThrow(VectorStoreError);
    await expect(store.search([1, 2], 1)).rejects.toThrow("exactly 3");

    const mismatchPool = createPool();
    mismatchPool.pool.query.mockImplementation(async (text: string) => text.includes("format_type")
      ? { rows: [{ type_name: "vector(4)" }] }
      : { rows: [] });
    const mismatched = new PgVectorStore({
      connectionString: "postgresql://test",
      dimensions: 3,
      pool: mismatchPool.pool as unknown as NonNullable<PgVectorStoreOptions["pool"]>,
      retryAttempts: 1
    });
    await expect(mismatched.initialize()).rejects.toThrow("dimension mismatch");
  });
});
