# 智能客服系统升级 PRD：企业级能力补齐 + DeepSeek Harness 接入

> 文档状态：历史方案（2026-08-24）。当前实现以 [README.md](README.md) 和 [ENGINEERING_AUDIT.md](ENGINEERING_AUDIT.md) 为准：生产默认链路为 DSH + data-pipeline + PostgreSQL/pgvector，本文中的 ChromaDB、旧 TypeScript adapter 和 Dify 默认描述不代表当前运行时。

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0 |
| 文档状态 | 待评审 |
| 编写日期 | 2026-08-24 |
| 适用仓库 | `d:\CodeFile\ai_customer_service` |
| 关联模块 | `Backend/`、`Frontend/`、`ts-enterprise-webagent/`、`deepseek-harness/` |

---

## 1. 背景

当前系统是一套基于 **Java 21 + Spring Boot 4.0.0 + Vue 3** 的全栈智能客服平台，业务域完整（知识库、工单、SLA、坐席管理、IM 会话、数据分析），AI 能力主要依赖外挂的 **Dify** 工作流引擎，另有一套自研的 TypeScript Agent（`ts-enterprise-webagent`）作为可选替代。

随着业务演进，出现三个核心诉求：

1. **补齐企业级工程底座**：当前在可观测性、可靠性、安全合规、AI 治理（LLMOps）等方面与真正的企业级产品存在明显差距。
2. **引入 DeepSeek Harness（dsh）**：以插件化方式接管客服的会话式 Agent 编排，摆脱对 Dify 的强绑定，获得工具调用、多步推理、子 Agent 等能力。
3. **解耦数据与 Agent**：将「知识文件切块 → 向量化 → 入库」独立为可复用服务，将 UI 替换为自研前端。

---

## 2. 目标与非目标

### 2.1 目标

- **G1**：独立出「知识数据处理」服务，具备完整的「文件解析 → 切块 → 向量化 → 向量入库/检索」闭环，且不依赖 Dify。
- **G2**：以 DeepSeek Harness 作为会话式 Agent 运行时，通过 Cordis 插件接入客服领域工具（知识检索、订单、工单、转人工、SLA）。
- **G3**：将客服 UI 替换为自研前端，DSH 以 `headless`（无 UI）模式运行，通过 BFF 桥接。
- **G4**：暂时隔离自研 Agent 与 Dify 侧服务，通过灰度开关可控切换，保留降级能力。
- **G5**：补齐可观测性、可靠性、安全合规、AI 治理等企业级底座。

### 2.2 非目标（本期不做）

- 不改写 Java 后端既有业务逻辑（订单/工单/SLA 领域模型保持系统记录地位）。
- 不替换 DSH 内部 React UI 的 slot 体系（仅在必要时做自研 UI 独立替换）。
- 不做多租户 SaaS 化改造（仅预留扩展点）。
- 不引入 Kubernetes 编排与微服务拆分（维持单体 + 独立 Agent/数据处理服务）。

---

## 3. 术语

| 术语 | 说明 |
|---|---|
| DSH / DeepSeek Harness | `@deepseek-ai/dsh`，DeepSeek 开源的插件化 Agent 框架，基于 vendored Cordis |
| Cordis | DSH 底层插件框架，插件通过 `ctx.effect()/ctx.on()` 贡献服务、事件、可逆效果 |
| Capability Seam | DSH 能力缝：Service Definition / Service Provider / Consumer 三角色 |
| BFF | Backend For Frontend，自研 UI 与 DSH 之间的薄桥接层 |
| RAG | 检索增强生成 |
| PDR | Parent Document Retrieval（父文档检索分块策略） |
| LLMOps | 大模型相关观测、评测、成本、治理的工程实践 |
| ACP | Agent Client Protocol，DSH 提供的自动化服务端协议 |
| Dify | 第三方 LLM 编排平台，当前系统的默认 AI 引擎 |

---

## 4. 现状分析

### 4.1 技术栈

