# 智能客服企业级改造执行 SOP

> 文档状态：执行基线与治理约束。已完成项及当前剩余门槛以仓库根目录 `ENGINEERING_AUDIT.md` 为准。

## 1. 目标

在保持 Java 后端为订单、工单、SLA、用户和知识版本唯一业务事实源的前提下，完成安全基线、DeepSeek Harness Agent 接入、记忆与知识治理、可靠性与可观测性、前端工程化和灰度发布能力建设。

## 2. 执行原则

1. 每个阶段使用独立提交，完成验收后推送 GitHub。
2. 每个阶段必须先通过相关自动化测试，再把状态从 `IN_PROGRESS` 更新为 `DONE`。
3. 不把模型输出当作授权依据。模型只提出动作，策略层授权，Java 领域服务执行并落库。
4. 不信任客户端或模型传入的 `userId`、`agentId`、角色、租户或权限；身份必须来自已验证的服务端上下文。
5. 高风险写操作必须具备确认、幂等、审计和人工升级路径。
6. DeepSeek Harness（DSH）是唯一目标客服 Agent 运行时；Dify 仅在灰度期间保留为降级通道，不做一次性全量切换。
7. `ts-enterprise-webagent/` 是历史 TS 脚手架，不再作为 Agent 实现、降级通道或代码来源；后续阶段不读取、不更新、不从中复用 Agent 代码。
8. Backend 的 Agent 选择只允许 `dify | dsh | gray`，不得恢复 `ts-agent` provider；知识和业务写入始终经由 Java 领域端口。

## 3. GitHub 更新规范

- 工作分支：`codex/enterprise-hardening`
- 阶段提交格式：`phase-N: <阶段结果>`
- 每次推送前执行：`git diff --check`、对应模块测试、检查提交文件清单。
- 每次推送后在本文件记录提交 SHA、测试结果和遗留项。
- `deepseek-harness/` 是独立 Git 仓库，未经确认不得把整个上游仓库嵌入根仓库提交；DSH 插件变更在其独立仓库中提交，或后续抽取到根仓库自有模块。
- 本 SOP 是当前执行基准；历史 PRD/README 中任何“自研 TS Agent”或 `ts-agent` 切换描述均不再指导实施。

## 4. 阶段计划与验收标准

### Phase 0：基线与执行治理

状态：`DONE_LOCAL（GITHUB_PUSH_PENDING）`

范围：

- 建立本 SOP、风险清单、阶段验收和回滚规则。
- 确认根仓库与 DeepSeek Harness 独立仓库边界。
- 建立可复现的 Backend、Frontend、Data Pipeline 基线测试记录。

验收：

- Backend `mvn test` 通过。
- Frontend `npm run build` 通过。
- Data Pipeline `npm test` 与 `npm run typecheck` 通过。
- SOP 已提交并推送 GitHub。

### Phase 1：P0 安全基线

状态：`DONE_LOCAL（GITHUB_PUSH_PENDING）`

范围：

- 修复 WebSocket 客服身份伪造，区分匿名用户通道与已认证客服动作。
- 禁止从请求体信任用户、客服和角色身份。
- 为会话历史、工单、手机号、订单等资源增加对象级鉴权。
- 统一 Spring Security 权限入口，启用细粒度权限并删除失效的重复鉴权路径。
- 收紧 CORS，返回真实 401/403/4xx/5xx 状态。
- 禁止生产环境访客管理员开关和 C 端思维链展示。
- 为 Data Pipeline 增加服务认证，保护删除、重建和启停接口。

验收：

- 匿名连接不能注册为客服、认领或转派会话。
- 用户不能读取其他用户的会话、订单和工单。
- 客服只能操作本人被分配或有权限管理的资源。
- 前端可正确处理 HTTP 401/403。
- 新增安全单元/集成测试全部通过。

### Phase 2：Agent Tool Gateway 与 DeepSeek Harness 灰度接入

状态：`DONE_LOCAL（GITHUB/DSH_REMOTE_PUSH_PENDING）`

范围：

- 建立 Agent/BFF 接口和短期 Capability Token。
- 工具参数移除模型可控的身份字段，拆分只读、可逆写和高风险写工具。
- 增加工具白名单、超时、最大 Step、最大工具次数、Token/成本和重复调用限制。
- 为写工具增加确认、幂等键、审计和人工升级。
- 修复客服 DSH 配置端口，补齐插件测试与 CI。
- 先接入知识检索影子流量，再灰度本人订单查询和本人工单创建。

