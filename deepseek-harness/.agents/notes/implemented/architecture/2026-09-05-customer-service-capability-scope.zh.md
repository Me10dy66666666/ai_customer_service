# Agent Note: 客服工具按会话组装并绑定 capability token

Status: implemented

[English](2026-09-05-customer-service-capability-scope.md) | 中文

## Problem

客服 Agent 跨越可信的 Java 业务边界。部署级工具插件无法安全表达并发会话各自的认证客户：它会共享 bearer credential，而模型可见的参数还可能变成身份选择器。创建工单会改变持久化业务状态，因此必须来自客户的明确决定，而不能由模型意图推断。

## Decision

客服 Gateway 在 Agent 创建或恢复时，把业务工具 Consumer 组装到每个 Agent scope 中。Host 传入可信 Java Gateway 生成的短期 capability token；token 只保留在工具闭包和受管会话记录中，绝不进入模型消息、Prompt、工具 schema 或 telemetry 值。

Java backend URL 已配置时，Gateway 要求请求携带一个 capability token，并把它绑定到会话；后续请求携带不同 token 会被拒绝。没有 conversation id 的并发请求获得彼此独立的会话身份。现有 gateway service token 是 Java 与 DSH 之间独立的传输凭据。

Java Agent Tool Gateway 校验 capability JWT，并从服务端用户记录解析 subject。`lookup_order` 只能读取该 subject 的订单。`create_work_order` 只调用 proposal 命令，将已校验的用户和会话信息写入带 TTL 的 Redis 记录，不调用工单领域写入。产品用户必须携带 `USER` 或 `VIP` JWT 调用确认接口；确认会再次校验归属，使用 `getAndDelete` 原子消费 proposal，然后委托 `WorkOrderApplicationService` 创建工单。

该包遵循 DSH [Service Definition / Service Provider / Consumer 能力 seam 术语](2026-06-13-capability-seams.zh.md)：`tool-customer-service` 负责模型可见 schema 和 HTTP 执行，Gateway 负责按 scope 组装。工具包仍导出 `apply` 支持简单静态组合，同时把注册函数作为 scoped Host 的显式 seam。

## Alternatives considered

- **使用共享部署 token 的全局客服工具实例**：拒绝，因为凭据无法区分并发客户，跨用户访问会变成部署级误用。
- **让模型提供 `userId` 或授权 header**：拒绝，因为模型输出是不可信数据，授权身份必须来自已认证的 Java 请求。
- **允许 Agent 直接创建工单**：拒绝，因为模型工具调用不等于客户确认，重试会造成持久化写入重复。
- **只把 proposal 放在进程内存中**：拒绝，因为重启和多实例会丢失确认记录；Redis 提供共享 TTL 存储和原子一次性消费。

## Consequences

每个会话获得隔离的业务工具闭包和稳定的授权身份，模型仍只看到相同的窄 schema，而 Host 控制工具代表谁执行。Java 保持业务事实源并拥有最终写入权。额外的确认轮次和 Redis 状态是有意承担的产品与运维成本。静态本地 DSH 组合仍可使用导出的 `apply`，但生产组装必须提供按会话的 capability token。

## Testing

Gateway 测试在 fake Agent scope 中组装真实客服工具注册函数，验证同一会话接受首个 capability 并拒绝后续不同 capability。Java controller 测试验证 capability 只能提出 proposal、其他用户不能确认，以及确认前会先消费 proposal 再调用领域服务。Host library 编译和 TypeScript 类型检查覆盖跨包 setup 契约。
