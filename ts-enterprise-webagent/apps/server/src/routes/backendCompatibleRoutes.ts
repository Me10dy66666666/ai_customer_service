import type { FastifyInstance } from "fastify";
import type { Logger, KnowledgeBaseManager } from "@enterprise-webagent/core";
import type { RouteDispatcher } from "@enterprise-webagent/core";
import { agentMessageRequestSchema, agentMessageResponseSchema } from "@enterprise-webagent/shared";

/**
 * 注册 Backend 对齐路由
 *
 * 提供与 Backend AiChatPort / KnowledgeBasePort 接口完全对标的一组 HTTP API，
 * 使 Backend 可以通过 TsAgentAdapter 以相同接口调用 TS Agent 服务。
 */
export function registerBackendCompatibleRoutes(
  fastify: FastifyInstance,
  dispatcher: RouteDispatcher,
  knowledgeBaseManager: KnowledgeBaseManager | null,
  logger: Logger
): void {
  // ============================================================
  // AiChatPort 对齐端点
  // ============================================================

  /**
   * 阻塞式消息 — 对齐 AiChatPort.sendBlockingMessage()
   *
   * Backend 调用方: ChatApplicationService
   *
   * 请求体（对齐 DifyClient.sendMessage）
   * {
   *   "query": "用户问题",
   *   "user": "user_1",
   *   "conversation_id": "会话ID",
   *   "inputs": { "userType": 1, "historyOrders": "...", "locale": "zh-CN" }
   * }
   *
   * 响应体（对齐 Dify /chat-messages 格式）
   * {
   *   "answer": "AI 回复",
   *   "conversation_id": "会话ID"
   * }
   */
  fastify.post("/api/v1/chat-messages", async (request, reply) => {
    const body = request.body as Record<string, unknown>;
    const query = String(body.query ?? "");
    const conversationId = String(body.conversation_id ?? "");

    // 从 Dify 风格的 inputs 中提取字段
    const inputs = (body.inputs as Record<string, unknown>) ?? {};

    const agentRequest = {
      userInput: query,
      sessionId: conversationId || undefined,
      userType: Number(inputs.userType ?? 0),
      historyOrders: typeof inputs.historyOrders === "string"
        ? (inputs.historyOrders as string).split(",").filter(Boolean)
        : [],
      locale: String(inputs.locale ?? "zh-CN")
    };

    const parsed = agentMessageRequestSchema.safeParse(agentRequest);
    if (!parsed.success) {
      return reply.status(400).send({
        message: "请求参数校验失败",
        issues: parsed.error.issues
      });
    }

    try {
      const response = await dispatcher.dispatch(parsed.data);
      return reply.status(200).send({
        answer: response.answer,
        conversation_id: response.sessionId
      });
    } catch (error) {
      logger.error("Chat message failed", {
        error: error instanceof Error ? error.message : String(error)
      });
      return reply.status(500).send({
        answer: "抱歉，AI 服务暂时不可用，请稍后重试。",
        conversation_id: conversationId
      });
    }
  });

  /**
   * 流式消息 — 对齐 AiChatPort.sendStreamingMessage()
   *
   * Backend 调用方: ChatApplicationService（流式对话）
   * 返回 SSE (Server-Sent Events) 格式
   */
  fastify.post("/api/v1/chat-messages/streaming", async (request, reply) => {
    const body = request.body as Record<string, unknown>;
    const query = String(body.query ?? "");
    const conversationId = String(body.conversation_id ?? "");

    const inputs = (body.inputs as Record<string, unknown>) ?? {};
    const agentRequest = {
      userInput: query,
      sessionId: conversationId || undefined,
      userType: Number(inputs.userType ?? 0),
      historyOrders: typeof inputs.historyOrders === "string"
        ? (inputs.historyOrders as string).split(",").filter(Boolean)
        : [],
      locale: String(inputs.locale ?? "zh-CN")
    };

    const parsed = agentMessageRequestSchema.safeParse(agentRequest);
    if (!parsed.success) {
      return reply.status(400).send({
        message: "请求参数校验失败",
        issues: parsed.error.issues
      });
    }

    // 设置 SSE 响应头
    reply.raw.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "X-Accel-Buffering": "no"
    });

    try {
      const response = await dispatcher.dispatch(parsed.data);

      // 模拟流式输出：将回答按句子分块发送
      const chunks = response.answer.split(/(?<=[。！？\n])/g);
      for (const chunk of chunks) {
        if (chunk.trim()) {
          reply.raw.write(`data: ${JSON.stringify({ answer: chunk, conversation_id: response.sessionId })}\n\n`);
          await new Promise(resolve => setTimeout(resolve, 30));
        }
      }
      reply.raw.write(`data: ${JSON.stringify({ answer: "", conversation_id: response.sessionId, event: "message_end" })}\n\n`);
    } catch (error) {
      const errMsg = error instanceof Error ? error.message : "Stream error";
      reply.raw.write(`data: ${JSON.stringify({ error: errMsg })}\n\n`);
    } finally {
      reply.raw.end();
    }
  });

  // ============================================================
  // KnowledgeBasePort 对齐端点
  // ============================================================

  if (!knowledgeBaseManager) {
    logger.warn("KnowledgeBaseManager not configured, knowledge endpoints will return 501");
  }

  /**
   * 上传文档 — 对齐 KnowledgeBasePort.uploadFile()
   */
  fastify.post("/api/v1/knowledge/datasets/:datasetId/documents", async (request, reply) => {
    if (!knowledgeBaseManager) {
      return reply.status(501).send({ message: "知识库管理未配置" });
    }
    try {
      const { datasetId } = request.params as { datasetId: string };
      const data = await request.file();
      if (!data) {
        return reply.status(400).send({ message: "缺少文件" });
      }
      const buffer = await data.toBuffer();
      const documentId = await knowledgeBaseManager.uploadDocument(buffer, data.filename, datasetId);
      return reply.status(200).send({ document: { id: documentId } });
    } catch (error) {
      logger.error("Upload document failed", { error: String(error) });
      return reply.status(500).send({ message: "上传失败" });
    }
  });

  /**
   * 删除文档 — 对齐 KnowledgeBasePort.deleteDocument()
   */
  fastify.delete("/api/v1/knowledge/datasets/:datasetId/documents/:documentId", async (request, reply) => {
    if (!knowledgeBaseManager) {
      return reply.status(501).send({ message: "知识库管理未配置" });
    }
    try {
      const { datasetId, documentId } = request.params as { datasetId: string; documentId: string };
      await knowledgeBaseManager.deleteDocument(datasetId, documentId);
      return reply.status(200).send({ result: "success" });
    } catch (error) {
      logger.error("Delete document failed", { error: String(error) });
      return reply.status(500).send({ message: "删除失败" });
    }
  });

  /**
   * 获取数据集信息 — 对齐 KnowledgeBasePort.getDataset()
   */
  fastify.get("/api/v1/knowledge/datasets/:datasetId", async (request, reply) => {
    if (!knowledgeBaseManager) {
      return reply.status(501).send({ message: "知识库管理未配置" });
    }
    try {
      const { datasetId } = request.params as { datasetId: string };
      const info = await knowledgeBaseManager.getDataset(datasetId);
      return reply.status(200).send(info);
    } catch (error) {
      logger.error("Get dataset failed", { error: String(error) });
      return reply.status(500).send({ message: "查询失败" });
    }
  });

  /**
   * 更新文档状态 — 对齐 KnowledgeBasePort.updateDocumentStatus()
   */
  fastify.patch("/api/v1/knowledge/datasets/:datasetId/documents/:documentId/status", async (request, reply) => {
    if (!knowledgeBaseManager) {
      return reply.status(501).send({ message: "知识库管理未配置" });
    }
    try {
      const { datasetId, documentId } = request.params as { datasetId: string; documentId: string };
      const body = request.body as { enabled?: boolean; enable?: boolean };
      const enabled = body.enabled ?? body.enable ?? true;
      await knowledgeBaseManager.updateDocumentStatus(datasetId, documentId, enabled);
      return reply.status(200).send({ result: "success" });
    } catch (error) {
      logger.error("Update document status failed", { error: String(error) });
      return reply.status(500).send({ message: "更新失败" });
    }
  });

  /**
   * 分页列出文档 — 对齐 KnowledgeBasePort.listDocuments()
   */
  fastify.get("/api/v1/knowledge/datasets/:datasetId/documents", async (request, reply) => {
    if (!knowledgeBaseManager) {
      return reply.status(200).send({ data: [], has_more: false });
    }
    try {
      const { datasetId } = request.params as { datasetId: string };
      const query = request.query as { page?: string; limit?: string };
      const page = parseInt(query.page ?? "1", 10);
      const limit = parseInt(query.limit ?? "20", 10);
      const docs = await knowledgeBaseManager.listDocuments(datasetId, page, limit);
      return reply.status(200).send({ data: docs, has_more: docs.length === limit });
    } catch (error) {
      logger.error("List documents failed", { error: String(error) });
      return reply.status(200).send({ data: [], has_more: false });
    }
  });

  /**
   * 列出所有文档 — 对齐 KnowledgeBasePort.listAllDocuments()
   */
  fastify.get("/api/v1/knowledge/datasets/:datasetId/documents/all", async (request, reply) => {
    if (!knowledgeBaseManager) {
      return reply.status(200).send([]);
    }
    try {
      const { datasetId } = request.params as { datasetId: string };
      const docs = await knowledgeBaseManager.listAllDocuments(datasetId);
      return reply.status(200).send(docs);
    } catch (error) {
      logger.error("List all documents failed", { error: String(error) });
      return reply.status(200).send([]);
    }
  });

  logger.info("Backend-compatible routes registered");
}
