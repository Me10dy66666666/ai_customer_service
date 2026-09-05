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
  timeoutMs?: number | undefined;
  retryAttempts?: number | undefined;
  retryDelayMs?: number | undefined;
}

interface EmbeddingResponse {
  data: Array<{ embedding: number[] }>;
}

/** Error boundary for an embedding provider failure. */
export class EmbeddingError extends Error {
  public constructor(
    message: string,
    public readonly statusCode?: number,
    options?: { cause?: unknown }
  ) {
    super(message, options);
    this.name = "EmbeddingError";
  }
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
    const url = `${this.config.baseUrl.replace(/\/$/, "")}/embeddings`;
    const attempts = Math.max(1, this.config.retryAttempts ?? 3);
    const timeoutMs = Math.max(100, this.config.timeoutMs ?? 15_000);
    let lastFailure: unknown;

    for (let attempt = 1; attempt <= attempts; attempt += 1) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), timeoutMs);
      try {
        const response = await fetch(url, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(this.config.apiKey ? { Authorization: `Bearer ${this.config.apiKey}` } : {})
          },
          body: JSON.stringify({
            model: this.config.model,
            input: texts,
            dimensions: this.config.dimensions
          }),
          signal: controller.signal
        });

        if (!response.ok) {
          const failure = new EmbeddingError(
            `Embedding API error: HTTP ${response.status}`,
            response.status
          );
          if (!this.isRetryableStatus(response.status) || attempt === attempts) throw failure;
          lastFailure = failure;
        } else {
          const data = (await response.json()) as EmbeddingResponse;
          if (!Array.isArray(data.data) || data.data.length !== texts.length) {
            throw new EmbeddingError("Embedding API returned an unexpected item count");
          }
          return data.data.map((item, index) => {
            if (!Array.isArray(item.embedding)
                || item.embedding.length !== this.config.dimensions
                || item.embedding.some((value) => !Number.isFinite(value))) {
              throw new EmbeddingError(
                `Embedding API returned an invalid vector at index ${index}`
              );
            }
            return item.embedding;
          });
        }
      } catch (error) {
        lastFailure = error;
        if (error instanceof EmbeddingError) {
          if (error.statusCode === undefined || !this.isRetryableStatus(error.statusCode)) {
            throw error;
          }
        }
        if (attempt === attempts) {
          throw error instanceof EmbeddingError
            ? error
            : new EmbeddingError("Embedding API request failed", undefined, { cause: error });
        }
      } finally {
        clearTimeout(timeout);
      }

      await new Promise<void>((resolve) => {
        setTimeout(resolve, (this.config.retryDelayMs ?? 250) * attempt);
      });
    }

    throw new EmbeddingError("Embedding API request failed", undefined, { cause: lastFailure });
  }

  private isRetryableStatus(status: number): boolean {
    return status === 408 || status === 429 || status >= 500;
  }
}
