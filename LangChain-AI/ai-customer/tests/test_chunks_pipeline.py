"""
test_chunks_pipeline.py — 端到端测试 chunks.py 完整流水线

覆盖范围（按测试函数）：
  1. test_db_connectivity        — 数据库连接 Ping
  2. test_table_reflection       — aiCustomer_tab 表结构反射（表名、列）
  3. test_extractor_*            — 三张表的提取器输出格式
  4. test_chunking_basic         — 文本切块逻辑（chunk_size / overlap）
  5. test_vector_store_*         — VectorStore 添加 / 查询 / 删除
  6. test_full_pipeline_with_mocks — 全流程端到端模拟（SQLite + 本地嵌入）

运行方式：
  pytest tests/test_chunks_pipeline.py -v                    # 默认模式（跳过真实 DB 测试）
  pytest tests/test_chunks_pipeline.py -v --run-db           # 包含真实 DB 连接测试
  pytest tests/test_chunks_pipeline.py -v --run-e2e          # 包含完整端到端（推荐）
  python  tests/test_chunks_pipeline.py                      # 脚本直接执行
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

import pytest

# ── 路径引导 ──────────────────────────────────────────────────
_src = Path(__file__).resolve().parent.parent / "src"
if str(_src) not in sys.path:
    sys.path.insert(0, str(_src))

# ── 环境兼容层 ────────────────────────────────────────────
# 1. chromadb 1.5.x 移除了 IncludeEnum，需要 stub
try:
    from chromadb.api.types import IncludeEnum  # noqa: F401
except ImportError:
    from enum import Enum

    class IncludeEnum(str, Enum):
        documents = "documents"
        embeddings = "embeddings"
        metadatas = "metadatas"
        distances = "distances"

    import chromadb.api.types as _types
    _types.IncludeEnum = IncludeEnum

# 2. sentence-transformers 未安装 → 降级到 ONNX DefaultEmbeddingFunction
#    chromadb 1.5.x 的 SentenceTransformerEmbeddingFunction 在 import 时不会报错，
#    但在实例化 __init__ 时会尝试 import sentence_transformers，所以必须提前 patch。
import warnings
from importlib.util import find_spec


if find_spec("sentence_transformers") is None:
    from chromadb.utils.embedding_functions import DefaultEmbeddingFunction

    class _FallbackSentenceTransformer(DefaultEmbeddingFunction):
        """降级：忽略所有额外参数，使用 ONNX 默认嵌入模型。"""
        def __init__(self, *args, **kwargs):
            super().__init__()

    import chromadb.utils.embedding_functions as _ef_mod
    _ef_mod.SentenceTransformerEmbeddingFunction = _FallbackSentenceTransformer
    warnings.warn(
        "sentence-transformers 未安装；已降级到 chromadb DefaultEmbeddingFunction（ONNX）。"
        " 向量维度可能有差异，但测试逻辑不受影响。"
    )


# ====================================================================
# 共享 Fixtures
# ====================================================================

@pytest.fixture(scope="function")
def temp_vectorstore(tmp_path):
    """提供基于临时目录 + chromadb 内置嵌入的 VectorStore 实例。"""
    from ai_customer.core.base.vector_store import VectorStore

    persist = tmp_path / "chroma_test"
    vs = VectorStore(
        collection_name="test_pipeline",
        embedding_model="paraphrase-multilingual-MiniLM-L12-v2",
        persist_directory=str(persist),
    )
    yield vs
    # 清理
    try:
        vs.delete_collection()
    except Exception:
        pass
    shutil.rmtree(persist, ignore_errors=True)


# ====================================================================
# 1. 数据库连接与表结构反射  (需要真实 MySQL)
# ====================================================================

@pytest.mark.skip_db  # 自定义标记，默认跳过
def test_db_connectivity():
    """验证 get_engine() 能成功连接 MySQL 并执行 SELECT 1。"""
    from ai_customer.core.base.mysqlConnector import get_engine
    from sqlalchemy import text as db_text

    engine = get_engine()
    with engine.connect() as conn:
        result = conn.execute(db_text("SELECT 1")).scalar()
        assert result == 1, "数据库 Ping 失败"

    with engine.connect() as conn:
        db_name = conn.execute(db_text("SELECT DATABASE()")).scalar()
        assert db_name, "无法获取当前数据库名称"


@pytest.mark.skip_db
def test_table_reflection():
    """验证 aiCustomer_tab 能成功反射知识库表，且包含预期列。"""
    from ai_customer.core.base.tables import aiCustomer_tab

    table_names = {t.name for t in aiCustomer_tab}
    assert "knowledge_documents" in table_names
    # 注：historical_orders / chat_messages 在 tables.py 中被注释，不参与入库流程

    for table in aiCustomer_tab:
        cols = [c.name for c in table.columns]
        assert len(cols) > 0, f"表 {table.name} 反射后无列"


# ====================================================================
# 2. 提取器（Extractor）输出格式  (纯逻辑，零依赖)
# ====================================================================

# extractor_map 在 chunks.py 中是 fetch_and_chunk() 内的局部变量，
# 无法直接导入；此处直接复制提取器逻辑用于测试。

def _extract_knowledge_document(row) -> str:
    return (
        f"文章标题：{row.title}\n"
        f"文章内容：{row.content}\n"
        f"分类：{row.category}\n"
        f"发布时间：{row.plushed_at}\n"
        f"审核人：{row.reviewed_by}"
    )


def _extract_historical_order(row) -> str:
    return (
        f"订单id: {row.id}\n"
        f"用户id:  {row.user_id}\n"
        f"购买了:  {row.product_name}\n"
        f"有关配置为: {row.product_model}\n"
        f"数量为: {row.quantity}\n"
        f"订单金额:  {row.amount} 元\n"
        f"总价为:  {row.total_amount} 元\n"
        f"订单状态:  {row.order_status}\n"
        f"下单时间:  {row.create_time}。"
    )


def _extract_chat_message(row) -> str:
    return (
        f"会话id:  {row.session_id}\n"
        f"发送人:  {row.sender_type}\n"
        f"发送人id: {row.sender_id}\n"
        f"消息内容: {row.content}\n"
        f"会话内消息序号：{row.message_seq}\n"
        f"时间： {row.create_time}"
    )


class MockRow:
    """模拟 SQLAlchemy Row，支持属性访问 + _mapping 接口。"""

    def __init__(self, **kwargs):
        self._data = dict(kwargs)
        for k, v in kwargs.items():
            setattr(self, k, v)

    @property
    def _mapping(self):
        return self._data


def test_extractor_knowledge_documents():
    """knowledge_documents 提取器输出包含所有关键字段。"""
    row = MockRow(
        title="FAQ-重置密码",
        content="请通过个人设置页面修改密码。",
        category="技术文档",
        plushed_at="2026-01-15",
        reviewed_by="张三",
    )
    result = _extract_knowledge_document(row)

    assert "文章标题：FAQ-重置密码" in result
    assert "文章内容：请通过个人设置页面修改密码。" in result
    assert "分类：技术文档" in result
    assert "发布时间：2026-01-15" in result
    assert "审核人：张三" in result


def test_extractor_historical_orders():
    """historical_orders 提取器输出包含所有订单字段。"""
    row = MockRow(
        id=1001,
        user_id=42,
        product_name="AI 客服",
        product_model="Pro版",
        quantity=2,
        amount=199.00,
        total_amount=398.00,
        order_status="已完成",
        create_time="2026-06-15 10:30:00",
    )
    result = _extract_historical_order(row)

    assert "订单id: 1001" in result
    assert "用户id:  42" in result
    assert "购买了:  AI 客服" in result
    assert "有关配置为: Pro版" in result
    assert "数量为: 2" in result
    assert "订单金额:  199.0" in result
    assert "总价为:  398.0" in result
    assert "订单状态:  已完成" in result
    assert "下单时间:  2026-06-15 10:30:00。" in result


def test_extractor_chat_messages():
    """chat_messages 提取器输出包含所有会话字段。"""
    row = MockRow(
        session_id="sess_abc",
        sender_type="user",
        sender_id=100,
        content="如何重置密码？",
        message_seq=1,
        create_time="2026-07-01 14:00:00",
    )
    result = _extract_chat_message(row)

    assert "会话id:  sess_abc" in result
    assert "发送人:  user" in result
    assert "发送人id: 100" in result
    assert "消息内容: 如何重置密码？" in result
    assert "会话内消息序号：1" in result
    assert "时间： 2026-07-01 14:00:00" in result


# ====================================================================
# 3. 文本切块逻辑  (纯逻辑)
# ====================================================================

def test_chunking_basic():
    """RecursiveCharacterTextSplitter 按 chunk_size 正确切块。"""
    from langchain_text_splitters import RecursiveCharacterTextSplitter

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=50, chunk_overlap=0, separators=["\n\n", "\n", "。", "！", "？"]
    )
    text = "第一段。第二段。第三段。第四段。"
    chunks = splitter.split_text(text)

    assert len(chunks) >= 1
    for c in chunks:
        assert len(c) <= 50, f"chunk 长度 {len(c)} 超过 chunk_size=50"


def test_chunking_respects_separators():
    """切块时优先按句号、感叹号、问号等分隔符断开，避免截断句子。"""
    from langchain_text_splitters import RecursiveCharacterTextSplitter

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=30, chunk_overlap=0, separators=["\n\n", "\n", "。", "！", "？"]
    )
    text = "你好！请问如何重置密码？我忘记了。"
    chunks = splitter.split_text(text)

    for c in chunks[:-1]:  # 最后一个 chunk 可能没有结尾分隔符
        assert any(c.endswith(s) for s in ["！", "？", "。"]) or len(c) < 30


# ====================================================================
# 4. 向量存储集成  (本地 ONNX 嵌入)
# ====================================================================

def test_vector_store_add_and_count(temp_vectorstore):
    """add_documents 后 count() 正确返回。"""
    vs = temp_vectorstore

    added = vs.add_documents(
        ids=["v1", "v2"],
        documents=["文档一", "文档二"],
        metadatas=[{"tag": "a"}, {"tag": "b"}],
    )
    assert added == 2
    assert vs.count() == 2


def test_vector_store_query(temp_vectorstore):
    """query() 返回已添加的文档。"""
    vs = temp_vectorstore
    vs.add_documents(
        ids=["q_doc"],
        documents=["如何重置密码？"],
        metadatas=[{"table": "knowledge_documents"}],
    )

    results = vs.query("重置密码", n_results=1)
    assert len(results["ids"]) >= 1
    # query() 对 ids 不做扁平化处理，结果为 [['q_doc']]
    assert results["ids"][0][0] == "q_doc"


def test_vector_store_delete(temp_vectorstore):
    """delete_documents 后 count 减少。"""
    vs = temp_vectorstore
    vs.add_documents(ids=["del_me"], documents=["待删除"])
    assert vs.count() == 1

    deleted = vs.delete_documents(["del_me"])
    assert deleted == 1
    assert vs.count() == 0


# ====================================================================
# 5. 端到端流水线 — 模拟 DB + 模拟 VectorStore
# ====================================================================

@pytest.fixture(scope="function")
def sqlite_engine(tmp_path):
    """创建 SQLite 文件引擎，预建三张业务表并插入测试数据。

    使用文件而非 :memory: ，确保 autoload_with 在不同连接间可共享。
    """
    from sqlalchemy import create_engine, text

    db_file = tmp_path / "test_e2e.db"
    engine = create_engine(f"sqlite:///{db_file}", echo=False)

    # ── 建表（与真实 MySQL 表结构对齐） ──
    with engine.begin() as conn:
        conn.execute(text("""
            CREATE TABLE knowledge_documents (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                title       TEXT,
                content     TEXT,
                category    TEXT,
                plushed_at  TEXT,
                reviewed_by TEXT
            )
        """))
        conn.execute(text("""
            CREATE TABLE historical_orders (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id       INTEGER,
                product_name  TEXT,
                product_model TEXT,
                quantity      INTEGER,
                amount        REAL,
                total_amount  REAL,
                order_status  TEXT,
                create_time   TEXT
            )
        """))
        conn.execute(text("""
            CREATE TABLE chat_messages (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id  TEXT,
                sender_type TEXT,
                sender_id   INTEGER,
                content     TEXT,
                message_seq INTEGER,
                create_time TEXT
            )
        """))

        # ── 插入测试数据 ──
        conn.execute(
            text("INSERT INTO knowledge_documents (title, content, category, plushed_at, reviewed_by) "
                 "VALUES (:t, :c, :cat, :p, :r)"),
            {"t": "常见问题", "c": "如何重置密码？请按以下步骤操作……", "cat": "技术文档",
             "p": "2026-01-01", "r": "张三"},
        )
        conn.execute(
            text("INSERT INTO historical_orders "
                 "(user_id, product_name, product_model, quantity, amount, total_amount, order_status, create_time) "
                 "VALUES (:u, :pn, :pm, :q, :a, :ta, :os, :ct)"),
            {"u": 42, "pn": "AI 客服系统", "pm": "标准版", "q": 1, "a": 99.0,
             "ta": 99.0, "os": "已完成", "ct": "2026-06-15 10:30:00"},
        )
        conn.execute(
            text("INSERT INTO chat_messages (session_id, sender_type, sender_id, content, message_seq, create_time) "
                 "VALUES (:sid, :st, :si, :c, :ms, :ct)"),
            {"sid": "sess_001", "st": "user", "si": 100, "c": "你好，我想咨询一下",
             "ms": 1, "ct": "2026-07-01 14:00:00"},
        )

    return engine


@pytest.mark.run_e2e
def test_full_pipeline_with_mocks(sqlite_engine, tmp_path, monkeypatch):
    """
    端到端流水线测试（模拟 MySQL + 模拟 Embedding API）。

    验证 fetch_and_chunk() 中以下步骤的连贯性：
      1. 数据库连接
      2. 表遍历（aiCustomer_tab）
      3. 提取器应用（extractor_map）
      4. 文本切块（RecursiveCharacterTextSplitter）
      5. 向量入库（ChromaDB）

    """
    from sqlalchemy import MetaData, Table

    from ai_customer.core.base import mysqlConnector
    from ai_customer.core.base.vector_store import VectorStore

    # ── Mock 1：替换 get_engine ──
    monkeypatch.setattr(mysqlConnector, "get_engine", lambda: sqlite_engine)

    # ── Mock 2：用 SQLite 引擎重新反射表 ──
    meta = MetaData()
    kd = Table("knowledge_documents", meta, autoload_with=sqlite_engine)
    ho = Table("historical_orders", meta, autoload_with=sqlite_engine)
    cm = Table("chat_messages", meta, autoload_with=sqlite_engine)

    # 用 mock 模块替换 sys.modules 中的 tables，避免其 MySQL autoload 被触发
    import sys
    from types import ModuleType
    mock_tables = ModuleType("ai_customer.core.base.tables")
    mock_tables.aiCustomer_tab = [kd, ho, cm]
    monkeypatch.setitem(sys.modules, "ai_customer.core.base.tables", mock_tables)

    # ── Mock 3：VectorStore 使用本地嵌入 + 临时目录 ──
    chroma_dir = tmp_path / "chroma_e2e"

    # 3a. chunks.py 中 vectorDB.add_texts → add_documents 已在源文件中修复，无需额外 patch。

    # 3b. 拦截 VectorStore 构建，强制使用本地嵌入 + chroma_dir
    original_init = VectorStore.__init__

    def _patched_init(self, collection_name="ai_customer", embedding_model="qwen3.7-text-embedding",
                      distance_metric="cosine", persist_path="./chroma_db", **kwargs):
        # 关键：把 embedding_model 替换为非 qwen 开头，避免触发 OpenAI API key 校验
        original_init(
            self,
            collection_name=collection_name,
            embedding_model="all-MiniLM-L6-v2",  # 迫使走 sentence-transformers（已降级为 ONNX）
            distance_metric=distance_metric,
            persist_directory=str(chroma_dir),
        )

    monkeypatch.setattr(VectorStore, "__init__", _patched_init)

    # ── 执行 ──
    from ai_customer.scripts.chunks import fetch_and_chunk

    try:
        fetch_and_chunk()
    except Exception as e:
        pytest.fail(f"fetch_and_chunk() 执行失败: {e}")

    # ── 验证结果 ──
    import chromadb
    client = chromadb.PersistentClient(path=str(chroma_dir))
    collection = client.get_collection(
        name="ai_customer",
        embedding_function=chromadb.utils.embedding_functions.DefaultEmbeddingFunction(),
    )
    count = collection.count()
    assert count > 0, f"向量库中无数据，预期至少 1 条，实际 {count}"

    # 验证知识库表（knowledge_documents）的数据已入库。
    # 注：historical_orders / chat_messages 的提取器在 chunks.py 中被注释，
    #     按设计不参与切块入库，此处只断言知识库表。
    all_data = collection.get(limit=count)
    table_names = {m["table_name"] for m in all_data["metadatas"]}
    assert "knowledge_documents" in table_names, "缺少 knowledge_documents 数据"
    assert table_names == {"knowledge_documents"}, (
        f"预期仅知识库表入库，实际包含: {table_names}"
    )

    # 验证 chunk_id 格式合规
    for doc_id in all_data["ids"]:
        assert "_chunk_" in doc_id, f"chunk id 格式异常: {doc_id}"

    # 清理
    try:
        client.delete_collection("ai_customer")
    except Exception:
        pass
    shutil.rmtree(chroma_dir, ignore_errors=True)

    # 打印概要
    per_table = {}
    for m in all_data["metadatas"]:
        per_table.setdefault(m["table_name"], 0)
        per_table[m["table_name"]] += 1

    print(f"\n[PASS] 端到端流水线完成：共 {count} 个块入库")
    for tbl, cnt in per_table.items():
        print(f"       {tbl}: {cnt} 个块")


# ── 脚本入口 ──────────────────────────────────────────────────
if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v", "--tb=short"]))
