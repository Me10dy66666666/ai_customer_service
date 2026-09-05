/**
 * Customer-service business tools over the Java backend REST API. Registers `lookup_order` and
 * `create_work_order` on `ctx.tools`; each tool POSTs/GETs the backend and unwraps the shared
 * `Result<T>` envelope. Named exports preserve Loader injection metadata.
 * @module @deepseek-ai/dsh-tool-customer-service
 */

import type { Context } from '@deepseek-ai/cordis'
import z from '@deepseek-ai/schemastery'
import { defineTool } from '@deepseek-ai/dsh-tools'

export const name = 'tool-customer-service'
export const inject = ['tools']

/** Model-facing tool configuration. */
export interface Config {
  /** Base URL of the Java backend, e.g. http://localhost:8081. */
  backendBaseUrl: string
  /** Short-lived, user/session/tool-scoped capability injected by the trusted host. */
  capabilityToken: string
  /** DSH session identity injected by the trusted host; never model-visible. */
  sessionId?: string
  /** Cooperative HTTP deadline enforced by the DSH timeout policy. */
  timeoutMs: number
}

/** Schemastery configuration for the tool consumer. */
export const Config: z<Config> = z.object({
  backendBaseUrl: z.string().required(),
  capabilityToken: z.string().default(''),
  sessionId: z.string().default(''),
  timeoutMs: z.number().min(100).max(30_000).default(5_000),
})

/** Shared backend response envelope (see Backend Result<T>). */
interface BackendEnvelope<T> {
  code: number
  message: string
  data: T
}

/** HistoricalOrder projection from GET /api/orders/user/{userId}. */
interface HistoricalOrder {
  id: number | null
  orderNo: string | null
  productName: string | null
  productModel: string | null
  quantity: number | null
  totalAmount: number | null
  orderStatus: string | null
}

function authHeaders(config: Config): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${config.capabilityToken}`,
  }
  if (config.sessionId !== undefined && config.sessionId !== '') {
    headers['X-Agent-Session-Id'] = config.sessionId
  }
  return headers
}

async function unwrapEnvelope<T>(response: Response, path: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`backend ${path} failed: HTTP ${response.status}`)
  }
  const envelope = (await response.json()) as BackendEnvelope<T>
  if (envelope.code !== 200) {
    throw new Error(`backend ${path} error: ${envelope.message}`)
  }
  return envelope.data
}

/**
 * Register the customer-service business tools in the supplied scope.
 * @param ctx - registrant context carrying the tool registry.
 * @param config - deployment's backend base URL and auth token.
 */
export function registerCustomerServiceTools(ctx: Context, config: Config): void {
  ctx.tools.register(defineTool({
    name: 'lookup_order',
    description:
      'Look up the authenticated customer\'s historical orders. Returns each order\'s number, product, '
      + 'quantity, amount, and status. Use it when a customer asks about their past orders.',
    parameters: {},
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          orders: {
            type: 'array',
            required: true,
            items: {
              type: 'object',
              additionalProperties: false,
              properties: {
                orderNo: { type: 'string', required: true },
                productName: { type: 'string', required: true },
                productModel: { type: 'string', required: true },
                quantity: { type: 'integer', required: true },
                totalAmount: { type: 'number', required: true },
                orderStatus: { type: 'string', required: true },
              },
            },
          },
        },
      },
      render: (_args, value) => [{
        type: 'text',
        text: value.orders.length === 0
          ? 'No orders found for this user.'
          : value.orders.map(o => `- ${o.orderNo}: ${o.productName} (${o.quantity} x ${o.totalAmount}, ${o.orderStatus})`).join('\n'),
      }],
    },
    timeoutMs: config.timeoutMs,
    async execute(_args, exec) {
      const path = '/api/agent/tools/orders'
      const response = await fetch(`${config.backendBaseUrl}${path}`, {
        method: 'GET',
        headers: authHeaders(config),
        signal: exec.signal,
      })
      const orders = await unwrapEnvelope<HistoricalOrder[] | null>(response, path)
      return {
        orders: (orders ?? []).map(o => ({
          orderNo: o.orderNo ?? '',
          productName: o.productName ?? '',
          productModel: o.productModel ?? '',
          quantity: o.quantity ?? 0,
          totalAmount: o.totalAmount ?? 0,
          orderStatus: o.orderStatus ?? '',
        })),
      }
    },
    presentCall: () => ({ card: 'generic', title: 'Look up my orders', kind: 'search' }),
  }))

  ctx.tools.register(defineTool({
    name: 'create_work_order',
    description:
      'Create a customer-service work-order proposal for the authenticated customer. The proposal '
      + 'does not execute until that customer explicitly confirms it through the product UI.',
    parameters: {
      title: { type: 'string', required: true, description: 'Short title for the work order (<=200 chars).' },
      description: { type: 'string', required: true, description: 'Full description of the issue.' },
      type: { type: 'string', description: 'Work order type; defaults to "after_sales".' },
      priority: { type: 'string', description: 'Priority: high | medium | low; defaults to "medium".' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          proposalId: { type: 'string', required: true },
          title: { type: 'string', required: true },
          priority: { type: 'string', required: true },
          requiresConfirmation: { type: 'boolean', required: true },
        },
      },
      render: (_args, value) => [{
        type: 'text',
        text: `Work-order proposal ${value.proposalId} is awaiting customer confirmation.`,
      }],
    },
    timeoutMs: config.timeoutMs,
    async execute(args, exec) {
      const path = '/api/agent/tools/work-orders/proposals'
      const response = await fetch(`${config.backendBaseUrl}${path}`, {
        method: 'POST',
        headers: authHeaders(config),
        body: JSON.stringify({
          title: args.title,
          description: args.description,
          type: args.type ?? 'after_sales',
          priority: args.priority ?? 'medium',
        }),
        signal: exec.signal,
      })
      return unwrapEnvelope<{
        proposalId: string
        title: string
        priority: string
        requiresConfirmation: boolean
      }>(response, path)
    },
    presentCall: args => ({ card: 'generic', title: 'Create work order', kind: 'other', rawInput: { title: args.title } }),
  }))
}

/** Loader entry point for a deployment-wide, static configuration. */
export function apply(ctx: Context, config: Config): void {
  registerCustomerServiceTools(ctx, config)
}
