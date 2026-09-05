# Agent Note: Customer-service HTTP gateway owns Agent session transport

Status: implemented

English | [中文](2026-09-05-customer-service-http-gateway.zh.md)

## Problem

Java Backend 需要稳定的客服边界，但 DSH Agent runtime 以进程内 service 暴露会话所有权、事件日志和流式输出。若让 Backend 直接依赖这些内部 API，会复制生命周期逻辑，并使重启恢复和 SSE 顺序出现分叉。

## Decision

`@deepseek-ai/dsh-customer-service-gateway` 作为 customer-service composition 的 host-side BFF。它在一个可配置前缀下提供两个精确的 `POST` 路由：阻塞式消息和 SSE 流式消息。模块校验有大小上限的 JSON envelope，可选校验受信任 Backend 的 Bearer token；有 `conversation_id` 时映射为 DSH `SessionId` 并恢复持久化会话，否则创建新的持久会话。

每个托管会话一次只接受一个 turn。阻塞响应等待 Agent 空闲，投影已提交的 assistant message、usage、工具调用结果和转人工事实。SSE 响应投影文本增量、工具、usage、done、error 事件，并在对应 turn 边界关闭。所有 Agent handle 和路由注册随插件 fiber 一起释放。

Gateway 不会把 `user` 或任意 `inputs` 追加到 model prompt；业务事实和 capability 边界仍由 Backend 负责。空 service token 仅用于 loopback 开发和无密钥测试；部署时设置 token，由 Java client 转发。

## Alternatives considered

**通过 HTTP 暴露 ACP** — 否决。ACP 是 stdio automation 协议，拥有不同的会话和传输生命周期；在这里适配会把协议状态泄漏到 Java contract，并使 SSE 取消语义不清晰。

**让 Backend 管理 DSH 会话** — 否决。会话恢复、事件顺序、turn 串行化和 handle 释放都是 DSH runtime invariant，应由 BFF 深模块持有。

**每次请求创建新 Agent** — 否决。这会丢失持久对话历史，使 `conversation_id` 无法在重启后恢复。

## Consequences

Java adapter 只消费小型 provider-neutral HTTP contract，并在不依赖 DSH package 类型的前提下记录 DSH usage/tool/handoff 事实。本地 JSONL 持久化在 persistence root 共享时支持进程重启恢复；多实例部署仍需要共享持久化后端，必须挂载相同 root 或替换 persistence provider。

## Verification

customer-service focused gate 覆盖凭证拒绝、阻塞答案投影、SSE 完成以及真实无密钥 `node:http` composition。DSH gateway 与 ACP application 可一起 type-check；Flyway runtime 依赖解析后 Java Backend 测试套件通过。
