# ai-customer

基于 Python、LangChain 和 LangGraph 的客服工作流。知识检索通过共享的 `data-pipeline` HTTP 契约完成，实际向量存储由 PostgreSQL + pgvector 统一承载。

## 目录结构

```text
ai-customer/
├─ .env.example
├─ .gitignore
├─ pyproject.toml
├─ requirements.txt
├─ README.md
├─ src/
│  └─ ai_customer/
│     ├─ __init__.py
│     ├─ __main__.py
│     ├─ cli.py
│     ├─ agents/
│     ├─ config/
│     ├─ core/
│     ├─ llms/
│     ├─ prompts/
│     ├─ schemas/
│     ├─ tools/
│     └─ workflows/
└─ tests/
```

## 功能说明

- `agents/`：封装基于 LangChain Tool Calling 的智能体。
- `workflows/`：封装状态驱动的工作流编排逻辑。
- `tools/`：封装可挂载到 Agent 的工具。
- `llms/`：统一管理模型实例化逻辑，避免业务层直接依赖厂商 SDK。
- `config/`：集中管理环境变量和运行配置。
- `core/base/vector_store.py`：data-pipeline 的窄 HTTP 适配器，不在本项目内创建本地向量库。
- `tests/`：关键工具与工作流的单元测试。

## 快速开始

1. 创建虚拟环境

```powershell
cd d:\CodeFile\ai_customer_service\LangChain-AI\ai-customer
py -3.13 -m venv .venv
.venv\Scripts\Activate.ps1
```

2. 安装依赖

```powershell
python -m pip install --upgrade pip
pip install -e .[dev]
```

3. 配置环境变量

```powershell
Copy-Item .env.example .env
```

至少设置 `OPENAI_API_KEY`、`PIPELINE_SERVICE_TOKEN`；`DATA_PIPELINE_URL` 默认指向 `http://localhost:3002`。

知识库同步命令从 MySQL 读取已批准的 `knowledge_documents` 行，并交给 data-pipeline 完成切块、Embedding 和 pgvector 入库：

```powershell
python -m ai_customer.scripts.chunks
```

4. 运行工作流

```powershell
ai-customer workflow "请帮我查询今天北京时间并总结待办。"
```

5. 直接运行智能体

```powershell
ai-customer agent "现在是几点？"
```

## 开发命令

```powershell
pytest
ruff check .
```
