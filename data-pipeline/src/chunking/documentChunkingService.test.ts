import { describe, it, expect } from "vitest";
import { DocumentChunkingService } from "./documentChunkingService.js";

describe("DocumentChunkingService", () => {
  it("递归分块：短文本不拆分", () => {
    const service = new DocumentChunkingService();
    const chunks = service.splitRecursive("短文本");
    expect(chunks).toHaveLength(1);
    expect(chunks[0]?.content).toBe("短文本");
  });

  it("递归分块：长文本按分隔符拆成多块且每块不超限", () => {
    const service = new DocumentChunkingService({ chunkSize: 20, chunkOverlap: 0 });
    const text = "第一句。第二句。第三句。第四句。第五句。第六句。第七句。";
    const chunks = service.splitRecursive(text);

    expect(chunks.length).toBeGreaterThan(1);
    for (const chunk of chunks) {
      expect(chunk.content.length).toBeLessThanOrEqual(20);
    }
  });

  it("PDR 模式：父块大于子块且子块关联父块", () => {
    const service = new DocumentChunkingService();
    const text = "段落内容。".repeat(200);
    const { parentChunks, childChunks } = service.splitWithParent(text);

    expect(parentChunks.length).toBeGreaterThan(0);
    expect(childChunks.length).toBeGreaterThan(0);
    expect(parentChunks[0]!.content.length).toBeGreaterThan(childChunks[0]!.content.length);
  });

  it("提取 Markdown 标题元数据", () => {
    const service = new DocumentChunkingService();
    const metadata = service.extractMetadata("# 标题一\n## 标题二\n正文");
    expect(metadata.h1).toBe("标题一");
    expect(metadata.h2).toBe("标题二");
  });
});
