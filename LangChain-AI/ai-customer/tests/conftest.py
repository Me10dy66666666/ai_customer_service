"""pytest 共享配置：注册自定义 CLI 选项和标记。"""

from __future__ import annotations

import pytest


def pytest_addoption(parser):
    parser.addoption(
        "--run-db",
        action="store_true",
        default=False,
        help="运行需要真实 MySQL 数据库的测试",
    )
    parser.addoption(
        "--run-e2e",
        action="store_true",
        default=False,
        help="运行需要完整端到端模拟的测试",
    )


def pytest_collection_modifyitems(config, items):
    """根据 CLI 选项跳过标记的用例。"""
    run_db = config.getoption("--run-db")
    run_e2e = config.getoption("--run-e2e")

    skip_db = pytest.mark.skip(reason="使用 --run-db 选项来运行")
    skip_e2e = pytest.mark.skip(reason="使用 --run-e2e 选项来运行")

    for item in items:
        if "skip_db" in item.keywords and not run_db:
            item.add_marker(skip_db)
        if "run_e2e" in item.keywords and not run_e2e:
            item.add_marker(skip_e2e)
