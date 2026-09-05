# 智能客服工程审计与改造报告

审计日期：2026-09-05
范围：Java 业务后端、DeepSeek Harness Agent、TypeScript Agent、data-pipeline、LangChain-AI 兼容工作流、Vue 前端及部署文档。
执行方式：Inspect → Assess → Refactor → Migrate → Test → Validate → Report。

## A. 完成度

```text
项目总体完成度：86%
Agent 工程化完成度：84%
Production Readiness：3/5
```

这是按核心链路、边界清晰度、故障行为、测试证据和上线前置条件进行的风险加权判断，不按文件数量或代码量推算。当前核心链路已经可以被标准命令构建和测试；尚未完成真实生产基础设施上的端到端验证、共享会话持久化运营化和完整 token/tracing 指标闭环，因此不评为 4/5 或以上。

| 能力 | 当前 / 目标 | 主要问题与改造结论 | 优先级 |
|---|---:|---|---|
| Agent 生命周期与 Session | 3 / 4 | DSH gateway 已绑定会话和 capability；多实例共享持久化仍需部署决策 | P1 |
| Planning / Reasoning / Execution | 2 / 3 | 使用 Harness agent loop、预算守卫和终止条件；尚未拆出独立业务 Planner | P1 |
| Tool Registry / Execution | 3 / 4 | 工具按 Agent scope 组合，超时、重试和 capability 校验已补齐 | P0/P1 |
| Context / Memory / RAG | 3 / 4 | 检索移至 data-pipeline + pgvector，历史和检索上下文有上限 | P0/P1 |
| LLM Provider 抽象 | 3 / 4 | Core 依赖 ChatModel contract，具体 OpenAI-compatible adapter 留在 server | P1 |
| Retry / Timeout / Recovery / Termination | 3 / 4 | HTTP、Embedding、PostgreSQL、Agent budget 均有边界；需真实故障注入和告警验证 | P1 |
| Prompt / Token 管理 | 3 / 4 | 静态 Prompt 版本化，动态上下文裁剪；暂未接入精确 token 计量 | P2 |
| Observability | 2 / 3 | Java/data-pipeline 有结构化日志和运行指标；跨服务 tracing、token 指标仍需接线 | P2 |
| 部署、配置与治理 | 2 / 4 | 默认路径、env 示例、Docker pgvector 已统一；生产密钥、迁移和灰度回滚仍是上线门槛 | P0/P1 |

## B. 主要问题与剩余风险

### P0

- 生产启动必须注入非空且足够长度的 `JWT_SECRET`、`PIPELINE_SERVICE_TOKEN`、`DSH_GATEWAY_SERVICE_TOKEN`、数据库、Embedding 和 LLM 配置；配置缺失时系统应保持 fail-closed。当前代码已取消默认 JWT、pipeline 和 DSH gateway 密钥，但本次未连接真实生产环境验证。
- 必须在目标 PostgreSQL 执行 pgvector migration，并确认 `EMBEDDING_DIMENSIONS` 与数据库 `vector(N)` 一致；旧 Chroma 数据需要按一次性导出脚本迁移后再切流。
- 必须执行一次真实的跨服务 smoke test：Vue → Java → DSH → data-pipeline/pgvector，以及已登录用户的工单提议 → UI 确认写入链路。

### P1

- DSH 当前示例会话持久化仍是示例级 JSONL/本地能力；多实例部署需要选择共享持久化、粘性会话或外置 session store，并配置备份、TTL 和恢复演练。
- 尚未在真实 PostgreSQL、Embedding、LLM、Redis、RabbitMQ 全部启动的环境运行集成/E2E 和故障注入；单测与离线构建已通过。
- Dify 保留为显式 fallback/gray provider，生产默认已切到 DSH；正式切流前仍需业务确认回滚策略和数据一致性验收。

### P2

- 已实现历史最多 5 条、检索最多 5 条、来源 excerpt 1200 字符和 Java 侧订单摘要裁剪，但没有接入 tokenizer 对 Before/After 进行实际 token 对比；报告不虚构节省数字。
- 需要补齐跨服务 trace/span、模型 token usage、tool latency、retrieval count 的统一采集与告警，以及一套可重复的检索质量评估集。

### P3

- 旧 PRD、SOP 和 chunking 调研仍保留部分 Chroma 字样，因为它们是历史方案或对旧方案的审计记录；当前运行时、依赖和 README 已明确 PostgreSQL + pgvector。后续可将历史文档迁入 archive，避免新成员误把历史方案当作当前实现。

## C. 已完成修改

