from __future__ import annotations

import logging

from sqlalchemy import inspect, select

from ai_customer.config.settings import get_settings
from ai_customer.core.base.mysqlConnector import get_engine
from ai_customer.core.base.tables import get_ai_customer_tables
from ai_customer.core.base.vector_store import KnowledgeBaseClient


logger = logging.getLogger(__name__)


def extract_knowledge_document(row) -> str:
    """Convert an approved knowledge row to the document sent to data-pipeline."""

    return (
        f"文章标题：{row.title}\n"
        f"文章内容：{row.content}\n"
        f"分类：{row.category}\n"
        f"发布时间：{row.plushed_at}\n"
        f"审核人：{row.reviewed_by}"
    )


def fetch_and_ingest(engine=None, tables=None, vector_store: KnowledgeBaseClient | None = None) -> int:
    """Read approved business rows and ingest logical documents into pgvector."""

    source_engine = engine or get_engine()
    source_tables = tables or get_ai_customer_tables(source_engine)
    client = vector_store or KnowledgeBaseClient(
        base_url=get_settings().vector.base_url,
        service_token=get_settings().vector.service_token,
        dataset_id=get_settings().vector.dataset_id,
        timeout_ms=get_settings().vector.timeout_ms,
        retry_attempts=get_settings().vector.retry_attempts,
        retry_delay_ms=get_settings().vector.retry_delay_ms,
    )
    ingested = 0

    with source_engine.connect() as connection:
        for table in source_tables:
            if table.name != "knowledge_documents":
                logger.info("skipping unapproved knowledge source: %s", table.name)
                continue
            primary_keys = [column.name for column in inspect(table).primary_key]
            for row in connection.execute(select(table)):
                content = extract_knowledge_document(row)
                row_data = dict(row._mapping)
                row_id = "_".join(str(row_data[key]) for key in primary_keys)
                document_id = f"{table.name}_{row_id}"
                metadata = {
                    str(key): str(value)
                    for key, value in row_data.items()
                    if key not in {"content", "product_model"} and value is not None
                }
                metadata.update({"table_name": table.name, "row_id": row_id})
                client.ingest_document(
                    document_id=document_id,
                    filename=f"{table.name}-{row_id}",
                    content=content,
                    metadata=metadata,
                )
                ingested += 1

    logger.info("knowledge ingestion complete: %s logical documents", ingested)
    return ingested


if __name__ == "__main__":
    fetch_and_ingest()
