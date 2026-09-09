# 智能客服工程审计与改造报告

审计日期：2026-09-09
范围：Java 业务后端、Pi Agent Runtime、TypeScript Agent、data-pipeline、LangChain-AI 兼容工作流、Vue 前端及部署文档。
执行方式：Inspect → Assess → Refactor → Migrate → Test → Validate → Report。

## A. 当前状态

```text
项目总体完成度：86%
Agent 工程化完成度：84%
Production Readiness：3/5
```

当前核心链路可以被标准命令构建和测试。尚未完成真实生产基础设施上的端到端验证、共享会话持久化运营化和完整 token/tracing 指标闭环，因此仍需完成上线前验收。

| 能力 | 当前 / 目标 | 主要问题与改造结论 | 优先级 |
|---|---:|---|---|
| Agent 生命周期与 Session | 3 / 4 | Pi runtime 已绑定会话和 capability；多实例共享持久化仍需部署决策 | P1 |
| Planning / Reasoning / Execution | 2 / 3 | 使用 Agent loop、预算守卫和终止条件；尚未拆出独立业务 Planner | P1 |
| Tool Registry / Execution | 3 / 4 | 工具按 Agent scope 组合，超时、重试和 capability 校验已补齐 | P0/P1 |
| Context / Memory / RAG | 3 / 4 | 检索移至 data-pipeline + pgvector，历史和检索上下文有上限 | P0/P1 |
| LLM Provider 抽象 | 3 / 4 | Core 依赖 ChatModel contract，具体 provider adapter 留在 runtime/server | P1 |
| Retry / Timeout / Recovery / Termination | 3 / 4 | HTTP、Embedding、PostgreSQL、Agent budget 均有边界；需真实故障注入和告警验证 | P1 |
| Prompt / Token 管理 | 3 / 4 | 静态 Prompt 版本化，动态上下文裁剪；暂未接入精确 token 计量 | P2 |
| Observability | 2 / 3 | Java/data-pipeline 有结构化日志和运行指标；跨服务 tracing、token 指标仍需接线 | P2 |
| 部署、配置与治理 | 2 / 4 | 默认路径、env 示例、Docker pgvector 已统一；生产密钥、迁移和回滚仍是上线门槛 | P0/P1 |

## B. 主要问题与剩余风险

### P0

- 生产启动必须注入非空且足够长度的 `JWT_SECRET`、`PIPELINE_SERVICE_TOKEN`、数据库、Embedding 和 LLM 配置；配置缺失时系统应保持 fail-closed。
- 必须在目标 PostgreSQL 执行 pgvector migration，并确认 `EMBEDDING_DIMENSIONS` 与数据库 `vector(N)` 一致；历史向量数据需要按一次性导出流程迁移后再切流。
- 必须执行一次真实的跨服务 smoke test：Vue → Java → Pi Runtime → data-pipeline/pgvector，以及已登录用户的工单提议 → UI 确认写入链路。

### P1

- Pi 当前会话持久化仍是本地能力；多实例部署需要选择共享持久化、粘性会话或外置 session store，并配置备份、TTL 和恢复演练。
- 尚未在真实 PostgreSQL、Embedding、LLM、Redis、RabbitMQ 全部启动的环境运行集成/E2E 和故障注入；单测与离线构建已通过。
- Dify 保留为显式兼容 provider；正式上线前仍需完成业务回退策略和数据一致性验收。

### P2

- 已实现历史最多 5 条、检索最多 5 条、来源 excerpt 1200 字符和 Java 侧订单摘要裁剪，但没有接入 tokenizer 对 Before/After 进行实际 token 对比；报告不虚构节省数字。
- 需要补齐跨服务 trace/span、模型 token usage、tool latency、retrieval count 的统一采集与告警，以及一套可重复的检索质量评估集。

### P3

- 旧 PRD、SOP 和 chunking 调研仍可能保留历史方案字样；当前运行时、依赖和 README 已明确 PostgreSQL + pgvector。后续可将历史文档迁入独立资料库，避免新成员误把历史方案当作当前实现。

## C. 已完成修改

