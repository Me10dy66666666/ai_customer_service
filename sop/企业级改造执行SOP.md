# 智能客服企业级改造执行 SOP

## 1. 目标

在保持 Java 后端为订单、工单、SLA、用户和知识版本唯一业务事实源的前提下，完成安全基线、DeepSeek Harness Agent 接入、记忆与知识治理、可靠性与可观测性、前端工程化和灰度发布能力建设。

## 2. 执行原则

1. 每个阶段使用独立提交，完成验收后推送 GitHub。
2. 每个阶段必须先通过相关自动化测试，再把状态从 `IN_PROGRESS` 更新为 `DONE`。
3. 不把模型输出当作授权依据。模型只提出动作，策略层授权，Java 领域服务执行并落库。
4. 不信任客户端或模型传入的 `userId`、`agentId`、角色、租户或权限；身份必须来自已验证的服务端上下文。
5. 高风险写操作必须具备确认、幂等、审计和人工升级路径。
6. Dify 在灰度期间保留为降级通道，不做一次性全量切换。

## 3. GitHub 更新规范

- 工作分支：`codex/enterprise-hardening`
- 阶段提交格式：`phase-N: <阶段结果>`
- 每次推送前执行：`git diff --check`、对应模块测试、检查提交文件清单。
- 每次推送后在本文件记录提交 SHA、测试结果和遗留项。
- `deepseek-harness/` 是独立 Git 仓库，未经确认不得把整个上游仓库嵌入根仓库提交；DSH 插件变更在其独立仓库中提交，或后续抽取到根仓库自有模块。

## 4. 阶段计划与验收标准

### Phase 0：基线与执行治理

状态：`DONE`

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

状态：`DONE`

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

状态：`PENDING`

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

状态：`PENDING`

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

### Phase 4：可靠性、可观测性与数据一致性

状态：`PENDING`

范围：

- 引入数据库迁移管理、乐观锁和唯一约束。
- 外部服务统一超时、重试、熔断、隔离舱和限流。
- MQ 增加幂等、DLQ、重放和 Outbox 状态机。
- 增加 Actuator、Micrometer、OpenTelemetry 和结构化日志。
- 增加 LLM Token、成本、首 Token 延迟、工具成功率、转人工率和越权拦截指标。
- 将生产 Session 持久化迁移到支持多实例的存储。

验收：

- 外部 AI/向量服务故障时可降级，不造成线程池和请求堆积。
- 消息重复投递不会产生重复业务写入。
- 核心链路可按 traceId/sessionId/workOrderId 追踪。
- 多实例重启后会话可以恢复。

### Phase 5：前端工程化、测试体系与发布

状态：`PENDING`

范围：

- 前端逐步迁移 TypeScript，生成 OpenAPI Client。
- 增加 ESLint、单元测试、组件测试和 Playwright E2E。
- 拆分超大组件和大于 500 KB 的 Chunk，统一错误和加载状态。
- 用事件推送替代固定 5 秒轮询，保留断线增量同步。
- 建立 Backend Testcontainers、契约测试、安全回归和 Agent Eval CI。
- 建立 5% → 20% → 50% → 100% 灰度门禁与自动回滚。

验收：

- 主链路 E2E、越权回归和 Agent Eval 在 CI 中稳定通过。
- 前端无超过约定阈值的入口 Chunk。
- 灰度期间 SLO、正确率、投诉率和单位会话成本不劣于基线。

## 5. 当前基线记录

| 项目 | 结果 | 备注 |
|---|---|---|
| Backend | PASS | `mvn test`：23 tests；新增 WebSocket、会话令牌和细粒度权限安全测试 |
| Frontend | PASS | `npm run build`；存在两个超过 1 MB 的 Chunk |
| Data Pipeline | PASS | 18 tests；服务认证测试与 TypeScript typecheck 通过 |
| DSH 客服插件 | NOT VERIFIED | 独立仓库依赖未完整安装，README 标记为 first-pass |

## 6. 阶段执行记录

| 阶段 | 状态 | 提交 SHA | 测试 | 遗留项 |
|---|---|---|---|---|
| Phase 0 | DONE | 见 `phase-0` 阶段提交 | Backend 15/15；Frontend build；Pipeline 17/17 + typecheck | DSH 插件待 Phase 2 验证 |
| Phase 1 | DONE | 见 `phase-1` 阶段提交 | Backend 23/23；Frontend build；Pipeline 18/18 + typecheck | WebSocket 查询参数令牌将在 Phase 2 升级为短期 Capability Ticket；大 Chunk 在 Phase 5 拆分 |
| Phase 2 | PENDING | - | - | - |
| Phase 3 | PENDING | - | - | - |
| Phase 4 | PENDING | - | - | - |
| Phase 5 | PENDING | - | - | - |

## 7. 回滚规则

- 代码回滚以阶段提交为最小单位，不混合跨阶段改动。
- Agent 灰度异常优先将会话路由切回 Dify，不删除 DSH 会话和审计记录。
- 知识索引使用版本化集合/别名切换，不在原集合上执行不可逆全量覆盖。
- 数据库迁移必须提供前向修复方案；破坏性迁移必须先完成备份演练。
