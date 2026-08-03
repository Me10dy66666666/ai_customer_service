import type { FastifyInstance } from "fastify";

import {
  agentMessageRequestSchema,
  agentMessageResponseSchema
} from "@enterprise-webagent/shared";
import type { CustomerAgent } from "@enterprise-webagent/core";

export function registerMessageRoute(
  fastify: FastifyInstance,
  customerAgent: CustomerAgent
): void {
  fastify.post("/api/v1/customer-agent/messages", async (request, reply) => {
    const parsedBody = agentMessageRequestSchema.safeParse(request.body);
    if (!parsedBody.success) {
      return reply.status(400).send({
        message: "请求参数校验失败",
        issues: parsedBody.error.issues
      });
    }

    const responsePayload = await customerAgent.reply(parsedBody.data);
    const parsedResponse = agentMessageResponseSchema.parse(responsePayload);

    return reply.status(200).send(parsedResponse);
  });
}
