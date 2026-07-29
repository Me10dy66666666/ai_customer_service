# ai-customer

基于 Python、LangChain 和 LangGraph 的 AI 工作流与智能体工程脚手架，适合作为客服、问答、流程编排类项目的起点。

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
