# TS Enterprise WebAgent

企业级 TypeScript Agent 开发脚手架，用于把 `LangChain-AI/ai-customer` 中的 `customerAgent` 迁移为可嵌入网站的 WebAgent 应用。

## 设计目标

- 可维护：使用 workspace + 分层模块，避免前后端和 Agent 逻辑耦合。
- 可持续开发：共享契约、可替换 adapter、可测试的核心编排。
- 防御性编程：环境校验、入参校验、超时控制、错误分层、输出净化。
- 可嵌入：通过自定义元素 `<enterprise-web-agent>` 直接挂载到任意站点。

## 目录结构

```text
ts-enterprise-webagent/
├─ apps/
│  ├─ server/          # Fastify API，封装 customerAgent 的 TS 服务端实现
│  └─ widget/          # 可嵌入网站的 Web Component widget
├─ packages/
│  ├─ shared/          # zod schema、共享类型、前后端通信契约
│  └─ core/            # customerAgent 核心编排、提示词构建、策略与 adapter seam
└─ examples/
   └─ embed-demo/      # 最小嵌入示例
```

## 迁移映射

Python `ai-customer` 的主要链路：

1. `vector_search_tool.py` -> `KnowledgeRetriever` adapter
2. `customer_agent.py` -> `CustomerAgentModule`
3. `customerSverviceWorkflow.py` -> `CustomerAgentModule.reply()` 内部编排
4. `api/routes/customer_service.py` -> `POST /api/v1/customer-agent/messages`

## 快速开始

```bash
npm install
npm run test
npm run build
```

开发运行：

```bash
npm run dev:server
npm run dev:widget
```

## 环境变量

服务端默认支持两种模型模式：

- `mock`：本地开发默认值，无需外部模型密钥。
- `openai-compatible`：连接 OpenAI 兼容接口。

示例见 `apps/server/.env.example`。

## 嵌入方式

构建 widget 后，在宿主页引入脚本并插入自定义元素：

```html
<script type="module" src="/enterprise-web-agent.js"></script>
<enterprise-web-agent
  api-base-url="http://localhost:3001"
  launcher-label="智能客服"
  user-type="1"
  history-orders='["2025 年购买过扫地机器人"]'
></enterprise-web-agent>
```
