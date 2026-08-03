"""
API 层集成测试：POST /api/v1/agent/customerService

在真实 FastAPI 应用（ai_customer.__main__.app）上发起 HTTP 请求，
完整打印【请求数据】与【响应数据】。

运行方式（在 ai-customer 目录下，使用项目 .venv）：
    .venv\\Scripts\\python.exe -m pytest tests/test_customer_service_api.py -v -s

说明：本测试不注入任何桩，路由 -> 工作流 -> 智能体（agents/support_agent.py）
全链路均为真实代码，会真实调用大模型（依赖 .env 中可用的 OPENAI_API_KEY）。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

# 避免 LLM 回复中的 emoji 等非 GBK 字符在 Windows 控制台打印时报错
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

from fastapi.testclient import TestClient  # noqa: E402

from ai_customer.__main__ import app  # noqa: E402

API_PATH = "/api/v1/agent/customerService"


@pytest.fixture(scope="module")
def client() -> TestClient:
    with TestClient(app, raise_server_exceptions=False) as test_client:
        yield test_client


def _pretty_print_json(data) -> None:
    print(json.dumps(data, ensure_ascii=False, indent=2))


def test_customer_service_happy_path(client: TestClient) -> None:
    """正常请求：完整打印请求 JSON 与响应 JSON。"""
    payload = {
        "user_input": "我想查询我最近的订单状态",
        "history": ["我之前问过退款问题", "现在想换个问题问问"],
        "userType": 1,
    }

    print("\n" + "=" * 60)
    print(f"[请求] POST {API_PATH}")
    print("[请求数据] 完整 JSON：")
    _pretty_print_json(payload)

    response = client.post(API_PATH, json=payload)

    print("-" * 60)
    print(f"[响应] HTTP 状态码: {response.status_code}")
    print("[响应数据] 完整 JSON：")
    try:
        _pretty_print_json(response.json())
    except ValueError:
        print(response.text)
    print("=" * 60 + "\n")

    assert response.status_code == 200, f"API 未正常工作，状态码: {response.status_code}"


def test_customer_service_validation_error(client: TestClient) -> None:
    """缺少必填字段（userType）时，完整打印 FastAPI 的 422 校验响应。"""
    payload = {"user_input": "你好"}

    print("\n" + "=" * 60)
    print(f"[请求] POST {API_PATH}")
    print("[请求数据] 完整 JSON（缺少必填字段 userType）：")
    _pretty_print_json(payload)

    response = client.post(API_PATH, json=payload)

    print("-" * 60)
    print(f"[响应] HTTP 状态码: {response.status_code}")
    print("[响应数据] 完整 JSON：")
    try:
        _pretty_print_json(response.json())
    except ValueError:
        print(response.text)
    print("=" * 60 + "\n")

    assert response.status_code == 422
