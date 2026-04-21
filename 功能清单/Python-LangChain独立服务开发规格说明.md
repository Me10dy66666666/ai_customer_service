# Python LangChain 独立服务开发规格说明（Spec）

版本：v1.0  
日期：2026-04-07  
适用仓库：`ai_customer_service`

## 1. 背景与目标

当前系统的知识库与对话能力主要通过 Dify 对接。为提升可控性、可扩展性与后续模型/向量库可替换能力，新增一个与 `Backend` 同级的 Python 独立服务（`rag-service`），由该服务负责文档向量化与检索增强问答（RAG）。

本规格目标：
- 在不改前端接口路径的前提下，替换后端内部 AI 知识库能力来源。
- 形成可独立部署、可灰度切换、可持续演进的 RAG 服务。
- 支持文档入库、向量检索、带引用回答三个核心能力。

## 2. 范围定义

本期范围（In Scope）：
- 文档上传、解析、切分、向量化、写入向量库。
- 文档列表、删除、启停（状态）管理。
- 检索接口（返回召回片段与分数）。
- RAG 回答接口（返回答案与引用片段）。
- 后端到 Python 服务的内部 HTTP 对接。

非本期范围（Out of Scope）：
- 前端直接调用 Python 服务。
- 完整替换现有业务日志、工单规则等 Java 侧逻辑。
- 多租户隔离与权限中心重构。

## 3. 架构与边界

组件职责：
- `Frontend`：保持现有调用方式，不直接接触向量库。
- `Backend`（Java）：鉴权、业务编排、会话日志、工单逻辑、对 Python 服务聚合。
- `rag-service`（Python）：文档处理、Embedding、向量检索、RAG 生成。
- 向量数据库：默认 `Qdrant`（可替换 `pgvector`）。
- MySQL：继续存业务主数据（`knowledge_base` 等）。

调用关系：
1. 前端请求 `Backend /api/kb/*` 或 `Backend /api/chat/*`。
2. Backend 在服务层调用 `rag-service` 内部接口。
3. `rag-service` 与向量库交互，返回标准化结果给 Backend。
4. Backend 保留当前日志与业务流程落库。

## 4. 目录与工程约束

建议新增目录：

```text
ai_customer_service/
  Backend/
  Frontend/
  rag-service/
    app/
      api/
      core/
      services/
      models/
    tests/
    requirements.txt
    .env.example
    README.md
```

约束：
- `rag-service` 为独立 Python 项目，不纳入 Maven 模块。
- 由 Backend 通过 HTTP 调用，不做进程内耦合。
- 服务配置采用环境变量，不把密钥写入代码仓库。

## 5. 向量库选型（本期默认）

默认选型：`LangChain + Qdrant`

原因：
- 结构简单，部署和调试成本低。
- 检索性能和过滤能力适合当前知识库规模。
- LangChain 对接成熟，后续替换模型或 reranker 成本可控。

备选：
- `PostgreSQL + pgvector`：若团队希望复用现有数据库体系，可优先该方案。

## 6. 接口规格（rag-service 对 Backend 内部）

