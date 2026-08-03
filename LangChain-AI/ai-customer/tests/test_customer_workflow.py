"""
Customer 工作流真实代码测试。

真实执行的代码路径：
    run_customer_service_workflow
      -> search_node  (真实 ChromaDB 向量检索，通过 query_knowledge 工具)
      -> agent_node   (仅对 LLM agent 打桩，避免测试时真实调用大模型)

验证点：
1. 工作流在 langgraph 1.x 下可正常编译（节点名使用字符串写法）
2. search_node 在真实 ChromaDB 中检索到种子知识文档
3. 检索结果作为 context 透传给 agent_node
4. agent_node 收到正确的 user_input / history_orders / userType
5. 返回值为 agent 的最终回复

运行方式（在 ai-customer 目录下执行）：
    python -m pytest tests/test_customer_workflow.py -v -s
"""
from __future__ import annotations

import shutil
import sys
import warnings
from importlib.util import find_spec
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))


# ==============================================================
# sentence-transformers 未安装 -> 降级到 ONNX DefaultEmbeddingFunction
# （已安装时使用真实嵌入模型，与 test_chunks_pipeline 行为一致）
# ==============================================================
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
    )

from ai_customer.core.base.vector_store import VectorStore  # noqa: E402
from ai_customer.workflows import customerSverviceWorkflow as customer_workflow_mod  # noqa: E402


# ==============================================================
# Fixtures
# ==============================================================

@pytest.fixture(scope="function")
def seeded_vectorstore(tmp_path):
    """构建临时 ChromaDB 向量库并写入 2 条知识文档。"""
    persist = tmp_path / "chroma_customer_test"
    vs = VectorStore(
        collection_name="test_customer_kb",
        embedding_model="paraphrase-multilingual-MiniLM-L12-v2",
        persist_directory=str(persist),
    )
    vs.add_documents(
        ids=["doc-1", "doc-2"],
        documents=[
            "智能客服知识库：订单退款政策，支持 7 天无理由退款。",
            "智能客服知识库：会员等级分为普通会员与黄金会员，黄金会员享优先人工服务。",
        ],
        metadatas=[
            {"table_name": "knowledge_documents", "row_id": "1"},
            {"table_name": "knowledge_documents", "row_id": "2"},
        ],
        show_progress=False,
    )
    yield vs
    try:
        vs.delete_collection()
    except Exception:
        pass
    shutil.rmtree(persist, ignore_errors=True)


@pytest.fixture(scope="function")
def workflow_with_stubbed_agent(monkeypatch, seeded_vectorstore):
    """将工作流中的 LLM agent 替换为桩；向量库指向真实临时库。"""

    captured = {}

    def fake_run_customer_agent(user_input, his_ords, user_type, context):
        captured["user_input"] = user_input
        captured["his_ords"] = his_ords
        captured["user_type"] = user_type
        captured["context"] = context
        return "【桩】客服回复：已根据知识库为您解答。"

    # 注意：必须 patch 工作流模块内已绑定的引用，而非 customer_agent 模块
    monkeypatch.setattr(customer_workflow_mod, "run_customer_agent", fake_run_customer_agent)

    # 让 query_knowledge 工具命中真实临时向量库
    import ai_customer.tools.vector_search_tool as vector_tool_mod

    monkeypatch.setattr(vector_tool_mod, "get_vector_store", lambda: seeded_vectorstore)

    return captured


# ==============================================================
# 测试用例
# ==============================================================

def test_customer_workflow_real_search_and_agent_chain(workflow_with_stubbed_agent):
    """真实链路：向量检索命中知识文档 -> context 透传 -> agent 回复。"""
    reply = customer_workflow_mod.run_customer_service_workflow(
        user_input="我想了解订单退款政策",
        history_orders="2025 年购买过扫地机器人",
        userType=1,
    )

    captured = workflow_with_stubbed_agent
    assert reply == "【桩】客服回复：已根据知识库为您解答。"
    assert captured["user_input"] == "我想了解订单退款政策"
    assert captured["his_ords"] == "2025 年购买过扫地机器人"
    assert captured["user_type"] == 1
    # 关键断言：search_node 真的从向量库检索到了种子文档（真实代码路径）
    assert "退款" in captured["context"]
    assert "knowledge_documents" in captured["context"]


def test_customer_workflow_no_match_still_replies(workflow_with_stubbed_agent):
    """查询与知识库无关时，工作流不崩溃，agent 仍正常返回。"""
    reply = customer_workflow_mod.run_customer_service_workflow(
        user_input="今天天气怎么样",
        history_orders="",
        userType=0,
    )

    captured = workflow_with_stubbed_agent
    assert reply == "【桩】客服回复：已根据知识库为您解答。"
    assert captured["user_type"] == 0
    # 向量检索必然返回最相近的 top_k 结果，context 应非空
    assert captured["context"].strip() != ""