验收：

- DSH 客服插件类型检查和自动化测试通过。
- Agent 无法通过参数切换用户身份或扩大权限。
- Loop 超限会确定性终止并给出人工接管路径。
- Dify/DSH 可按会话灰度切换并可快速回滚。

### Phase 3：记忆与知识库治理

状态：`DONE_REMOTE（DEPLOYMENT_MIGRATION_PENDING）`

范围：

- 将工作记忆、用户长期记忆、业务事实和知识文档分层存储。
- 提供记忆授权、查看、删除、过期和脱敏策略。
- Java/MySQL 维护知识审核版本，向量库保持为可重建投影。
- 修复 PDR 父块存储与回查，落实分块策略配置。
- 增加文档/分块/Embedding 版本、ACL 过滤、混合检索、重排和引用。
- 建立检索与答案离线评测集。

验收：

- 不同用户、角色和知识域之间无检索串权。
- 文档下线后不能被 Agent 检索。
- 可按版本重建索引并回滚。
- 检索和答案质量指标达到约定阈值。

### Phase 4：DSH 客服 Agent 可靠性、可观测性与数据一致性

状态：`DONE_REMOTE（DSH_PERSONAL_REMOTE_PENDING / DEPLOYMENT_DRILL_PENDING）`

范围：

- 清理 Backend 中历史 TS 脚手架 Agent 的 Client、Adapter、provider 配置和包命名，建立独立 DSH Gateway/BFF 边界。
- 本阶段只增强 DSH 客服 Agent 及 Java 业务边界，不规划、更新或测试历史 TS Agent。
- 引入数据库迁移管理、乐观锁和唯一约束。
- DSH BFF、Dify 降级通道和向量服务统一超时、重试、熔断、隔离舱和限流；无幂等保障的写请求禁止自动重试。
- MQ 增加幂等、DLQ、重放和 Outbox 状态机。
- 增加 Actuator、Micrometer、OpenTelemetry 和结构化日志。
- 增加 LLM Token、成本、首 Token 延迟、工具成功率、转人工率和越权拦截指标。
- 将生产 Session 持久化迁移到支持多实例的存储。

当前实施批次：

- [x] 删除 Backend `TsAgentClient`/`TsAgentAdapter` 和 `ts-agent` 配置，将灰度路由迁入独立 `dsh` 边界。
- [x] 拆分 Agent 对话与知识管理端口，DSH 只负责客服 Agent 编排，Java 保持知识业务事实边界。
- [x] DSH Gateway 客户端增加连接/读取超时，并接入 Actuator、Prometheus 和 OpenTelemetry 基础设施。
- [x] DSH Gateway 已落地并发舱、速率闸门和连续失败熔断，超时、阻断和请求结果写入统一指标。
- [x] 建立统一外部调用重试边界：Dify 幂等读/删请求按退避策略重试；DSH 客服消息、Dify 工作流/上传和其他写请求明确禁止自动重试。
- [x] 将同一重试策略补齐到向量服务适配器，并完成跨提供者契约测试。
- [x] 完成乐观锁、Outbox 幂等/DLQ/失败重放和一致性测试；迁移 SQL 基线已提供。
- [x] 接入 Flyway 运行时、启动校验和 V4 迁移脚本；目标 MySQL（本地或部署环境）仍需执行一次真实基线校验。
- [x] 打通 DSH usage/tool/session、首 Token、工具结果和转人工事件到 Backend 指标；DSH BFF 的 blocking/SSE 会话契约、Java 流式协议归一化和 trace 字段已完成本地验证。
- [ ] 将 DSH `persistenceRoot` 指向共享持久化并完成双实例重启恢复演练；本地测试只覆盖 `conversation_id` resume 合同，不能替代部署环境演练。

验收：

- 外部 AI/向量服务故障时可降级，不造成线程池和请求堆积。
- 消息重复投递不会产生重复业务写入。
- 核心链路可按 traceId/sessionId/workOrderId 追踪。
- 多实例重启后会话可以恢复。

### Phase 5：前端工程化、测试体系与发布

状态：`DONE_LOCAL（CI_E2E_TOOLCHAIN_PENDING）`

范围：