统一约定：
- 协议：HTTP/JSON（上传接口为 multipart）。
- 鉴权：Header `X-Internal-Token: <token>`。
- 基础路径：`/internal/rag`
- 响应结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "uuid"
}
```

### 6.1 健康检查

- `GET /internal/rag/health`
- 用途：Backend 启动探活与监控。

### 6.2 文档上传入库

- `POST /internal/rag/documents/upload`
- `multipart/form-data`
- 参数：
- `file`：必填，支持 `pdf/docx/txt/md`
- `title`：选填，默认文件名
- `category`：选填，默认 `general`
- `metadata`：选填，JSON 字符串
- `force`：选填，默认 `false`

返回字段：
- `ragDocumentId`：Python 服务侧文档 ID
- `chunkCount`：切分块数量
- `status`：`indexed`/`failed`

### 6.3 文档列表

- `GET /internal/rag/documents`
- 查询参数：`page`、`size`、`category`、`status`

### 6.4 删除文档

- `DELETE /internal/rag/documents/{ragDocumentId}`
- 行为：向量库逻辑删除或物理删除（本期默认物理删除）。

### 6.5 检索

- `POST /internal/rag/retrieval/search`
- 请求：

```json
{
  "query": "设备报错E12怎么处理",
  "topK": 5,
  "category": "after_sales",
  "filters": {
    "status": "enabled"
  }
}
```

返回：

```json
{
  "chunks": [
    {
      "chunkId": "c_001",
      "ragDocumentId": "d_123",
      "score": 0.87,
      "content": "..."
    }
  ]
}
```

### 6.6 RAG 回答

- `POST /internal/rag/chat/answer`
- 请求：

```json
{
  "sessionId": "s_001",
  "userId": 1001,
  "userType": 1,
  "query": "这个故障是否在保修范围内",
  "topK": 5,
  "history": [
    { "role": "user", "content": "..." },
    { "role": "assistant", "content": "..." }
  ]
}
```

返回：

```json
{
  "answer": "根据手册，E12属于传感器异常...",
  "citations": [
    {
      "ragDocumentId": "d_123",
      "chunkId": "c_001",
      "score": 0.87
    }
  ],
  "hitCount": 3
}
```

## 7. 数据模型规格

### 7.1 向量库 Collection

Collection：`acs_kb_chunks`

每个向量点 payload 字段：
- `rag_document_id`
- `chunk_id`
- `title`
- `category`
- `file_type`
- `status`
- `source`
- `content`
- `created_at`

### 7.2 MySQL 扩展建议（Backend）

现有 `knowledge_base` 保留，建议新增字段：
- `rag_document_id` VARCHAR(64) NULL
- `vector_store` VARCHAR(32) NULL
- `embedding_model` VARCHAR(64) NULL
- `index_status` TINYINT NOT NULL DEFAULT 0
- `index_error` TEXT NULL

状态建议：
- `0=待入库`、`1=已入库`、`2=失败`、`3=已删除`

## 8. 关键流程

### 8.1 上传入库流程

1. 前端上传文件到 Backend `/api/kb/upload`。
2. Backend 完成基础校验与本地文件落盘。
3. Backend 调用 `rag-service /documents/upload`。
4. `rag-service` 执行解析、切分、向量化、写入向量库。
5. 返回 `ragDocumentId/chunkCount/status` 给 Backend。
6. Backend 更新 `knowledge_base` 对应字段并返回前端。

### 8.2 问答流程

1. 前端请求 Backend `/api/chat/send`。
2. Backend 组装上下文（用户类型、历史订单、会话历史）。
3. Backend 调用 `rag-service /chat/answer`。
4. `rag-service` 检索 TopK 片段并生成答案+引用。
5. Backend 按现有逻辑落库 `consultation_logs` 并回传前端。

## 9. 非功能要求（NFR）

- 性能：
- 文档入库：单个 10MB 文档在 30s 内完成（不含超大文件）。
- 检索接口：P95 < 800ms。
- 回答接口：P95 < 5s（非流式）。

- 稳定性：
- 任何下游失败不导致 Backend 进程崩溃，返回可追踪错误码。
- 所有请求写入结构化日志并带 `requestId`。

- 安全：
- 仅内网可访问 `rag-service`。
- 使用内部 Token 校验，禁止匿名调用。
- 文件类型白名单与大小限制（默认 10MB）。

## 10. 配置项（rag-service）

必需环境变量：
- `RAG_SERVICE_PORT`
- `INTERNAL_API_TOKEN`
- `VECTOR_STORE_PROVIDER`（`qdrant` / `pgvector`）
- `QDRANT_URL`
- `QDRANT_API_KEY`（可空）
- `EMBEDDING_PROVIDER`
- `EMBEDDING_MODEL`
- `EMBEDDING_API_KEY`
- `LLM_PROVIDER`
- `LLM_MODEL`
- `LLM_API_KEY`
- `CHUNK_SIZE`
- `CHUNK_OVERLAP`
- `DEFAULT_TOP_K`
- `SCORE_THRESHOLD`

## 11. 实施里程碑

M1（1-2天）：
- 初始化 `rag-service` 项目骨架。
- 完成 `/health` 与统一错误处理。

M2（2-3天）：
- 完成上传、删除、列表接口。
- 打通文档入库（切分+向量化+写入向量库）。

M3（2-3天）：
- 完成检索与 RAG 回答接口。
- 增加引用返回、阈值兜底策略。

M4（1-2天）：
- Backend 接口切换与灰度开关。
- 联调、压测、验收。

## 12. 验收标准（DoD）

- 上传任意支持格式文档后，可在 30s 内检索到对应内容。
- 删除文档后，检索结果中不再出现该文档片段。
- 回答接口必须返回引用信息（至少 1 条，命中时）。
- `Backend /api/kb/*` 与 `/api/chat/*` 对前端保持兼容。
- 异常场景（向量库不可用、模型超时）能返回明确错误码与日志。

## 13. 待确认决策

- 向量库最终是否采用 `Qdrant`（默认）还是 `pgvector`。
- Embedding 与 LLM 的供应商与模型清单。
- 本期是否要求流式输出（SSE）直出 `rag-service`。
- 是否保留 Dify 作为兜底通道（灰度期间建议保留）。

## 14. 结论

本方案采用“同仓库、独立 Python 服务、Backend 内部对接”的模式，能在不推翻现有系统的前提下，快速引入 LangChain 向量检索能力，并支持后续从知识库检索逐步演进到完整 RAG 中台能力。
