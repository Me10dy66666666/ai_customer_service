# 智能客服场景 Agent 架构技术调研

> 撰写日期：2026-08-10
> 项目背景：本项目（ai_customer_service）是一个基于 Spring Boot + Vue 3 + Dify AI 引擎构建的全栈智能客服平台，核心功能包括 AI 自动应答、多轮对话、知识检索、工单流转、SLA 管理与人工转接。`ts-enterprise-webagent` 子项目正在将 Python 版 LangChain Agent 迁移为 TypeScript 原生实现，当前已建成 adapter 抽象层（KnowledgeRetriever / ChatModel / Logger）与基于 RAG 的基础编排管线 `CustomerAgentModule`。

---

## 目录

- [1. 主流 Agent 架构对比](#1-主流-agent-架构对比)
  - [1.1 ReAct (Reasoning + Acting)](#11-react-reasoning--acting)
  - [1.2 Plan-and-Execute](#12-plan-and-execute)
  - [1.3 Function Calling (原生 Tool Use)](#13-function-calling-原生-tool-use)
  - [1.4 Multi-Agent 协作](#14-multi-agent-协作)
  - [1.5 RAG-first Agent](#15-rag-first-agent)
  - [1.6 架构适配度总览](#16-架构适配度总览)
- [2. Agent 编排调度框架选型](#2-agent-编排调度框架选型)
  - [2.1 LangGraph](#21-langgraph)
  - [2.2 Vercel AI SDK](#22-vercel-ai-sdk)
  - [2.3 Mastra](#23-mastra)
  - [2.4 CrewAI](#24-crewai)
  - [2.5 自定义编排方案](#25-自定义编排方案)
  - [2.6 框架选型总览](#26-框架选型总览)
- [3. 最终推荐方案](#3-最终推荐方案)

---

## 1. 主流 Agent 架构对比

### 1.1 ReAct (Reasoning + Acting)

- **原理**：思考-行动-观察循环交织，LLM 输出 Thought/Action/Observation 三段式文本
- **优势**：可解释性高、灵活工具选择
- **劣势**：延迟高（3-5次LLM调用）、Token消耗大、稳定性不足（无限循环/解析失败）
- **适合度**：部分适合，作为复杂意图降级路径；不推荐作为主架构

### 1.2 Plan-and-Execute

- **原理**：先生成完整执行计划，再由 Executor 按计划逐步执行
- **优势**：全局视野、计划可审查、步骤可并行
- **劣势**：初始延迟极高、计划僵硬不适应动态对话、过度设计
- **适合度**：不适合作为主架构

### 1.3 Function Calling (原生 Tool Use)

- **原理**：LLM 直接输出结构化 tool_calls（JSON），Host 执行后返回结果
- **优势**：低延迟（1-2次调用）、高可靠性（结构化输出）、并行工具调用、TypeScript友好
- **劣势**：多步推理需要 Host 编排、工具列表膨胀
- **适合度**：非常适合作为主架构

### 1.4 Multi-Agent 协作

- **原理**：多个专业化子 Agent 通过消息传递或共享黑板协作
- **优势**：职责分离清晰、独立迭代
- **劣势**：延迟累计（3-5倍单Agent）、协调复杂度高、Token开销巨大
- **适合度**：当前不适合，作为长期演进方向

### 1.5 RAG-first Agent

- **原理**：知识检索为必经管线，工具调用作为辅助降级
- **优势**：极低延迟、幻觉风险最低、成本最优
- **劣势**：无法处理纯行动类请求、过度依赖知识库质量
- **适合度**：非常适合作为"快速路径"（FAQ类问题）

### 1.6 架构适配度总览

| 维度 | ReAct | Plan-Execute | Function Calling | Multi-Agent | RAG-first |
|------|-------|-------------|-------------------|-------------|-----------|
| **首次响应延迟** | 高 | 极高 | 中低 | 极高 | 极低 |
| **简单问答处理** | 过度设计 | 过度设计 | 适中 | 过度设计 | 最优 |
| **复杂多步推理** | 最优 | 良好 | 需要Host编排 | 良好 | 弱 |
| **可靠性与稳定性** | 中 | 低 | 高 | 低 | 高 |
| **Token消耗** | 高 | 高 | 中 | 极高 | 低 |
| **综合推荐度** | 降级路径 | 不推荐 | 主架构 | 长期方向 | 快速路径 |

---

## 2. Agent 编排调度框架选型

### 2.1 LangGraph

- **TypeScript 版**：`@langchain/langgraph` v0.2+
- **成熟度**：中高（Python版极高），状态图编排 + Checkpoint + 人在回路
- **适配度**：高（8/10），Python版已有积累，TS版API对齐

### 2.2 Vercel AI SDK

- **成熟度**：高（10k+ Stars），原生 Tool Calling 支持
- **适配度**：中高（7/10），但缺少工作流编排和Checkpoint

### 2.3 Mastra

- **成熟度**：低中（5k+ Stars，v0.x），轻量 TypeScript 原生
- **适配度**：中（6/10），值得关注但生产风险高

### 2.4 CrewAI

- **TypeScript**：不支持（仅Python）
- **适配度**：低（2/10）

### 2.5 自定义编排方案

- **适配度**：中（6/10），完全可控但需自行实现框架级能力

### 2.6 框架选型总览

| 维度 | LangGraph | Vercel AI SDK | Mastra | CrewAI | 自定义 |
|------|-----------|--------------|--------|--------|--------|
| **TS原生** | 是 | 完全原生 | 完全原生 | 否 | 完全原生 |
| **框架成熟度** | 中高 | 高 | 低中 | 中(Python) | 取决于实现 |
| **工作流编排** | 极强 | 弱 | 中等 | 强 | 完全自主 |
| **Checkpoint** | 内置 | 无 | 有限 | 有 | 需自行实现 |
| **人在回路** | 内置 | 无 | 无 | 有 | 需自行实现 |
| **综合推荐** | 首选(8/10) | 备选(7/10) | 观望(6/10) | 不推荐(2/10) | 保底(6/10) |

---

## 3. 最终推荐方案

### 推荐架构：Function Calling + RAG 混合

**三条通道设计：**

1. **RAG 快速通道**（覆盖 70%）- FAQ/政策查询 → 知识检索 → LLM生成 → 延迟 < 1.5s
2. **Function Calling Agent**（覆盖 20%）- 订单/工单/转人工 → LLM选工具 → Host执行 → 延迟 < 3s
3. **兜底策略**（覆盖 10%）- 闲聊/问候 → 规则匹配 → 固定话术 → 延迟 < 1s

### 推荐框架：LangGraph (TS) + 自定义混合

- LangGraph 负责复杂流程：Function Calling 工具循环、人在回路审批、人工转接子图
- 自定义编排保持 RAG 快速通道的轻量高性能

### 工具清单

| 工具名 | 功能 | Dify对应 |
|--------|------|----------|
| `search_knowledge` | 知识库检索 | Dify Knowledge API |
| `lookup_order` | 查询用户订单 | Backend OrderService |
| `create_work_order` | 创建工单（触发人在回路审批） | WorkOrderApplicationService |
| `transfer_to_human` | 转接人工坐席 | SessionDispatchService |
| `check_sla_policy` | 查询SLA政策 | SlaCalculationService |
| `analyze_sentiment` | 情绪分析 | Dify Workflow (工单分析) |
| `summarize_conversation` | 对话摘要 | Dify Transfer Workflow |

### 迁移路线图

- **Phase 1（已完成）**：adapter 抽象层 + RAG 基础管线
- **Phase 2（当前）**：RAG 增强 + 意图路由 + 统一配置 + Agent管理界面
- **Phase 3**：Function Calling Agent + LangGraph 引入
- **Phase 4**：人在回路 + 人工转接
- **Phase 5**：持续优化
