/**
 * Embedding 客户端接缝：向量化能力可替换（如 mock、通义、BGE、本地部署）。
 */
export interface EmbeddingClient {
  embedTexts(texts: string[]): Promise<number[][]>;
}

export interface EmbeddingConfig {
  baseUrl: string;
  apiKey: string;
  model: string;
  dimensions: number;
}

interface EmbeddingResponse {
  data: Array<{ embedding: number[] }>;
}

/**
 * OpenAI 兼容 Embedding 客户端（默认实现）。
 * 走 `/embeddings` 接口，分批调用以降低请求次数。
 */
export class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
  private readonly batchSize = 20;

  public constructor(private readonly config: EmbeddingConfig) {}

  public async embedTexts(texts: string[]): Promise<number[][]> {
    if (texts.length === 0) return [];

    const results: number[][] = [];
    for (let i = 0; i < texts.length; i += this.batchSize) {
      const batch = texts.slice(i, i + this.batchSize);
      const embeddings = await this.embedBatch(batch);
      results.push(...embeddings);
    }
    return results;
  }

  private async embedBatch(texts: string[]): Promise<number[][]> {
    const url = `${this.config.baseUrl}/embeddings`;
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${this.config.apiKey}`
      },
      body: JSON.stringify({
        model: this.config.model,
        input: texts,
        dimensions: this.config.dimensions
      })
    });

    if (!response.ok) {
      throw new Error(`Embedding API error: HTTP ${response.status}`);
    }

    const data = (await response.json()) as EmbeddingResponse;
    return data.data.map((item) => item.embedding);
  }
}
