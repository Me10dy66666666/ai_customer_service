from __future__ import annotations

import logging
from functools import lru_cache

from langchain_core.tools import tool

from ai_customer.config.settings import get_settings
from ai_customer.core.base.vector_store import KnowledgeBaseClient, VectorStoreError


logger = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def get_vector_store() -> KnowledgeBaseClient:
    """Return the process-scoped adapter to the shared data-pipeline service."""

    vector = get_settings().vector
    return KnowledgeBaseClient(
        base_url=vector.base_url,
        service_token=vector.service_token,
        dataset_id=vector.dataset_id,
        timeout_ms=vector.timeout_ms,
        retry_attempts=vector.retry_attempts,
        retry_delay_ms=vector.retry_delay_ms,
    )


@tool("query_knowledge", description="根据用户的提问从共享知识库中查询信息。")
def query_knowledge(query: str, top_k: int = 5) -> str:
    """Retrieve bounded, cited knowledge sources for the customer question."""

    try:
        sources = get_vector_store().search(query, limit=min(max(top_k, 1), 10))
    except (VectorStoreError, ValueError) as error:
        logger.warning("knowledge retrieval failed: %s", error)
        return "知识库暂时不可用，请稍后再试。"

    if not sources:
        return "未找到与您问题相关的信息。"

    formatted_results: list[str] = []
    for index, source in enumerate(sources, 1):
        metadata = source.get("metadata")
        metadata_map = metadata if isinstance(metadata, dict) else {}
        title = str(source.get("title") or metadata_map.get("table_name") or "未知来源")
        row_id = str(metadata_map.get("row_id") or source.get("id") or "未知记录")
        excerpt = str(source.get("excerpt") or "").strip()
        formatted_results.append(f"【结果 {index}】来源：{title}（{row_id}）\n{excerpt}")

    return "\n\n".join(formatted_results)


def get_knowledge_base_tools() -> list:
    """Return the knowledge tools exposed to the LangChain agent."""

    return [query_knowledge]
