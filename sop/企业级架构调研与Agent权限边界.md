# 企业级架构调研与 Agent 权限边界

## 1. 结论

脱离 Dify、使用 DeepSeek Harness（DSH）建设智能客服 Agent **技术上可行，但不能直接把 DSH 当成完整客服业务平台替换 Dify**。推荐定位如下：

- DSH 负责 Agent Loop、事件化 Session、工具注册与执行、模型适配、上下文压缩、超时/重复调用守卫和运行时扩展。
- Java Backend 继续作为用户、订单、工单、SLA、知识版本、权限和审计的唯一业务事实源。
- Agent Tool Gateway 负责把“模型提出的动作”转换为受身份、资源、额度、确认和幂等约束的业务调用。
- C 端只开放受限客服能力；B 端只开放配置、审核、观测和人工处置能力，不开放任意代码/文件/终端工具。
- Dify 在灰度期保留，按会话路由到 Dify 或 DSH，指标异常时快速切回。

当前 DSH `0.1.1-rc.2` 已具备替代 Dify 编排内核所需的大部分基础设施，但项目内 `examples/customer-service` 仍是 first-pass：业务工具曾把 `userId` 暴露给模型，知识检索未携带 Pipeline 服务令牌，且组合中缺少生产级硬上限。因此可行性判断为“**有条件可行，需完成 Tool Gateway 与身份上下文插件后灰度**”。

## 2. 项目现状与企业级优化点

### 2.1 Backend

| 优先级 | 现状/风险 | 企业级改造 |
|---|---|---|
| P0 | WebSocket 和部分工单/统计接口曾信任请求中的用户、客服或角色 ID | 身份统一来自 Spring Security/握手上下文；增加会话、工单对象级授权 |
| P0 | JWT 曾使用进程启动时随机密钥；服务重启令牌全部失效 | 使用环境注入且可轮换的签名密钥；后续支持 `kid` 与双密钥轮换 |
| P0 | 细粒度权限拦截器未注册且读取 JWT 角色 | 从当前数据库身份解析角色；缓存未命中时数据库回源；对工单管理启用权限码 |
| P0 | 异常响应泄漏内部异常类型和消息 | 对外使用稳定错误码/通用消息，详细堆栈只进入服务端日志 |
| P1 | Dify/TS Agent 客户端缺少统一超时、重试、熔断与隔离 | Resilience4j 策略按读写请求拆分；写请求不做无幂等重试 |
| P1 | SQL 初始化脚本同时承担结构变更与数据初始化 | 引入 Flyway/Liquibase，迁移不可变、可审计，生产禁用自动初始化 |
| P1 | MQ/异步摘要缺少完整 Outbox、DLQ、幂等状态机 | 业务事务写 Outbox；消费者按事件 ID 幂等；失败进入 DLQ 并支持重放 |
| P1 | Session 依赖内存/Redis 组合且恢复语义不完整 | Session 事件持久化，Redis 只作热状态；定义重启恢复和过期策略 |
| P1 | 日志中仍有临时 DEBUG 标记和可能的业务标识 | 结构化日志、字段脱敏、traceId/sessionId；删除临时调试区块 |
| P2 | Controller 承担编排、权限、推送和异步分析 | 抽取 Use Case/Application Service；Controller 只做协议适配 |

### 2.2 Frontend

| 优先级 | 现状/风险 | 企业级改造 |
|---|---|---|
| P0 | 会话 ID 可被替换后读取历史；客服 WS 身份来自客户端负载 | 会话绑定签名令牌；客服 WS 使用已认证服务端身份 |
| P0 | 开发访客管理员开关存在进入生产构建的风险 | 使用编译期 `DEV` 门禁，生产构建恒关闭 |
| P0 | `<think>` 思维链可能展示给 C 端 | 丢弃思维链，仅展示最终回答、引用和允许公开的工具状态 |
| P1 | 多个入口 Chunk 超过 1 MB | 路由级懒加载、拆分编辑器/图表/Markdown 依赖、设置包体预算 |
| P1 | JavaScript 为主，接口 DTO 易漂移 | 渐进迁移 TypeScript，从 OpenAPI 生成 Client 与类型 |
| P1 | 缺少稳定的单测、组件测试和 E2E | Vitest + Vue Test Utils + Playwright，覆盖登录、聊天、转人工、工单与越权 |
| P1 | 轮询与 WebSocket 恢复逻辑分散 | 统一实时事件层，使用游标增量同步、指数退避和断线恢复 |
| P2 | Store 同时管理传输、领域状态和 UI 状态 | 拆分 API Client、领域 Store、视图状态；统一错误码映射 |

### 2.3 Data Pipeline / Knowledge

