"""临时脚本：检查相关文件在磁盘上的大小。"""
from pathlib import Path

paths = [
    "src/ai_customer/api/routes/customer_service.py",
    "src/ai_customer/api/routes/__init__.py",
    "src/ai_customer/workflows/support_workflow.py",
    "src/ai_customer/__main__.py",
    "src/ai_customer/agents/support_agent.py",
    "src/ai_customer/tools/builtin.py",
    "src/ai_customer/schemas/state.py",
    "src/ai_customer/api/__init__.py",
]

for p in paths:
    f = Path(p)
    if f.exists():
        print(f"{p}: {f.stat().st_size} bytes")
    else:
        print(f"{p}: MISSING")
