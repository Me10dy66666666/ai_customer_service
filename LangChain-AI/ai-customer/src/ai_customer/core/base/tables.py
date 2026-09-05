from __future__ import annotations

from collections.abc import Sequence

from sqlalchemy import MetaData, Table

from ai_customer.core.base.mysqlConnector import get_engine


def get_ai_customer_tables(engine=None) -> Sequence[Table]:
    """Reflect only the business tables that are approved for knowledge ingestion."""

    metadata = MetaData()
    source_engine = engine or get_engine()
    return [Table("knowledge_documents", metadata, autoload_with=source_engine)]
