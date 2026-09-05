from __future__ import annotations

import json
import logging
import time
from collections.abc import Mapping, Sequence
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


logger = logging.getLogger(__name__)


class VectorStoreError(RuntimeError):
    """Raised when the shared data-pipeline/vector boundary cannot be completed."""


class KnowledgeBaseClient:
    """Small HTTP adapter for the canonical PostgreSQL + pgvector service.

    The Python workflow never opens a local vector database or loads an embedding
    model. Chunking, embedding, filtering, and pgvector retrieval remain owned by
    ``data-pipeline`` so all application stacks share one storage contract.
    """

    def __init__(
        self,
        base_url: str,
        service_token: str,
        dataset_id: str = "default",
        timeout_ms: int = 5_000,
        retry_attempts: int = 2,
        retry_delay_ms: int = 200,
    ) -> None:
        normalized_url = base_url.strip().rstrip("/")
        if not normalized_url.startswith(("http://", "https://")):
            raise ValueError("data-pipeline URL must use http or https")
        if not dataset_id.strip():
            raise ValueError("vector dataset id must not be empty")
        if timeout_ms <= 0 or retry_attempts < 0 or retry_delay_ms < 0:
            raise ValueError("vector timeout/retry settings must be non-negative")
        self.base_url = normalized_url
        self.service_token = service_token
        self.dataset_id = dataset_id.strip()
        self.timeout_seconds = timeout_ms / 1_000
        self.retry_attempts = retry_attempts
        self.retry_delay_seconds = retry_delay_ms / 1_000

    def ingest_document(
        self,
        document_id: str,
        filename: str,
        content: str,
        metadata: Mapping[str, Any] | None = None,
    ) -> int:
        """Ingest one logical document; data-pipeline owns chunking and embeddings."""

        if not document_id.strip() or not filename.strip() or not content.strip():
            raise ValueError("document_id, filename, and content must not be empty")
        values = {str(key): str(value) for key, value in (metadata or {}).items() if value is not None}
        roles = [role.strip() for role in values.get("allowedRoles", "PUBLIC").split(",") if role.strip()]
        payload: dict[str, Any] = {
            "filename": filename.strip()[:255],
            "content": content,
            "datasetId": self.dataset_id,
            "documentId": document_id.strip(),
            "knowledgeDomain": values.get("knowledgeDomain", "customer-service"),
            "allowedRoles": roles or ["PUBLIC"],
            "metadata": values,
        }
        expires_at = values.get("expiresAt")
        if expires_at:
            payload["expiresAt"] = expires_at
        response = self._request("/ingest", payload)
        chunk_count = response.get("chunkCount")
        if not isinstance(chunk_count, int) or chunk_count < 0:
            raise VectorStoreError("data-pipeline returned an invalid chunk count")
        return chunk_count

    def ingest_documents(
        self,
        ids: Sequence[str],
        documents: Sequence[str],
        metadatas: Sequence[Mapping[str, Any]] | None = None,
    ) -> int:
        """Ingest a sequence and return the number of successfully submitted documents."""

        if len(ids) != len(documents):
            raise ValueError("ids and documents must have equal lengths")
        if metadatas is not None and len(metadatas) != len(ids):
            raise ValueError("metadatas and ids must have equal lengths")
        submitted = 0
        for index, (document_id, content) in enumerate(zip(ids, documents, strict=True)):
            metadata = metadatas[index] if metadatas is not None else None
            self.ingest_document(document_id, document_id, content, metadata)
            submitted += 1
        return submitted

    def search(
        self,
        query_text: str,
        limit: int = 5,
        where: Mapping[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        """Return canonical knowledge sources from server-side filtered retrieval."""

        if not isinstance(query_text, str) or not query_text.strip():
            raise ValueError("query_text must be a non-empty string")
        if limit < 1:
            raise ValueError("limit must be positive")
        filters = dict(where or {})
        payload: dict[str, Any] = {
            "query": query_text,
            "limit": min(limit, 50),
            "datasetId": str(filters.get("datasetId", self.dataset_id)),
        }
        for key in ("knowledgeDomain", "roles"):
            if key in filters and filters[key] is not None:
                payload[key] = filters[key]
        response = self._request("/search", payload)
        sources = response.get("sources", [])
        if not isinstance(sources, list):
            raise VectorStoreError("data-pipeline returned an invalid source list")
        return [source for source in sources if isinstance(source, dict)]

    def delete_documents(self, ids: Sequence[str]) -> int:
        """Delete logical documents through the data-pipeline lifecycle endpoint."""

        deleted = 0
        for document_id in ids:
            if not str(document_id).strip():
                continue
            self._request(f"/documents/{quote(str(document_id), safe='')}", None, method="DELETE")
            deleted += 1
        return deleted

    def _request(self, path: str, payload: Mapping[str, Any] | None, method: str = "POST") -> dict[str, Any]:
        body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if self.service_token:
            headers["Authorization"] = f"Bearer {self.service_token}"

        for attempt in range(self.retry_attempts + 1):
            request = Request(f"{self.base_url}{path}", data=body, headers=headers, method=method)
            try:
                with urlopen(request, timeout=self.timeout_seconds) as response:
                    raw = response.read()
                decoded = json.loads(raw.decode("utf-8"))
                if not isinstance(decoded, dict):
                    raise VectorStoreError("data-pipeline returned a non-object response")
                return decoded
            except HTTPError as error:
                retryable = error.code in {408, 429} or error.code >= 500
                if not retryable or attempt >= self.retry_attempts:
                    raise VectorStoreError(f"data-pipeline request failed: HTTP {error.code}") from error
            except (URLError, TimeoutError, OSError) as error:
                if attempt >= self.retry_attempts:
                    raise VectorStoreError("data-pipeline request failed") from error
            if self.retry_delay_seconds > 0:
                time.sleep(self.retry_delay_seconds * (attempt + 1))

        raise VectorStoreError("data-pipeline request exhausted retries")
