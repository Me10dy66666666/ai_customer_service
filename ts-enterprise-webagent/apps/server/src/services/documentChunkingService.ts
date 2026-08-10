/**
 * 文档分块与索引服务
 *
 * 基于调研报告推荐方案实现：
 * - 主策略：递归分块（chunk_size=800, overlap=150）
 * - 增强策略：父文档检索（PDR）
 * - 分割符优化：针对中文 FAQ/政策文档
 */
export class DocumentChunkingService {
  private readonly DEFAULT_CHUNK_SIZE = 800;
  private readonly DEFAULT_CHUNK_OVERLAP = 150;

  private readonly SEPARATORS = [
    "\n\n", "\n", "。", "！", "？", "；", "，", " "
  ];

  /**
   * 递归分块（TypeScript 原生实现）
   */
  public splitRecursive(
    text: string,
    options?: { chunkSize?: number; chunkOverlap?: number }
  ): Chunk[] {
    const chunkSize = options?.chunkSize ?? this.DEFAULT_CHUNK_SIZE;
    const chunkOverlap = options?.chunkOverlap ?? this.DEFAULT_CHUNK_OVERLAP;

    const fragments = this.recursiveSplit(text, this.SEPARATORS, chunkSize, chunkOverlap);
    return fragments.map((content, index) => ({
      id: `chunk-${index}`,
      content,
      index,
      sourceType: "knowledge_base" as const
    }));
  }

  /**
   * 父文档检索模式分块
   */
  public splitWithParent(text: string): { parentChunks: Chunk[]; childChunks: Chunk[] } {
    const parentChunks = this.splitRecursive(text, { chunkSize: 2000, chunkOverlap: 200 });
    const childChunks = this.splitRecursive(text, { chunkSize: 400, chunkOverlap: 50 });

    for (let i = 0; i < childChunks.length; i++) {
      const child = childChunks[i];
      if (!child) continue;
      const childText: string = child.content;
      const parentIdx = parentChunks.findIndex(
        (p: Chunk | undefined) => {
          if (!p) return false;
          return childText.includes(p.content.slice(0, 100)) ||
                 p.content.includes(childText.slice(0, 100));
        }
      );
      if (parentIdx >= 0) {
        const parent = parentChunks[parentIdx];
        if (parent) child.parentId = parent.id;
      }
    }

    return { parentChunks, childChunks };
  }

  /**
   * 从 Markdown 文本中提取元数据（标题层级等）
   */
  public extractMetadata(text: string): Record<string, string> {
    const metadata: Record<string, string> = {};

    const h1Match = text.match(/^# (.+)$/m);
    if (h1Match) metadata.h1 = h1Match[1]?.trim() ?? "";

    const h2Match = text.match(/^## (.+)$/m);
    if (h2Match) metadata.h2 = h2Match[1]?.trim() ?? "";

    const h3Match = text.match(/^### (.+)$/m);
    if (h3Match) metadata.h3 = h3Match[1]?.trim() ?? "";

    return metadata;
  }

  // ============================================================
  // 私有实现
  // ============================================================

  private recursiveSplit(
    text: string,
    separators: string[],
    chunkSize: number,
    chunkOverlap: number
  ): string[] {
    if (text.length <= chunkSize) {
      return [text];
    }

    for (const separator of separators) {
      const frags = text.split(separator).filter(Boolean);
      const maxLen = Math.max(...frags.map((f) => f.length));
      if (maxLen <= chunkSize && frags.length > 1) {
        return this.mergeFragments(frags, separator, chunkSize, chunkOverlap);
      }
    }

    return this.fixedSizeSplit(text, chunkSize, chunkOverlap);
  }

  private mergeFragments(
    fragments: string[],
    separator: string,
    chunkSize: number,
    chunkOverlap: number
  ): string[] {
    const chunks: string[] = [];
    let current = "";

    for (let i = 0; i < fragments.length; i++) {
      const fragment = fragments[i];
      if (!fragment) continue;

      const candidate = current ? `${current}${separator}${fragment}` : fragment;

      if (candidate.length > chunkSize && current.length > 0) {
        chunks.push(current.trim());

        if (chunkOverlap > 0 && i > 0) {
          const prev = fragments[i - 1] ?? "";
          if (prev.length < chunkOverlap) {
            current = prev + separator + fragment;
          } else {
            current = fragment;
          }
        } else {
          current = fragment;
        }
      } else {
        current = candidate;
      }
    }

    if (current.trim()) {
      chunks.push(current.trim());
    }

    return chunks;
  }

  private fixedSizeSplit(text: string, chunkSize: number, chunkOverlap: number): string[] {
    const chunks: string[] = [];
    let start = 0;

    while (start < text.length) {
      const end = Math.min(start + chunkSize, text.length);
      chunks.push(text.slice(start, end));
      start += chunkSize - chunkOverlap;
    }

    return chunks;
  }
}

/**
 * 文本块定义
 */
export interface Chunk {
  id: string;
  content: string;
  index: number;
  sourceType: "knowledge_base" | "policy" | "faq";
  parentId?: string;
}