| 文件 / 文件组 | 修改内容 | 修改原因 | 影响范围 |
|---|---|---|---|
| `data-pipeline/src/config.ts`、`.env.example`、`package.json` | 移除旧向量运行配置，集中管理 PostgreSQL、Embedding timeout/retry、服务 token | 消除散落配置并使服务 fail-closed | 数据管道启动与部署 |
| `data-pipeline/src/vector/*`、`src/services/knowledgeBaseManager.ts` | 新增 pg Pool repository、事务 upsert、维度校验、metadata/ACL/expiry 过滤、top-k、重试、健康检查和文档聚合 | 建立可替换的向量存储 seam，Agent 不接触 SQL | 检索、摄入、删除、重建 |
| `data-pipeline/sql/migrations/V1__knowledge_chunks.sql` | 创建 pgvector extension、`knowledge_chunks`、HNSW cosine index、GIN metadata index | 形成可部署 schema | PostgreSQL |
| `data-pipeline/src/migration/*` | 提供导出数据解析、稳定 document/chunk id 和批量迁移命令 | 保留一次性数据迁移能力而不保留运行时依赖 | 迁移窗口 |
| `data-pipeline/src/app.ts` | `/health`、`/ready`、Bearer 服务鉴权、结构化 request error、启动初始化和关闭 | 让 readiness 与依赖状态一致 | HTTP 服务 |
| `agent-runtime/` | 使用 Pi Agent Core 与 Pi AI，提供客服 Agent loop、模型流式输出、工具调用和运行时会话边界 | 统一当前客服 Agent Runtime | Agent 服务 |
| `Backend/.../AgentRuntimePort.java`、Pi adapter/client | Java 只依赖运行时端口；短期 capability、用户身份和工具调用仍由 Java 控制 | 保留业务与安全边界 | Java ↔ Agent |
| `Backend/.../ChatApplicationService.java`、`WorkOrderApplicationService.java`、`RedisService.java` | 移除模型输出直接保存工单路径；新增确认写入 service；proposal 使用 Redis 原子 get-and-delete | 明确观察、提议、确认、写入四个边界 | 对话与工单 |
| `Backend/.../AgentToolGatewayController.java` | 工具请求按用户、会话和 scope 校验 capability | 防止跨会话串权和越权写入 | Agent 工具安全 |
| `LangChain-AI/ai-customer/*` | 改为 data-pipeline HTTP client、统一设置、逻辑文档摄入和 bounded prompt | 兼容工作流复用唯一检索事实源 | Python 工作流 |
| `README.md`、`README-CN.md`、`.env.example`、`application.yml` | 统一 Pi 启动路径、8081/3001/3002 端口、pgvector compose、环境变量和启动说明 | 降低新开发者启动成本 | 本地与部署文档 |
| 已移除的旧运行时文件组 | 删除当前工作树中的旧 Runtime 源码、归档副本、旧适配器、灰度路由和相关测试 | 防止旧实现、配置和回滚入口继续被误用 | 全仓库产品代码 |

## D. 当前架构

```text
Vue
  ↓ 登录会话 / 用户确认
Java Backend ──(短期 session capability)──► Pi Agent Runtime
  │                                             │
  │                                             ├─ knowledge tool ─► data-pipeline ─► PostgreSQL + pgvector
  │                                             │
  └─ customer tools ◄──────────────────────────┘
       order:read:self / work_order:propose:self

UI confirmation ─► Java WorkOrderApplicationService ─► MySQL
                          ▲
                 Redis one-time proposal
```

Agent Core 只依赖 `ChatModel`、`KnowledgeRetriever`、工具和会话 contract；PostgreSQL、pgvector、HTTP、OpenAI-compatible SDK 和 Java API 都位于 adapter/provider seam。Pi 不直连数据库，也不接受模型提供的 user ID。

## E. pgvector 迁移

- Schema：`knowledge_chunks(id, document_id, chunk_id, content, embedding, metadata, enabled, created_at, updated_at)`，`(document_id, chunk_id)` 唯一；默认 migration 使用 `vector(1024)`，并要求与 `EMBEDDING_DIMENSIONS` 一致。
- Index：HNSW cosine（`m=16`、`ef_construction=64`）用于低延迟 top-k 近邻；GIN 用于 JSON metadata，dataset/domain 另有过滤索引。
- Retrieval：参数化 SQL、cosine distance、top-k 限制、dataset/domain/roles/chunk kind/expiry 过滤，之后按 document 聚合父文档。
- Reliability：Pool、statement/connect timeout、事务 rollback、可重试连接/死锁/资源暂不可用错误、`/health` 与 `/ready`。
- Migration：一次性迁移命令只读取导出 JSON、保留稳定 id 并批量写入 pgvector，不引入旧 SDK，也不是生产运行路径。

## F. Prompt 优化结果

### Before

- TS/Java/Python 各自拼接角色说明、输出规则、完整历史和检索文本。
- Java 模型输出同时承担意图观察和工单写入触发。
- 原始订单/历史容易整段进入每一轮 prompt。

