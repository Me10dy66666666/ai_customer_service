import type { KnowledgeSource } from "../types.js";

export interface RetrievalCase {
  query: string;
  relevantDocumentIds: string[];
}

export interface RetrievalEvaluation {
  caseCount: number;
  hitRateAtK: number;
  meanReciprocalRank: number;
}

/**
 * 离线检索评测：只消费检索结果，不调用 LLM，适合在 CI 中固定回归。
 * relevantDocumentIds 使用知识源 metadata.docId，而不是易变的 chunk id。
 */
export function evaluateRetrieval(
  cases: RetrievalCase[],
  retrieve: (query: string) => KnowledgeSource[],
): RetrievalEvaluation {
  if (cases.length === 0) {
    return { caseCount: 0, hitRateAtK: 0, meanReciprocalRank: 0 };
  }

  let hits = 0;
  let reciprocalRankTotal = 0;
  for (const testCase of cases) {
    const relevant = new Set(testCase.relevantDocumentIds);
    const results = retrieve(testCase.query);
    const firstRelevantIndex = results.findIndex((source) => relevant.has(source.metadata.docId ?? ""));
    if (firstRelevantIndex >= 0) {
      hits += 1;
      reciprocalRankTotal += 1 / (firstRelevantIndex + 1);
    }
  }

  return {
    caseCount: cases.length,
    hitRateAtK: hits / cases.length,
    meanReciprocalRank: reciprocalRankTotal / cases.length
  };
}
