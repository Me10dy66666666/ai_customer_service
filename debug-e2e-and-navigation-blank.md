# Debug Session: e2e-and-navigation-blank

**状态**: [OPEN]
**日期**: 2026-06-06
**目标**: E2E 验证两个修复报告 + 排查客服侧导航栏页面切换空白 Bug

---

## 假设验证结果

### H1: 审核计时修复 — review_started_at 在上传时正确写入
**状态**: ⚠️ 无法直接验证（需要 KB Admin 登录上传文档）
**证据**: 代码审查确认 `OcrProcessService.uploadAndOcr()` 第113行正确设置 `reviewStartedAt`

### H2: 首次响应修复 — dispatched_at 在认领工单时正确写入
**状态**: ⚠️ 无数据触发（未进行工单认领操作）
**证据**: 数据洞察页面显示 "平均首次响应" = "-" 符合预期

### H3: 会话派发无竞态 — ✗ 证伪！两个客服同时介入同一会话
**状态**: ❌ **严重 Bug**
**证据**:
- agent1 的待接入队列可见 "用户#2"
- agent2 的待接入队列也可见 "用户#2"
- agent2 成功认领并进入 "接待中" 状态
- agent1 仍然可见该会话

### H4: 页面导航空白 — ✓ 确认
**状态**: ✅ **已确认**
**证据**: 第三次导航到 AgentDesk 时 `AgentDesk:mounted` 事件未触发，DOM 为空 (.admin-stage innerHTML 仅 7 字节)

### H5: 页面导航空白（store 重置） — ✗ 证伪
**状态**: 与控制台日志一致，不是 store 问题

---

## 根因分析

### Bug 1: 会话派发竞态条件
**根因**: `AgentSessionService.transferToHuman()` 通过 `agentBroadcaster.broadcast()` 全员广播 `agent_queue_notify`，消息体无 `dispatchedAgentId`，前端 `agentStore.js` 无条件入队。

**修复方向**:
1. 后端: `agent_queue_notify` 消息体增加 `dispatchedAgentId` 字段
2. 前端: `agentStore.js` 中做归属校验

### Bug 2: 导航栏页面切换空白
**根因**: `AdminLayout.vue` 的 `router-view` 使用 `<transition mode="out-in">`，多次切换后 Vue 的过渡系统丢失组件挂载事件，导致 `Component` 不被创建。

**修复方向**: 移除 `mode="out-in"` 或添加 `:key="$route.fullPath"`

---

## 修复计划
- [ ] Fix Bug 1: 会话派发竞态条件
- [ ] Fix Bug 2: 导航栏页面切换空白
