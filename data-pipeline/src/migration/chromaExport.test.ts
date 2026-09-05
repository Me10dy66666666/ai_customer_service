import { describe, expect, it } from "vitest";

import { ChromaMigrationError, parseChromaExport } from "./chromaExport.js";

describe("Chroma export migration parser", () => {
  it("normalizes Chroma collection.get output and preserves stable ids", () => {
    const result = parseChromaExport({
      ids: ["doc-1-child-0"],
      documents: ["退货政策"],
      metadatas: [{ docId: "doc-1", chunkId: "child-0", title: "policy.md" }],
      embeddings: [[0.1, 0.2, 0.3]]
    }, "customer-service");

    expect(result.documents[0]).toMatchObject({
      id: "doc-1-child-0",
      documentId: "doc-1",
      chunkId: "child-0",
      metadata: { datasetId: "customer-service", enabled: "true" }
    });
    expect(result.embeddings).toEqual([[0.1, 0.2, 0.3]]);
  });

  it("rejects incomplete exports instead of silently losing vectors", () => {
    expect(() => parseChromaExport({ ids: ["doc-1"], documents: ["text"] }))
      .toThrow(ChromaMigrationError);
  });
});
