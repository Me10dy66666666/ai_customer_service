import { afterEach, describe, expect, it, vi } from 'vitest'
import { Context } from '@deepseek-ai/cordis'
import { CallId } from '@deepseek-ai/dsh-llm'
import SystemPrompt from '@deepseek-ai/dsh-system-prompt'
import ToolRuntime from '@deepseek-ai/dsh-tools'
import * as customerServiceTools from '../src/index.ts'

const signal = new AbortController().signal

afterEach(() => {
  vi.unstubAllGlobals()
})

async function setup() {
  const ctx = new Context()
  await ctx.plugin(SystemPrompt)
  await ctx.plugin(ToolRuntime)
  customerServiceTools.apply(ctx, {
    backendBaseUrl: 'http://backend.test',
    capabilityToken: 'signed-capability',
    sessionId: 'session-1',
    timeoutMs: 5_000,
  })
  return ctx
}

describe('customer-service capability boundary', () => {
  it('keeps identity out of model-visible schemas and injects the host capability', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 200,
      message: 'ok',
      data: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const ctx = await setup()

    const schema = ctx.tools.schemas().find(tool => tool.name === 'lookup_order')
    expect(schema?.parameters.properties).not.toHaveProperty('userId')

    await ctx.tools.execute({
      callId: CallId('lookup-own-orders'),
      name: 'lookup_order',
      arguments: {},
      signal,
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const orderCall = fetchMock.mock.calls[0]
    expect(orderCall?.[0]).toBe('http://backend.test/api/agent/tools/orders')
    const orderInit = orderCall?.[1] as RequestInit
    const orderHeaders = new Headers(orderInit.headers)
    expect(orderHeaders.get('Authorization')).toBe('Bearer signed-capability')
    expect(orderHeaders.get('X-Agent-Session-Id')).toBe('session-1')
  })

  it('creates a confirmation proposal instead of executing a work order', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 200,
      message: 'ok',
      data: {
        proposalId: 'proposal-1',
        title: '退款咨询',
        priority: 'medium',
        requiresConfirmation: true,
      },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const ctx = await setup()

    await ctx.tools.execute({
      callId: CallId('propose-work-order'),
      name: 'create_work_order',
      arguments: { title: '退款咨询', description: '需要人工处理' },
      signal,
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const proposalCall = fetchMock.mock.calls[0]
    expect(proposalCall?.[0]).toBe('http://backend.test/api/agent/tools/work-orders/proposals')
    const proposalInit = proposalCall?.[1] as RequestInit
    expect(proposalInit.method).toBe('POST')
  })
})