- 前端逐步迁移 TypeScript，生成 OpenAPI Client。
- 增加 ESLint、单元测试、组件测试和 Playwright E2E。
- 拆分超大组件和大于 500 KB 的 Chunk，统一错误和加载状态。
- 用事件推送替代固定 5 秒轮询，保留断线增量同步。
- 建立 Backend Testcontainers、契约测试、安全回归和 Agent Eval CI。
- 建立 5% → 20% → 50% → 100% 灰度门禁与自动回滚。

本地已完成：工单 WebSocket 事件适配器和重连后增量同步、TypeScript/OpenAPI 风格工单客户端契约、Node 单元测试入口、模块化 ECharts/Element Plus 和 vendor 分包；生产 chunk 已全部低于 500 KB。Frontend 当前未安装 ESLint/Vitest/Playwright 依赖，因此完整组件测试、Playwright E2E、Testcontainers、Agent Eval CI 和灰度自动回滚保留为 CI/部署环境工作项，不伪造本地通过结果。

验收：

- 主链路 E2E、越权回归和 Agent Eval 在 CI 中稳定通过。
- 前端无超过约定阈值的入口 Chunk。
- 灰度期间 SLO、正确率、投诉率和单位会话成本不劣于基线。

## 5. 当前基线记录

| 项目 | 结果 | 备注 |
|---|---|---|
| Backend | PASS | `mvn -o -q -pl backend-boot -am test`：34 tests；包含 DSH/Dify 契约、向量重试、Outbox 重放和乐观锁测试 |
| Frontend | PASS | `npm run test:unit`（2/2）、`npm run build`、TypeScript 契约 `tsc --noEmit`；生产 JS chunks 全部低于 500 KB |
| Data Pipeline | PASS | 18 tests；服务认证测试与 TypeScript typecheck 通过 |
| DSH 客服插件 | PASS | customer-service focused gate：4 files / 8 tests；Gateway typecheck 和真实 keyless WebServer composition 通过 |

## 6. 阶段执行记录

| 阶段 | 状态 | 提交 SHA | 测试 | 遗留项 |
|---|---|---|---|---|
| Phase 0 | DONE_LOCAL / PUSH_PENDING | `73d6fa93` | Backend 15/15；Frontend build；Pipeline 17/17 + typecheck | GitHub HTTPS 连接被重置；DSH 插件待 Phase 2 验证 |
| Phase 1 | DONE_LOCAL / PUSH_PENDING | `c2c813bc` | Backend 23/23；Frontend build；Pipeline 18/18 + typecheck | GitHub HTTPS 连接被重置；WebSocket Ticket 与大 Chunk 分别在 Phase 2/5 处理 |
| Phase 2 | DONE_LOCAL / PUSH_PENDING | 根仓库见 `phase-2`；DSH `b864a7756b` | Backend 27/27；DSH customer-service 4/4 + 三包 typecheck/build | GitHub 网络重置；共享多会话 DSH Worker 上线前必须改为 per-agent scoped capability；当前仅允许单客户隔离 Worker 灰度 |
| Phase 3 | DONE_REMOTE / DEPLOYMENT_MIGRATION_PENDING | `4b463b49` | Data Pipeline 26/26 + typecheck；TS core/server 11/11 + typecheck/build；Python compileall | Java 记忆迁移需在部署环境执行；DSH 共享 Worker 仍需 per-agent capability |
| Phase 4 | DONE_REMOTE / DSH_PERSONAL_REMOTE_PENDING / DEPLOYMENT_DRILL_PENDING | `84406775` | Backend interfaces 20/20 + backend-boot package；DSH focused 4 files / 10 tests + Gateway typecheck（本地独立仓库） | DSH 个人远端尚未配置；在目标 MySQL 执行 Flyway 基线、共享 Session 多实例恢复和 OTLP 端到端演练待部署环境 |
| Phase 5 | DONE_LOCAL / CI_E2E_TOOLCHAIN_PENDING | 待提交 | Frontend unit 2/2、build、TypeScript contract check；JS chunks <500 KB | ESLint/Vitest/Playwright/Testcontainers/Agent Eval 依赖与灰度回滚门禁待 CI/部署环境 |

## 7. 回滚规则

- 代码回滚以阶段提交为最小单位，不混合跨阶段改动。
- Agent 灰度异常优先将会话路由切回 Dify，不删除 DSH 会话和审计记录。
- 知识索引使用版本化集合/别名切换，不在原集合上执行不可逆全量覆盖。
- 数据库迁移必须提供前向修复方案；破坏性迁移必须先完成备份演练。