| 文件 / 文件组 | 修改内容 | 修改原因 | 影响范围 |
|---|---|---|---|
| `data-pipeline/src/config.ts`、`.env.example`、`package.json` | 移除生产 Chroma 配置和依赖，集中管理 PostgreSQL、Embedding timeout/retry、服务 token | 消除散落配置并使服务 fail-closed | 数据管道启动与部署 |
| `data-pipeline/src/vector/pgVectorStore.ts`、`vectorStore.ts`、`src/services/knowledgeBaseManager.ts` | 新增 pg Pool repository、事务 upsert、维度校验、metadata/ACL/expiry 过滤、top-k、重试、健康检查和文档聚合 | 建立可替换的向量存储 seam，Agent 不接触 SQL | 检索、摄入、删除、重建 |
| `data-pipeline/sql/migrations/V1__knowledge_chunks.sql` | 创建 pgvector extension、`knowledge_chunks`、HNSW cosine index、GIN metadata index | 形成可部署 schema | PostgreSQL |
| `data-pipeline/src/migration/*`、`src/scripts/migrate-chroma-export.ts` | 提供 Chroma JSON 导出解析、稳定 document/chunk id 和批量迁移命令 | 保留一次性迁移能力而不保留运行时依赖 | 迁移窗口 |
| `data-pipeline/src/app.ts` | `/health`、`/ready`、Bearer 服务鉴权、结构化 request error、启动初始化和关闭 | 让 readiness 与依赖状态一致 | HTTP 服务 |
| `ts-enterprise-webagent/packages/core/src/domain/prompt.ts`、Agent contract | 静态 system Prompt v2 与动态 context 分离，版本化并限制 history/source | 消除每轮重复注入和原始上下文膨胀 | Agent Core |
| `ts-enterprise-webagent/apps/server/src/adapters/dataPipelineKnowledgeBase.ts`、`app.ts` | 以 HTTP adapter 连接 data-pipeline；移除 in-memory/Chroma fallback；增加 timeout/retry/error boundary | Core 依赖接口而不是具体数据库 | TS Agent server |
| `deepseek-harness/packages/customer-service/customer-service-gateway/*` | capability 按会话注入 customer tools；缺少 capability 时只允许 knowledge-only；service token 必填；并发新会话使用独立 key；turn/error 输出结构化 JSON 日志 | 消除全局静态业务工具和跨会话串权，同时保留 request/session/agent/model/latency/token/tool/retrieval 事实 | DSH gateway |
| `deepseek-harness/packages/customer-service/tool-customer-service/*` | 工具请求携带 session header；业务工具只接受 gateway 注入的 capability | 将用户、会话和 scope 绑定到后端边界 | DSH → Java |
| `Backend/.../DshGatewayClient.java`、`AgentToolGatewayController.java` | Java 为登录用户按 conversation 签发短期 capability；后端校验 scope、活跃用户、session header；工单改为提议后由 UI 确认 | 禁止模型输出直接产生持久化副作用 | Agent 工具安全与工单 |
| `Backend/.../ChatApplicationService.java`、`WorkOrderApplicationService.java`、`RedisService.java` | 移除模型输出保存工单路径；新增确认写入 service；proposal 使用 Redis 原子 get-and-delete | 明确观察、提议、确认、写入四个边界 | 对话与工单 |
| `LangChain-AI/ai-customer/src/ai_customer/*`、`tests/*` | 改为 data-pipeline HTTP client、统一设置、惰性表反射、逻辑文档摄入和 bounded prompt；移除 Chroma/sentence-transformers | 兼容工作流复用唯一检索事实源 | Python 工作流 |
| `README.md`、`.env.example`、`Backend/docker-compose.yml`、`application.yml` | 统一 DSH 默认路径、8081/3001/3002 端口、pgvector compose、环境变量和启动/迁移说明 | 降低新开发者启动成本 | 本地与部署文档 |

## D. 架构变化

改造前的主要问题是 TS Agent 直接拥有 Chroma/in-memory 具体实现，DSH customer tools 是全局静态安装，Java 会把模型输出解析后直接保存工单，Prompt 将动态历史和检索结果混入重复 system 文本。

当前职责关系：

```text
Vue
  ↓ 登录会话 / 用户确认
Java Backend ──(短期 session capability)──► DSH Gateway
  │                                            │
  │                                            ├─ knowledge tool ─► data-pipeline ─► PostgreSQL + pgvector
  │                                            │
  └─ customer tools ◄── DSH Agent Core ◄──────┘
       order:read:self / work_order:propose:self

UI confirmation ─► Java WorkOrderApplicationService ─► MySQL
                          ▲
                 Redis one-time proposal
```

Agent Core 只依赖 `ChatModel`、`KnowledgeRetriever`、工具/会话 contract；PostgreSQL、pgvector、HTTP、OpenAI-compatible SDK 和 Java API 都位于 adapter/provider seam。这样测试可以替换依赖，生产实现可以独立演进。

## E. pgvector 迁移

