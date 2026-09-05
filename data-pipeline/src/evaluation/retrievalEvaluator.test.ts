import { describe, expect, it } from "vitest";
import { evaluateRetrieval } from "./retrievalEvaluator.js";

describe("retrieval offline evaluator", () => {
  it("按 docId 计算命中率和 MRR", () => {
    const evaluation = evaluateRetrieval(
      [
        { query: "退货", relevantDocumentIds: ["doc-refund"] },
        { query: "发票", relevantDocumentIds: ["doc-invoice"] }
      ],
      (query) => query === "退货"
        ? [{ id: "chunk-1", title: "退款", excerpt: "...", sourceType: "faq", metadata: { docId: "doc-refund" } }]
        : [
            { id: "chunk-2", title: "其他", excerpt: "...", sourceType: "faq", metadata: { docId: "doc-other" } },
            { id: "chunk-3", title: "发票", excerpt: "...", sourceType: "policy", metadata: { docId: "doc-invoice" } }
          ]
    );

    expect(evaluation.caseCount).toBe(2);
    expect(evaluation.hitRateAtK).toBe(1);
    expect(evaluation.meanReciprocalRank).toBe(0.75);
  });
});