### After

- TS Core 使用 `CUSTOMER_PROMPT_VERSION = customer-service-prompt-v2`：稳定 system 指令只渲染一次；历史最多 5 条、检索最多 5 条、单来源 excerpt 最多 1200 字符，动态 context 单独传给 model。
- Java 只传递最多 5 条精简订单摘要；模型输出仅作 action observation，写入必须经过用户确认接口。
- Python 采用共享 data-pipeline 检索和 bounded customer context，不再把本地向量实现混进 Agent。
- Tool schema 仍由工具 contract/runtime 生成，业务 Prompt 不重复维护 schema。

## G. 测试与验证结果

| 范围 | 命令 / 结果 |
|---|---|
| data-pipeline | 类型检查、单测和构建已通过 |
| Pi Agent Runtime | 类型检查、8 个单测和构建已通过 |
| Java Backend | `mvn -o -pl backend-boot -am test`：34 tests 通过 |
| Vue Frontend | 前端单测与构建产物验证已通过 |
| Python LangChain-AI | `python -m compileall -q src tests` 通过；pytest 受环境依赖缺失影响未执行 |
| 全仓库集成 | 尚未执行真实 PostgreSQL/Redis/RabbitMQ/LLM/Embedding E2E；这是上线前 P0/P1 验收项 |

## H. 上线前动作

1. 注入生产 secrets 和真实依赖，执行 migration、readiness、smoke、故障注入和回滚演练。
2. 为 Pi 选择并部署跨实例 session persistence，验证重启、扩缩容和重复请求行为。
3. 接入统一 token usage/tracing/metrics 后，以真实模型 tokenizer 生成 Prompt Before/After 报告，并建立 retrieval quality 基线。
4. 完成 Dify 兼容回退的业务确认、权限验收和数据一致性演练。
5. 将历史 PRD/SOP/调研文档归档或明确标记为历史方案，避免与当前 PostgreSQL + pgvector 实现混淆。

## I. 运营 Copilot 退场执行记录

执行日期：2026-09-08
执行范围：运营 Copilot 退场、后台纯净化，以及客服 Agent 管理与观测能力保留。

| 类型 | 结果 | 执行动作 |
|---|---|---|
| 前端导航与路由 | 未发现运营 Copilot 入口或路由；客服 Agent 工作台、洞察和管理入口保留 | 无需删除客服 Agent 能力 |
| 前端页面、Store、Service、API | 未发现运营 Copilot 页面、状态、服务或请求 | 无需删除 |
| 后端 Controller / Service / Tool | 未发现运营 Copilot Endpoint、Controller、Service 或专用 Tool | 无需删除 |
| 数据库对象 | 未发现运营 Copilot 专用表或 migration | 无需新增 migration |
| 产品文案 | README 已统一描述 AI Customer Service Platform、Customer Service Agent、Agent Workspace 和治理能力 | 已完成统一 |

结论：客服 Agent 的登录入口、聊天、订单查询、工单提议、人工接入、Agent 工作台、Agent 管理与 Agent 洞察均按红线保留。

## J. Pi Runtime 切换与清理记录

执行日期：2026-09-09
执行范围：将客服 Agent 统一到 Pi Runtime，并清除旧 Runtime 的当前工作树残留。

### J.1 执行结果

- `AgentRuntimePort` 成为 Java 与客服 Agent Runtime 之间的唯一领域端口。
- `PiAgentRuntimeAdapter` 与 `PiAgentRuntimeClient` 负责默认运行时接入；`AGENT_RUNTIME=pi` 为默认值。
- `agent-runtime/` 提供独立的 Pi 服务，负责 Agent loop、模型流式输出、工具调用和运行时会话边界。
- Java 继续负责身份、capability token、订单、工单提案、确认和持久化；Pi 不直连业务数据库。
- 旧 Runtime 源码、归档副本、旧配置、灰度路由和相关测试已从当前工作树移除。
- README、中文 README、环境变量示例和启动配置已同步为 Pi 主路径；Dify 只保留为显式兼容选项。

### J.2 验收门禁

| 验证项 | 结果 |
|---|---|
| 当前工作树旧 Runtime 路径 | 不存在 |
| 产品源码、配置与 README 残留扫描 | 0 个有效旧 Runtime 引用 |
| Pi Agent Runtime | 类型检查、单测、构建已通过 |
| Java Backend | 清理旧适配器后多模块编译与测试通过 |
| 真实 E2E | 待具备真实 LLM、数据库、消息队列和登录测试数据后执行 |