- Schema：`knowledge_chunks(id, document_id, chunk_id, content, embedding, metadata, enabled, created_at, updated_at)`，`(document_id, chunk_id)` 唯一；默认 migration 使用 `vector(1024)`，并要求与 `EMBEDDING_DIMENSIONS` 一致。
- Index：HNSW cosine（`m=16`、`ef_construction=64`）用于低延迟 top-k 近邻；GIN 用于 JSON metadata，dataset/domain 另有过滤索引。选择 HNSW 是因为当前服务优先查询延迟和增量写入，不假设需要离线重建的超大批量 IVFFlat；规模变大后应以实际 recall/latency 基准复核参数。
- Retrieval：参数化 SQL、cosine distance、top-k 限制、dataset/domain/roles/chunk kind/expiry 过滤，之后按 document 聚合父文档。
- Reliability：Pool、statement/connect timeout、事务 rollback、可重试连接/死锁/资源暂不可用错误、`/health` 与 `/ready`。
- Migration：`npm run migrate:chroma -- <chroma-export.json> [datasetId]` 只负责一次性读取导出 JSON、保留稳定 id 并批量写入 pgvector；它不引入 Chroma SDK，也不是生产运行路径。
- 清理：生产 `package.json`/lock、TS server adapter、Python 依赖和运行配置已移除 Chroma；剩余字样仅限一次性迁移工具和历史文档。

## F. Prompt 优化结果

### Before

- TS/Java/Python 各自拼接角色说明、输出规则、完整历史和检索文本。
- Java 模型输出同时承担意图观察和工单写入触发。
- 原始订单/历史容易整段进入每一轮 prompt。

### After

- TS Core 使用 `CUSTOMER_PROMPT_VERSION = customer-service-prompt-v2`：稳定 system 指令只渲染一次；历史最多 5 条、检索最多 5 条、单来源 excerpt 最多 1200 字符，动态 context 单独传给 model。
- Java 只传递最多 5 条精简订单摘要；模型输出仅作 action observation，写入必须经过用户确认接口。
- Python 采用共享 data-pipeline 检索和 bounded customer context，不再把本地向量实现混进 Agent。
- Tool schema 仍由工具 contract/运行时生成，业务 Prompt 不重复维护 schema。

### Token 优化原因

优化来源是去重静态指令、限制历史、限制 retrieval top-k、裁剪来源 excerpt、压缩订单字段和不把 metadata/凭证放入 Prompt。当前未安装统一 tokenizer，也没有采集生产 token usage，因此无法安全给出 Before/After 的精确 token 数或百分比。

## G. 测试与验证结果

| 范围 | 命令 / 结果 |
|---|---|
| data-pipeline | `npm run typecheck` 通过；`npm test -- --run`：8 files / 26 tests 通过；`npm run build` 通过 |
| TypeScript Agent | shared/core/server/widget typecheck 通过；core/server：11 tests 通过；`npm run build` 通过 |
| DeepSeek Harness | `npm run typecheck` 通过；`npm run build:lib:host` 通过；customer-service focused Vitest：4 files / 10 tests 通过；customer-service oxlint 通过 |
| DSH 文档门禁 | Agent Note verifier：598 条通过；translation pairing：1304 ok、0 out-of-sync、4 个未配对 README（历史/说明文档） |
| Java Backend | `mvn -o -pl backend-interfaces -am test`：20 tests 通过；离线 compile/test 成功 |
| Vue Frontend | 已有前端单测：2 tests 通过；构建产物验证过，最后保留并恢复了基线 `Frontend/dist` 状态 |
| Python LangChain-AI | `python -m compileall -q src tests` 通过；pytest 未执行，因为环境未安装项目依赖（pytest、langchain、SQLAlchemy 等） |
| 全仓库集成 | 未执行真实 PostgreSQL/Redis/RabbitMQ/LLM/Embedding E2E；这是上线前 P0/P1 验收项 |

已知工具环境限制：DSH 默认 aggregate Vitest 的 repository invariant 在当前 Node 24 下触发 `FiberState.PENDING`/`ACTIVE` 未处理状态，focused customer-service config 通过；DSH 的 `tsx` 脚本曾因 Node 24 `uv_os_get_passwd returned ENOMEM` 失败，直接 Node `--experimental-strip-types` 的同等 lint/note 校验通过。这些是工具运行环境问题，不是本次业务测试失败。

## H. 当前技术债务与上线前动作

以下是本次安全范围内无法替代真实环境或业务决策完成的事项：

1. 注入生产 secrets 和真实依赖，执行 migration、readiness、smoke、故障注入和回滚演练。
2. 为 DSH 选择并部署跨实例 session persistence，验证重启、扩缩容和重复请求行为。
3. 接入统一 token usage/tracing/metrics 后，以真实模型 tokenizer 生成 Prompt Before/After 报告，并建立 retrieval quality 基线。
4. 确认 DSH 默认 provider 的正式切流和 Dify fallback/gray 回滚策略。
5. 将历史 PRD/SOP/调研文档归档或明确标记为历史方案，避免与当前 PostgreSQL + pgvector 实现混淆。