| 层 | 技术栈 | 关键文件 |
|---|---|---|
| 后端 | Java 21 + Spring Boot 4.0.0，DDD 六模块 | [application.yml](file:///d:/CodeFile/ai_customer_service/Backend/backend-boot/src/main/resources/application.yml) |
| 数据 | MySQL(MyBatis) / Redis(缓存+会话+Stream) / RabbitMQ / Elasticsearch / LibreOffice / 阿里云 OCR / ShedLock | [docker-compose.yml](file:///d:/CodeFile/ai_customer_service/Backend/docker-compose.yml) |
| AI 引擎 | Dify（默认）+ ts-agent（自研 Fastify，端口 3001） | [DifyClient.java](file:///d:/CodeFile/ai_customer_service/Backend/backend-infrastructure/src/main/java/com/example/backend/infrastructure/dify/DifyClient.java) |
| 前端 | Vue 3 + Vite + Pinia + Router 5 + Element Plus + ECharts + TipTap | [package.json](file:///d:/CodeFile/ai_customer_service/Frontend/package.json) |
| 自研 Agent | Fastify + ChromaDB + zod，三种模型模式 | [config.ts](file:///d:/CodeFile/ai_customer_service/ts-enterprise-webagent/apps/server/src/config.ts) |
| DSH | Node 22.19+/24 + pnpm 11.7，Cordis 插件框架 | [architecture.md](file:///d:/CodeFile/ai_customer_service/deepseek-harness/docs/architecture.md) |

### 4.2 现有 AI 链路（三条并行）

| 链路 | 路径 | 特点 |
|---|---|---|
| A（Dify） | 文件 → Dify 上传 → Dify 内部切块+向量化 → `DifySyncService` 回拉元数据 → ES 索引 | 向量化完全外包给 Dify，系统内只有元数据 |
| B（自管 ES） | 文件 → 分片上传 → OCR → `EsDocumentIndexService` → ES | 仅 BM25/ngram 全文检索，**无向量** |
| C（自研 Agent） | 文件 → `DocumentChunkingService`（递归分块+PDR）→ OpenAI 兼容 Embedding → ChromaDB | **已具备切块+向量化，且零 Dify 依赖** |

关键结论：**「切块 → 向量化 → 入库」的核心逻辑已经存在（链路 C），且与 Dify 解耦**，只是当前嵌在 `ts-enterprise-webagent` 里，未独立。

### 4.3 三套 AI 资产关系

- **Dify**：承担知识上传/检索、对话生成、工单分析/干预、摘要 4 项职责。
- **ts-enterprise-webagent**：承担 RAG 检索 + 意图路由 + Agent 编排，含可嵌入网站的 Widget。
- **deepseek-harness**：成熟的插件化 Agent 框架，具备 session、tools、agent-loop、shell、fs、workflow、subagent 等完整能力。

---

## 5. 企业级差距分析（优化项来源）

| 编号 | 差距域 | 现状 | 目标态 |
|---|---|---|---|
| D1 | 可观测性 | 无 Actuator/Prometheus/Micrometer，无链路追踪，无 LLM 观测 | 指标 + 链路 + 日志 + LLM 专项观测 |
| D2 | 可靠性 | 无熔断/降级/限流，MQ 无 DLQ/幂等 | 熔断降级 + DLQ + 幂等 + 限流 |
| D3 | 安全合规 | 仅 JWT，密钥明文 env，无内容审核，无数据留存闭环 | OAuth2/SSO + 密钥管理 + 内容审核 + 留存审计 |
| D4 | 架构工程化 | 无网关、无配置中心、无 DB 迁移版本管理、测试覆盖极低、无 CI/CD | 网关 + 配置中心 + Flyway + CI/CD + 测试体系 |
| D5 | RAG 成熟度 | ES 单路 BM25，无向量/混合检索/重排/Embedding 版本管理 | 混合检索 + 重排 + 引用溯源 + 评测集 |
| D6 | 多租户/扩展 | 单租户，Agent 会话持久化于 SQLite/内存，无法多实例共享 | 共享会话存储 + 租户扩展点 |

---

## 6. 总体方案

### 6.1 目标架构

```text
┌─────────────────────────────────────────────────────────────┐
│                     自研 Vue 前端（客服 UI）                    │
│   复用 ChatView.vue / WebAgentWidget + 客服域展示              │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP / SSE / WS
                    ┌───────▼────────┐
                    │   BFF（自研薄层） │  鉴权、会话映射、流式转发、契约校验
                    └───────┬────────┘
                            │ JSON-RPC / ACP
                    ┌───────▼────────────────────────────┐
                    │   DeepSeek Harness（headless）      │
                    │   ctx.tools 插件：                  │
                    │   search_knowledge / lookup_order  │
                    │   create_work_order / transfer     │
                    │   check_sla_policy / summarize     │
                    └───┬───────────────┬────────────────┘
                        │ 向量检索        │ 业务 REST
              ┌─────────▼────────┐  ┌────▼─────────────────────┐
              │ 数据处理服务       │  │ Java 后端（系统记录）       │
              │ 解析→切块→向量化   │  │ 订单/工单/SLA/用户/知识     │
              │ 向量库（Milvus等） │  │ MySQL/Redis/RabbitMQ/ES   │
              └──────────────────┘  └──────────────────────────┘
```

### 6.2 职责划分

| 组件 | 职责 | 变更程度 |
|---|---|---|
| Java 后端 | 业务系统记录（订单/工单/SLA/用户/鉴权），对外 REST | 少量新增对接 |
| 数据处理服务 | 文件解析 + OCR + 切块 + 向量化 + 向量库读写 | 从 ts-enterprise-webagent 抽出 |
| DSH（headless） | 会话式 Agent 大脑：意图、工具调用、多步推理、子 Agent | 新增插件 |
| BFF | Vue ↔ DSH 桥接，鉴权/会话映射/流式转发 | 新增 |
| 自研 Vue UI | 客服前端界面 | 复用现有 |
| Dify / ts-enterprise-webagent | 隔离，保留作灰度降级 | 冻结 |

---

## 7. 功能需求（FR）

### 7.1 FR1 — 数据处理独立服务（知识切块向量化入库）

**目标**：将「文件 → 文本 → 切块 → 向量化 → 向量库」做成独立、可复用、Dify 无关的服务。

**FR1.1 从 `ts-enterprise-webagent` 抽出已有实现**

- 复用 [documentChunkingService.ts](file:///d:/CodeFile/ai_customer_service/ts-enterprise-webagent/apps/server/src/services/documentChunkingService.ts)（递归分块 + PDR + 中文分隔符）。
- 复用 [chromadbKnowledgeBase.ts](file:///d:/CodeFile/ai_customer_service/ts-enterprise-webagent/apps/server/src/adapters/chromadbKnowledgeBase.ts)（OpenAI 兼容 Embedding + 向量读写）。
- 复用 [chromadbKnowledgeBaseManager.ts](file:///d:/CodeFile/ai_customer_service/ts-enterprise-webagent/apps/server/src/services/chromadbKnowledgeBaseManager.ts)（知识库管理门面）。

**FR1.2 文件解析能力**

| 需求 | 说明 | 优先级 |
|---|---|---|
| 文本类解析 | Markdown/TXT 直接读取 | P0 |
| Office 解析 | PDF/Word/Excel，复用后端 LibreOffice + POI/PDFBox | P0 |
| OCR | 扫描件/图片，复用阿里云 OCR | P1 |

**FR1.3 向量化与存储**

- Embedding 走 OpenAI 兼容 `/embeddings` 接口，通过配置切换模型（默认推荐通义 `text-embedding-v3` 或 BGE）。
- 向量库默认 ChromaDB（MVP），支持切换 Milvus / Qdrant / pgvector。
- 需提供「重建索引（reindex）」「删除文档」「启停用」能力。

**验收标准**

- 上传 PDF/Word/Markdown 后，能在向量库中检索到语义相近内容。
- 全程不调用 Dify 任何接口。
- 分块策略可配置（chunk_size / overlap / PDR 父子层级）。

---

### 7.2 FR2 — DeepSeek Harness 接入（Agent 运行时）

**目标**：DSH 以 headless 模式作为客服 Agent 运行时，通过 Cordis 插件提供客服工具。

**FR2.1 运行时与模型**

- 运行环境：Node `^22.19 || >=24` + pnpm。
- 模型：`DEEPSEEK_API_KEY`（支持 `DEEPSEEK_BASE_URL` 指向兼容端点）。

**FR2.2 客服工具插件（注册到 `ctx.tools`）**

| 工具 | 桥接目标 | 优先级 |
|---|---|---|
| `search_knowledge` | 数据处理服务向量库 / 后端 ES | P0 |
| `lookup_order` | 后端订单接口 | P0 |
| `create_work_order` | 后端工单服务（触发人在回路） | P0 |
| `transfer_to_human` | 后端会话分派服务 | P0 |
| `check_sla_policy` | 后端 SLA 服务 | P1 |
| `analyze_sentiment` / `summarize_conversation` | DeepSeek 模型或既有 workflow | P1 |

- 每个工具遵循 DSH Capability Seam 三件套（Service Definition / Provider / Consumer），Provider 通过 HTTP 回调后端。
- 工具契约复用 `@enterprise-webagent/shared` 的 zod schema。

**FR2.3 会话持久化**

- DSH 默认 SQLite，需切换到共享存储（Redis/Postgres），支持多实例与断线重连。

**验收标准**

- 用户提问后，DSH 能自主选择并调用 `search_knowledge`，生成带知识来源的回答。
- 触发工单/转人工意图时，能正确调用对应后端接口。

---

### 7.3 FR3 — 自研 UI 替换 + BFF

**目标**：客服 UI 使用自研前端，DSH 无 UI 运行，中间由 BFF 桥接。

**FR3.1 自研 UI**

- 复用 [ChatView.vue](file:///d:/CodeFile/ai_customer_service/Frontend/src/domains/chat/ChatView.vue) 与 [web-agent-widget.ts](file:///d:/CodeFile/ai_customer_service/ts-enterprise-webagent/apps/widget/src/web-agent-widget.ts)。
- 支持流式回复、Markdown 渲染、工单操作按钮、来源引用展示。

**FR3.2 BFF 层**

- 将 Vue 前端请求翻译为 DSH 的 JSON-RPC / ACP 调用。
- 负责鉴权、DSH 会话 ↔ 系统用户会话映射、流式转发、契约校验。

**验收标准**

- 前端仅与 BFF 通信，不感知 DSH 协议细节。
- 消息流式回显，断开可重连续传。

---

### 7.4 FR4 — 隔离与灰度切换

**目标**：暂时隔离自研 Agent 与 Dify，通过开关可控切换，保留降级。

- 复用后端已有 `agent.provider` 开关，扩展为 `dify | ts-agent | dsh`。
- Dify 侧接口（知识上传/检索、对话、工单分析、摘要）在切到 dsh 后旁路，但保留配置可回退。
- 提供灰度策略（按用户/租户/百分比）。

**验收标准**

- 一键可切回 Dify，功能不丢失。
- 切换过程中会话不中断（有会话迁移或冷启动策略）。

---

### 7.5 FR5 — 企业级底座补齐（对应差距 D1-D6）

| 需求 | 说明 | 优先级 |
|---|---|---|
| 指标监控 | Prometheus + Grafana，后端暴露 Actuator/Micrometer | P0 |
| 链路追踪 | OpenTelemetry + Jaeger/Tempo | P1 |
| LLM 观测 | Langfuse（token/成本/延迟/trace/评测） | P0 |
| 熔断限流 | Resilience4j（后端对 DSH/Dify 熔断）+ Sentinel | P1 |
| 内容安全 | 敏感词/违规内容审核（数美/易盾/Azure Content Safety） | P0 |
| API 网关 | Kong/APISIX/Spring Cloud Gateway（鉴权/限流/路由） | P1 |
| 密钥管理 | Vault，替换明文 env | P1 |
| 配置中心 | Nacos/Apollo，统一管理后端 + DSH `cordis.yml` | P1 |
| DB 迁移 | Flyway，替代手工 `init.sql` | P1 |
| MQ 可靠性 | RabbitMQ 补 DLQ + 幂等键 | P1 |

---

## 8. 非功能需求（NFR）

| 编号 | 类别 | 要求 |
|---|---|---|
| NFR1 | 性能 | FAQ 类问答 P95 延迟 < 1.5s；工具调用类 < 3s |
| NFR2 | 可用性 | 核心链路可用性 ≥ 99.9%；DSH 实例故障可降级 Dify |
| NFR3 | 安全 | 密钥不落盘明文；日志脱敏（沿用 `MaskUtils`）；接口鉴权全覆盖 |
| NFR4 | 合规 | 内容审核接入；用户数据留存/导出/删除闭环 |
| NFR5 | 可扩展 | 数据处理服务与 DSH 均可水平扩展；会话共享存储 |
| NFR6 | 可观测 | 一次请求可端到端追踪（前端 → BFF → DSH → 后端 → 模型） |

---

## 9. 依赖与中间件清单

| 类别 | 依赖 | 状态 |
|---|---|---|
| 模型 | `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` | 新增 |
| Embedding | 通义 `text-embedding-v3` / BGE / 其他 OpenAI 兼容 | 新增（DeepSeek 无 embedding 端点） |
| 向量库 | ChromaDB(MVP) / Milvus / Qdrant / pgvector | 新增/升级 |
| 运行时 | Node 22.19+/24 + pnpm | 新增 |
| 会话存储 | Redis / Postgres（替代 DSH 默认 SQLite） | 新增 |
| 观测 | Prometheus + Grafana + OpenTelemetry + Langfuse | 新增 |
| 治理 | API 网关、Resilience4j/Sentinel、Vault、Nacos、Flyway | 新增 |
| 文档处理 | LibreOffice + POI/PDFBox + 阿里云 OCR | 已有，复用 |
| 内容安全 | 数美/易盾/Azure Content Safety | 新增 |

---

## 10. 里程碑与路线图

| 阶段 | 内容 | 交付物 |
|---|---|---|
| Phase 1 | 抽出数据处理服务，接文件解析/OCR，定 Embedding + 向量库 | 独立 data-pipeline 服务 |
| Phase 2 | 起 DSH headless，打通 `search_knowledge` 直连向量库 | 最小 RAG 通路 |
| Phase 3 | 自研 BFF + 复用 Vue UI，跑通流式问答 | UI 替换完成 |
| Phase 4 | `agent.provider` 切到 dsh，灰度验证，Dify/自研 Agent 降级 | 灰度上线 |
| Phase 5 | 补齐企业级底座（观测/熔断/内容审核/网关/密钥） | 生产就绪 |

---

## 11. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| DeepSeek 无 embedding 端点 | 向量化缺模型 | 引入独立 embedding 模型并配置化切换 |
| DSH 处于开发者预览、API 破坏性变更 | 升级成本 | 固定版本、封装 SDK 适配层、订阅变更 |
| 替换 UI 若误入 DSH React slot 深坑 | 工期失控 | 坚持 headless + SDK/ACP + 自研 UI 路线 |
| 文档解析/OCR 未接入导致知识入库不完整 | 检索召回下降 | Phase 1 优先补齐解析与 OCR |
| 会话持久化切换（SQLite→共享存储） | 断线丢会话 | Phase 2 前完成存储切换 |
| 切流后功能回退 | 业务受损 | 灰度开关 + Dify 降级保留 |

---

## 12. 成功标准

1. 知识文件（PDF/Word/Markdown/扫描件）可全链路向量化入库并被语义检索，全程零 Dify 依赖。
2. DSH 作为客服 Agent 运行时，能自主调用知识检索/订单/工单/转人工/SLA 工具。
3. 客服 UI 完全由自研前端提供，DSH 以 headless 运行，流式交互体验达标。
4. 具备可观测性、熔断限流、内容审核、密钥管理等企业级底座，满足上线合规要求。
5. 通过 `agent.provider` 可一键在 `dify / ts-agent / dsh` 间切换并灰度发布。

---

## 13. 待确认事项

1. Embedding 模型最终选型（通义 vs BGE vs 本地部署）。
2. 向量库最终选型（ChromaDB 先行 vs 直接上 Milvus/Qdrant）。
3. 内容审核供应商选型。
4. 是否需要在 Phase 4 前完成多实例部署与共享会话存储的验证。
5. DSH 侧会话与 Java 后端用户会话的 ID 映射与鉴权方案细节。
