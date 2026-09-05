import { readFile } from "node:fs/promises";

import { loadPipelineConfig } from "../config.js";
import { parseChromaExport } from "../migration/chromaExport.js";
import { PgVectorStore } from "../vector/pgVectorStore.js";

async function main(): Promise<void> {
  const exportPath = process.argv[2];
  if (!exportPath) {
    throw new Error("Usage: npm run migrate:chroma -- <chroma-export.json> [datasetId]");
  }
  const datasetId = process.argv[3] ?? "default";
  const input = JSON.parse(await readFile(exportPath, "utf8")) as unknown;
  const batch = parseChromaExport(input, datasetId);
  const config = loadPipelineConfig();
  const store = new PgVectorStore({
    connectionString: config.postgres.connectionString,
    dimensions: config.embedding.dimensions,
    poolMax: config.postgres.poolMax,
    idleTimeoutMs: config.postgres.idleTimeoutMs,
    connectionTimeoutMs: config.postgres.connectionTimeoutMs,
    statementTimeoutMs: config.postgres.statementTimeoutMs,
    retryAttempts: config.postgres.retryAttempts,
    retryDelayMs: config.postgres.retryDelayMs
  });
  try {
    await store.initialize();
    const batchSize = 100;
    for (let index = 0; index < batch.documents.length; index += batchSize) {
      await store.add(
        batch.documents.slice(index, index + batchSize),
        batch.embeddings.slice(index, index + batchSize)
      );
    }
    console.info(`Migrated ${batch.documents.length} vectors into PostgreSQL + pgvector.`);
  } finally {
    await store.close();
  }
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