| 优先级 | 现状/风险 | 企业级改造 |
|---|---|---|
| P0 | 管理与检索接口原先无服务认证 | 健康检查公开，其余接口使用服务令牌；后续升级 mTLS/短期服务凭证 |
| P0 | 向量检索缺少租户、角色、知识域 ACL | ACL 必须在检索前过滤，不允许仅在生成答案后过滤 |
| P1 | ChromaDB 是运行事实源，删除/重建边界弱 | MySQL 保存审核版本与发布状态，向量库仅是可重建投影 |
| P1 | PDR 父块回查、文档/Embedding 版本不完整 | 保存 parent/child/version/hash，索引使用版本集合与别名切换 |
| P1 | 仅有功能测试，无检索质量门禁 | 建立 Recall@K、MRR、nDCG、引用正确率和拒答正确率评测集 |

## 3. DSH 替代 Dify 的能力映射

| 能力 | DSH 当前能力 | 项目需补充 | 判断 |
|---|---|---|---|
| Agent Loop | `agent-loop` 执行模型→工具→结果→下一步，并记录事件 | 最大 Step、最大工具次数、总耗时/Token/成本硬预算 | 可复用，必须加硬守卫 |
| Session/短期记忆 | append-only Session Event、持久化和恢复接口 | 客服身份映射、业务会话生命周期、删除/导出策略 | 可复用事件层 |
| 上下文管理 | token meter、compaction 插件 | 客服摘要模板、事实不可压缩区、敏感字段脱敏 | 可复用框架 |
| 工具调用 | scoped tool registry、pre/execute/post/result 扩展点 | Capability、资源 ACL、确认、幂等、审计、业务错误语义 | 可复用执行管线 |
| 超时 | 工具声明 `timeoutMs` + timeout-policy；协作式取消 | 所有网络工具传递 AbortSignal；进程外硬超时 | 可复用但不能只依赖软取消 |
| 重复调用 | repeat-tool-reminder 只提供建议 | 达到阈值后硬阻断和转人工 | 需自研生产守卫 |
| 权限预设 | sandbox/approval 两类开发者工具权限预设 | C/B 端 RBAC、ABAC、对象级 ACL | **不可直接复用为业务权限** |
| 自动化接入 | ACP 可创建/恢复/取消 Session 并处理一次性批准 | HTTP/消息网关、用户 Capability 绑定、流式协议 | 可作为内部协议适配层 |
| 知识库 | 通过工具插件外接 | 发布审核、版本、ACL、混合检索和评测 | 由本项目负责 |
| 运维治理 | Session telemetry、OTel 插件能力 | 业务 SLO、成本、投诉、转人工与越权指标 | 可扩展 |

依据：DSH 官方仓库的 [Core 子系统](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/subsystems/core.md)、[Agent Loop](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/core/agent-loop/README.md)、[权限预设](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/subsystems/permission-presets.md) 和 [ACP](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/acp/acp/README.md)。

## 4. 推荐运行架构

```text
C 端 / B 端
    │ Access Token + Chat Session Token
    ▼
Java Backend / Agent BFF
    ├─ 身份、RBAC/ABAC、对象 ACL
    ├─ 会话路由：Dify | DSH（按租户/用户/会话灰度）
    ├─ Capability Token（短期、定用户、定会话、定工具）
    └─ Tool Gateway（确认、幂等、审计、限额）
             │
             ▼
       DSH Agent Runtime
       ├─ Loop / Session Event / Compaction
       ├─ 只加载客服白名单工具
       └─ 不拥有订单、工单、用户或知识发布事实
             │
             ├─ 只读工具 ──► Java 领域服务
             ├─ 写入提案 ──► 确认流 ──► Java 领域服务
             └─ 检索工具 ──► Data Pipeline ──► ChromaDB
```

关键不变量：模型参数中不出现可改变授权主体的 `userId`、`agentId`、`tenantId`、`roles`；这些字段由 Gateway 根据 Capability 注入。

## 5. C 端与 B 端权限、边界和职责

### 5.1 功能矩阵

