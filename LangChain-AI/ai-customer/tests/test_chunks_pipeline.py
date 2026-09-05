"""Tests for the MySQL-to-data-pipeline knowledge ingestion boundary."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

_src = Path(__file__).resolve().parent.parent / "src"
if str(_src) not in sys.path:
    sys.path.insert(0, str(_src))


class FakeKnowledgeBaseClient:
    def __init__(self) -> None:
        self.documents: list[dict] = []

    def ingest_document(self, document_id, filename, content, metadata=None):
        self.documents.append({
            "document_id": document_id,
            "filename": filename,
            "content": content,
            "metadata": dict(metadata or {}),
        })
        return 1


@pytest.mark.skip_db
def test_db_connectivity():
    """Verify the optional MySQL source connection when explicitly enabled."""

    from sqlalchemy import text

    from ai_customer.core.base.mysqlConnector import get_engine

    with get_engine().connect() as connection:
        assert connection.execute(text("SELECT 1")).scalar() == 1


@pytest.mark.skip_db
def test_table_reflection():
    """Verify the approved source table can be reflected from MySQL."""

    from ai_customer.core.base.tables import get_ai_customer_tables

    tables = get_ai_customer_tables()
    assert {table.name for table in tables} == {"knowledge_documents"}
    assert all(len(table.columns) > 0 for table in tables)


def test_knowledge_document_extraction():
    from ai_customer.scripts.chunks import extract_knowledge_document

    class Row:
        title = "FAQ-重置密码"
        content = "请通过个人设置页面修改密码。"
        category = "技术文档"
        plushed_at = "2026-01-15"
        reviewed_by = "张三"

    result = extract_knowledge_document(Row())
    assert "文章标题：FAQ-重置密码" in result
    assert "文章内容：请通过个人设置页面修改密码。" in result
    assert "分类：技术文档" in result
    assert "发布时间：2026-01-15" in result
    assert "审核人：张三" in result


def test_chunking_basic():
    from langchain_text_splitters import RecursiveCharacterTextSplitter

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=50,
        chunk_overlap=0,
        separators=["\n\n", "\n", "。", "！", "？"],
    )
    chunks = splitter.split_text("第一段。第二段。第三段。第四段。")
    assert chunks
    assert all(len(chunk) <= 50 for chunk in chunks)


def test_data_pipeline_client_request_contract(monkeypatch):
    from ai_customer.core.base.vector_store import KnowledgeBaseClient

    client = KnowledgeBaseClient(
        base_url="http://pipeline.test/",
        service_token="pipeline-secret",
        dataset_id="customer-service",
    )
    calls = []

    def request(path, payload, method="POST"):
        calls.append((path, payload, method))
        if path == "/ingest":
            return {"documentId": "doc-1", "chunkCount": 3}
        if path == "/search":
            return {"sources": [{"id": "doc-1", "title": "FAQ", "excerpt": "退款规则"}]}
        return {}

    monkeypatch.setattr(client, "_request", request)
    assert client.ingest_documents(["doc-1"], ["退款规则"], [{"table_name": "knowledge_documents"}]) == 1
    assert client.search("退款", limit=2)[0]["id"] == "doc-1"
    assert calls[0][0] == "/ingest"
    assert calls[0][1]["datasetId"] == "customer-service"
    assert calls[0][1]["metadata"]["table_name"] == "knowledge_documents"


def test_data_pipeline_client_delete_contract(monkeypatch):
    from ai_customer.core.base.vector_store import KnowledgeBaseClient

    client = KnowledgeBaseClient("http://pipeline.test", "secret")
    calls = []
    monkeypatch.setattr(
        client,
        "_request",
        lambda path, payload, method="POST": calls.append((path, payload, method)) or {},
    )
    assert client.delete_documents(["doc/1", "doc-2"]) == 2
    assert calls == [
        ("/documents/doc%2F1", None, "DELETE"),
        ("/documents/doc-2", None, "DELETE"),
    ]


@pytest.fixture
def sqlite_engine(tmp_path):
    from sqlalchemy import create_engine, text

    engine = create_engine(f"sqlite:///{tmp_path / 'test_e2e.db'}", echo=False)
    with engine.begin() as connection:
        connection.execute(text("""
            CREATE TABLE knowledge_documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                content TEXT,
                category TEXT,
                plushed_at TEXT,
                reviewed_by TEXT
            )
        """))
        connection.execute(
            text("INSERT INTO knowledge_documents (title, content, category, plushed_at, reviewed_by) "
                 "VALUES (:title, :content, :category, :published, :reviewed)"),
            {
                "title": "常见问题",
                "content": "如何重置密码？请按以下步骤操作。",
                "category": "技术文档",
                "published": "2026-01-01",
                "reviewed": "张三",
            },
        )
    return engine


def test_full_ingestion_pipeline_with_fake_vector_service(sqlite_engine):
    from sqlalchemy import MetaData, Table

    from ai_customer.scripts.chunks import fetch_and_ingest

    metadata = MetaData()
    table = Table("knowledge_documents", metadata, autoload_with=sqlite_engine)
    vector_store = FakeKnowledgeBaseClient()

    assert fetch_and_ingest(
        engine=sqlite_engine,
        tables=[table],
        vector_store=vector_store,
    ) == 1
    assert len(vector_store.documents) == 1
    document = vector_store.documents[0]
    assert document["document_id"].startswith("knowledge_documents_")
    assert "重置密码" in document["content"]
    assert document["metadata"]["table_name"] == "knowledge_documents"
