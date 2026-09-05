"""Customer workflow contract tests with the canonical knowledge-service seam."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))


class FakeKnowledgeBaseClient:
    def search(self, query_text: str, limit: int = 5, where=None):
        if "天气" in query_text:
            return []
        return [{
            "id": "doc-1",
            "title": "退款政策",
            "excerpt": "支持 7 天无理由退款。",
            "metadata": {"table_name": "knowledge_documents", "row_id": "1"},
        }][:limit]


@pytest.fixture
def workflow_with_stubbed_agent(monkeypatch):
    from ai_customer.tools import vector_search_tool
    from ai_customer.workflows import customerSverviceWorkflow as workflow_module

    captured = {}

    def fake_run_customer_agent(user_input, his_ords, user_type, context):
        captured.update({
            "user_input": user_input,
            "his_ords": his_ords,
            "user_type": user_type,
            "context": context,
        })
        return "【桩】客服回复：已根据知识库为您解答。"

    monkeypatch.setattr(workflow_module, "run_customer_agent", fake_run_customer_agent)
    monkeypatch.setattr(vector_search_tool, "get_vector_store", lambda: FakeKnowledgeBaseClient())
    return captured


def test_customer_workflow_retrieves_through_service_boundary(workflow_with_stubbed_agent):
    from ai_customer.workflows import customerSverviceWorkflow as workflow_module

    reply = workflow_module.run_customer_service_workflow(
        user_input="我想了解订单退款政策",
        history_orders="2025 年购买过扫地机器人",
        userType=1,
    )

    captured = workflow_with_stubbed_agent
    assert reply == "【桩】客服回复：已根据知识库为您解答。"
    assert captured["user_input"] == "我想了解订单退款政策"
    assert captured["his_ords"] == "2025 年购买过扫地机器人"
    assert captured["user_type"] == 1
    assert "退款" in captured["context"]
    assert "knowledge_documents" in captured["context"]


def test_customer_workflow_without_results_still_replies(workflow_with_stubbed_agent):
    from ai_customer.workflows import customerSverviceWorkflow as workflow_module

    reply = workflow_module.run_customer_service_workflow(
        user_input="今天天气怎么样",
        history_orders="",
        userType=0,
    )

    assert reply == "【桩】客服回复：已根据知识库为您解答。"
    assert workflow_with_stubbed_agent["user_type"] == 0
    assert workflow_with_stubbed_agent["context"].strip() == "未找到与您问题相关的信息。"