| 能力 | C 端用户 | B 端客服 | B 端知识管理员 | B 端系统管理员 |
|---|---|---|---|---|
| Loop 可见性 | 仅最终答复、引用、允许公开的动作状态 | 本人会话的工具摘要与失败原因 | 评测/检索诊断，不看无授权用户隐私 | 全局指标和审计，不默认看明文会话 |
| Loop 控制 | 取消本人请求、转人工 | 暂停/接管本人已分配会话 | 不控制在线会话 | 灰度、熔断、模型/预算策略；无任意代码执行 |
| 短期记忆 | 本人会话；可清空 | 本人已分配会话的必要上下文 | 无 | 策略配置和脱敏审计 |
| 长期记忆 | 明示同意后查看/修改/删除本人偏好 | 原则上只读必要字段，不可自行写入敏感偏好 | 无 | 只管理策略，不默认读取内容 |
| 业务事实 | 查询本人订单/工单 | 查询本人被分配资源 | 无 | 按职责审批和审计 |
| 知识检索 | 仅已发布 C 端知识域 | 已发布客服知识域，可含内部话术 | 草稿、审核、发布、下线和重建 | 配置策略，不替代知识审核 |
| 只读工具 | 本人资源，低额度 | 分配资源，中等额度 | 知识诊断工具 | 审计/运维工具 |
| 可逆写工具 | 必须确认，如补充工单信息 | 分配资源内执行并审计 | 草稿编辑 | 配置变更需二次确认 |
| 高风险写工具 | 禁止自动执行；转人工 | 退款、补偿、隐私导出走审批 | 批量发布/下线走双人复核 | 权限变更、批量删除走双人复核 |
| 工具配置 | 不可见 | 不可修改 | 仅知识相关阈值 | 白名单、预算、灰度；不能突破代码级 denylist |

### 5.2 Loop 硬限制

- C 端：建议最多 6 Steps、8 次工具调用、单工具 5 秒、整轮 30 秒；超过后确定性停止并转人工。
- B 端客服辅助：建议最多 10 Steps、15 次工具调用、整轮 60 秒；写工具仍逐项授权。
- 同名同参调用连续 3 次警告、5 次硬阻断；总 Token、成本和并发均按租户与用户限额。
- Stop 原因必须结构化：`completed`、`budget_exceeded`、`tool_denied`、`timeout`、`cancelled`、`handoff_required`。

### 5.3 记忆边界

- 工作记忆：当前会话事件和摘要，按会话 TTL 自动过期。
- 长期记忆：仅保存用户明确授权的稳定偏好；来源、用途、有效期、版本和删除记录必须可追踪。
- 业务事实：订单、工单、账户状态永远实时查询 Java Backend，不写入长期自然语言记忆。
- 禁止记忆：密码、验证码、完整支付信息、身份凭证、模型思维链；手机号等敏感字段只做短时脱敏使用。
- 客服不能通过 Agent 将个人判断写成用户长期事实；需要用户确认或受控业务事件。

### 5.4 知识库边界

- C 端只能检索 `published=true`、渠道包含 C 端、租户/产品/地区匹配且在生效期内的文档。
- 客服可检索内部话术，但模型不得把 `internal_only` 内容原文返回 C 端。
- 知识管理员负责草稿、审核、发布、下线和回滚；系统管理员只管理权限和基础设施。
- 检索结果必须携带 documentId/version/chunkId/标题，答案保存引用；无证据时拒答或转人工。

### 5.5 工具分级

| 等级 | 示例 | 策略 |
|---|---|---|
| T0 纯计算 | 分类、格式转换 | 自动执行，仍计入预算 |
| T1 只读 | 本人订单、已发布知识 | Capability + 对象 ACL + 限流 + 审计 |
| T2 可逆写 | 创建工单草稿、补充描述 | 明示确认 + 幂等键 + 前后状态审计 |
| T3 高风险写 | 退款、补偿、转派、隐私导出 | Agent 只能提案；人工审批/双人复核后由领域服务执行 |
| T4 禁止 | 改角色、任意 SQL/HTTP/文件/终端、关闭审计 | C/B 客服 Agent 永不装载，提示词不能解除 |

## 6. 实现可行性与阶段门禁

1. 影子阶段只启用 `search_knowledge`，比较 Dify/DSH 检索与答案，不向用户展示 DSH 结果。
2. 只读灰度启用“本人订单查询”；Tool Gateway 根据短期 Capability 注入主体，工具 schema 无 `userId`。
3. 写入灰度先做“创建工单提案”，由用户确认后 Java 服务执行；Agent 不持有可直接写入的长期服务管理员令牌。
4. 达到质量、安全和成本阈值后扩大灰度；任一越权样例、引用错误率或 P95 延迟越界自动切回 Dify。
5. DSH 上游当前仍为 RC 版本，生产应固定 commit/镜像、维护内部补丁队列、做许可证/SBOM/漏洞扫描，不跟随 `master` 自动升级。

## 7. 当前执行状态

- Phase 1 已完成身份与资源授权基线，提交 `c2c813bc`。
- Phase 2 本地实现已完成：Java Tool Gateway、稳定会话灰度路由、DSH 身份参数移除、Pipeline 认证、硬预算与定向测试均已落地；DSH 独立提交为 `b864a7756b`。
- DSH 当前环境 Capability 只允许单客户隔离 Worker 试点；共享多会话进程必须先把工具按 Agent Scope 挂载并注入各会话 Capability。
- GitHub 推送因当前网络连接重置暂未成功；本地提交和分支完整保留。
