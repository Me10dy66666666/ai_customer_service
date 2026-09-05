import { afterEach, describe, expect, it, vi } from "vitest";

import { DataPipelineKnowledgeBase } from "./dataPipelineKnowledgeBase.js";

const config = {
  baseUrl: "http://pipeline.test/",
  serviceToken: "pipeline-secret",
  timeoutMs: 1_000,
  retryAttempts: 2,
  retryDelayMs: 0,
  embeddingModel: "text-embedding-v3",
  embeddingDimensions: 1024
};

describe("DataPipelineKnowledgeBase", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("maps filtered search sources and sends the service credential", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({
        sources: [{
          id: "chunk-1",
          title: "退款规则",
          excerpt: "退款需要在签收后七天内申请。",
          sourceType: "policy",
          metadata: { datasetId: "customer-service", allowedRoles: ["PUBLIC"] }
        }]
      }), { status: 200, headers: { "content-type": "application/json" } })
    );

    const result = await new DataPipelineKnowledgeBase(config).search({
      query: "怎么退款",
      userType: 0,
      limit: 5
    });

    expect(result).toEqual([{
      id: "chunk-1",
      title: "退款规则",
      excerpt: "退款需要在签收后七天内申请。",
      sourceType: "policy",
      metadata: { datasetId: "customer-service", allowedRoles: "PUBLIC" }
    }]);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://pipeline.test/search",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer pipeline-secret" })
      })
    );
    const [, init] = fetchMock.mock.calls[0] ?? [];
    expect(JSON.parse(String(init?.body))).toMatchObject({
      query: "怎么退款",
      limit: 5,
      roles: ["PUBLIC"]
    });
  });

  it("retries transient pipeline responses and fails closed on non-retryable responses", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response("temporary failure", { status: 503 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ sources: [] }), { status: 200 }));

    await expect(new DataPipelineKnowledgeBase(config).search({
      query: "查询",
      userType: 1,
      limit: 3
    })).resolves.toEqual([]);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    fetchMock.mockReset().mockResolvedValue(new Response("forbidden", { status: 403 }));
    await expect(new DataPipelineKnowledgeBase(config).search({
      query: "查询",
      userType: 1,
      limit: 3
    })).rejects.toMatchObject({ name: "DataPipelineError", statusCode: 403 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not retry a multipart upload whose server-generated id could duplicate", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("temporary failure", { status: 503 }));

    await expect(new DataPipelineKnowledgeBase(config).uploadDocument(
      Buffer.from("knowledge"),
      "faq.txt",
      "customer-service"
    )).rejects.toMatchObject({ name: "DataPipelineError", statusCode: 503 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
