import type { StoredDocument } from "../vector/vectorStore.js";

export interface ChromaMigrationBatch {
  documents: StoredDocument[];
  embeddings: number[][];
}

export class ChromaMigrationError extends Error {
  public constructor(message: string) {
    super(message);
    this.name = "ChromaMigrationError";
  }
}

/**
 * Converts the JSON returned by Chroma `collection.get({ include: [...] })` into
 * the repository's storage-neutral write shape. The parser intentionally has no
 * Chroma dependency, so the one-time migration can run after the production
 * dependency has been removed.
 */
export function parseChromaExport(input: unknown, defaultDatasetId = "default"): ChromaMigrationBatch {
  if (Array.isArray(input)) return parseRecordExport(input, defaultDatasetId);
  if (!isRecord(input)) throw new ChromaMigrationError("Chroma export must be a JSON object or record array");

  const ids = flattenStrings(input.ids, "ids");
  const documents = flattenStrings(input.documents, "documents");
  const metadatas = input.metadatas === undefined ? [] : flattenColumn(input.metadatas, "metadatas");
  const embeddings = flattenEmbeddings(input.embeddings);
  if (ids.length === 0) return { documents: [], embeddings: [] };
  if (documents.length !== ids.length || embeddings.length !== ids.length
      || (metadatas.length !== 0 && metadatas.length !== ids.length)) {
    throw new ChromaMigrationError("Chroma export ids, documents, and embeddings must have equal lengths");
  }

  return buildBatch(ids.map((id, index) => ({
    id,
    content: documents[index],
    embedding: embeddings[index],
    metadata: metadatas[index]
  })), defaultDatasetId);
}

function parseRecordExport(records: unknown[], defaultDatasetId: string): ChromaMigrationBatch {
  const normalized = records.map((record, index) => {
    if (!isRecord(record) || typeof record.id !== "string") {
      throw new ChromaMigrationError(`Chroma export record ${index} has no string id`);
    }
    return {
      id: record.id,
      content: record.content ?? record.document,
      embedding: record.embedding,
      metadata: record.metadata
    };
  });
  return buildBatch(normalized, defaultDatasetId);
}

function buildBatch(
  records: Array<{ id: string; content: unknown; embedding: unknown; metadata: unknown }>,
  defaultDatasetId: string
): ChromaMigrationBatch {
  const documents: StoredDocument[] = [];
  const embeddings: number[][] = [];
  for (const [index, record] of records.entries()) {
    if (typeof record.content !== "string" || record.content.length === 0) {
      throw new ChromaMigrationError(`Chroma export record ${index} has no document text`);
    }
    if (!Array.isArray(record.embedding)
        || record.embedding.some((value) => typeof value !== "number" || !Number.isFinite(value))) {
      throw new ChromaMigrationError(`Chroma export record ${index} has no finite embedding vector`);
    }
    const metadata = toStringMetadata(record.metadata);
    metadata.datasetId ??= defaultDatasetId;
    metadata.docId ??= metadata.document_id ?? record.id;
    metadata.chunkId ??= metadata.chunk_id ?? `${metadata.chunkKind ?? "chunk"}-${metadata.chunkIndex ?? record.id}`;
    metadata.enabled ??= "true";
    documents.push({
      id: record.id,
      content: record.content,
      documentId: metadata.docId,
      chunkId: metadata.chunkId,
      metadata
    });
    embeddings.push(record.embedding);
  }
  return { documents, embeddings };
}

function flattenColumn(value: unknown, name: string): unknown[] {
  const column = Array.isArray(value) && Array.isArray(value[0]) ? value[0] : value;
  if (!Array.isArray(column)) throw new ChromaMigrationError(`Chroma export is missing ${name}`);
  return column;
}

function flattenStrings(value: unknown, name: string): string[] {
  const column = flattenColumn(value, name);
  return column.map((item, index) => {
    if (typeof item !== "string") throw new ChromaMigrationError(`${name}[${index}] must be a string`);
    return item;
  });
}

function flattenEmbeddings(value: unknown): number[][] {
  const column = Array.isArray(value) && Array.isArray(value[0]) && Array.isArray(value[0][0])
    ? value[0]
    : value;
  if (!Array.isArray(column)) throw new ChromaMigrationError("Chroma export is missing embeddings");
  return column.map((item, index) => {
    if (!Array.isArray(item)) throw new ChromaMigrationError(`embeddings[${index}] must be a vector`);
    return item as unknown[] as number[];
  });
}

function toStringMetadata(value: unknown): Record<string, string> {
  if (!isRecord(value)) return {};
  const metadata: Record<string, string> = {};
  for (const [key, item] of Object.entries(value)) {
    if (item === null || item === undefined) continue;
    metadata[key] = typeof item === "string" ? item : JSON.stringify(item);
  }
  return metadata;
}

function isRecord(value: unknown): value is Record<string, any> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
