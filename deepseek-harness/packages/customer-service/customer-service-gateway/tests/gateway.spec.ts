import { EventEmitter } from 'node:events'
import { Readable } from 'node:stream'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { Context } from '@deepseek-ai/cordis'
import type { Agent, AgentHandle, AgentRegistry } from '@deepseek-ai/dsh-agent'
import WebServer, { type WebRoute, type WebServer as WebServerService } from '@deepseek-ai/dsh-host-webserver'
import type { SessionEvent, SessionId } from '@deepseek-ai/dsh-session'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apply, type Config } from '../src/index.ts'

interface ResponseState { status?: number; headers: Record<string, unknown>; body: string; ended: boolean }

function fakeServer(routes: WebRoute[]): WebServerService {
  return {
    register(route: WebRoute) {
      routes.push(route)
      return () => { const index = routes.indexOf(route); if (index !== -1) routes.splice(index, 1) }
    },
  } as unknown as WebServerService
}

function response(): { response: ServerResponse; state: ResponseState } {
  const state: ResponseState = { headers: {}, body: '', ended: false }
  const emitter = new EventEmitter()
  const res = Object.assign(emitter, {
    headersSent: false,
    writableEnded: false,
    setHeader(name: string, value: unknown) { state.headers[name] = value },
    writeHead(status: number, headers?: Record<string, unknown>) {
      state.status = status
      Object.assign(state.headers, headers)
      this.headersSent = true
      return this
    },
    flushHeaders() {},
    write(value: string | Uint8Array) { state.body += Buffer.from(value).toString(); return true },
    end(value?: string | Uint8Array) {
      if (value !== undefined) state.body += Buffer.from(value).toString()
      state.ended = true
      this.writableEnded = true
    },
  }) as unknown as ServerResponse
  return { response: res, state }
}

function request(body: unknown, headers: Record<string, string> = {}): IncomingMessage {
  const req = Readable.from([Buffer.from(JSON.stringify(body))]) as unknown as IncomingMessage
  Object.assign(req, { method: 'POST', headers: { 'content-type': 'application/json', ...headers } })
  return req
}

function fakeAgent(
  id: SessionId,
  events: SessionEvent[],
  notify?: (session: { id: SessionId; events: SessionEvent[] }, event: SessionEvent) => void,
): Agent {
  const session = { id, events }
  const append = (event: SessionEvent) => { events.push(event); notify?.(session, event) }
  return {
    id,
    session,
    followup(message: Parameters<Agent['followup']>[0]) {
      append({ type: 'user/message', seq: events.length, time: Date.now(), data: message })
      append({
        type: 'assistant/chunk',
        seq: events.length,
        time: Date.now(),
        data: { turn: 1, step: 1, chunk: { type: 'text-delta', text: '流' } },
      } as unknown as SessionEvent)
      append({
        type: 'assistant/message',
        seq: events.length,
        time: Date.now(),
        data: {
          turn: 1,
          step: 1,
          message: {
            id: 'assistant-1',
            role: 'assistant',
            source: { kind: 'model', provider: 'mock', model: 'mock' },
            content: [{ type: 'text', text: '已处理' }],
          },
        },
      } as SessionEvent)
      append({ type: 'turn/end', seq: events.length, time: Date.now(), data: { turn: 1, reason: { kind: 'completed' } } })
    },
    whenIdle: () => Promise.resolve(),
  } as unknown as Agent
}

function setup(overrides: Partial<Config> = {}): {
  routes: WebRoute[]
  ctx: Context
  dispose: () => Promise<void>
  agent: Agent
  createdOptions: Record<string, unknown> | undefined
} {
  const ctx = new Context()
  const routes: WebRoute[] = []
  const events: SessionEvent[] = []
  const agent = fakeAgent('conversation-1' as SessionId, events, (session, event) => {
    ctx.emit('session/event', session as never, event)
  })
  const handle: AgentHandle = { agent, dispose: async () => {} }
  let createdOptions: Record<string, unknown> | undefined
  ctx.provide('webServer', fakeServer(routes))
  ctx.provide('agents', {
    create: async (options: Record<string, unknown>) => { createdOptions = options; return handle },
    resume: async () => handle,
    get: () => agent,
  } as unknown as AgentRegistry)
  const config: Config = {
    provider: 'mock', model: 'mock', serviceToken: 'secret', pathPrefix: '/api/v1/customer-service', maxBodyBytes: 8192,
    ...overrides,
  }
  apply(ctx, config)
  return {
    routes,
    ctx,
    dispose: async () => ctx.fiber.dispose(),
    agent,
    get createdOptions() { return createdOptions },
  }
}

afterEach(() => {})

describe('customer-service HTTP boundary', () => {
  it('rejects missing credentials before creating an agent', async () => {
    const mounted = setup()
    const result = response()
    await mounted.routes[0]!.handler(request({ query: 'hello' }), result.response)
    expect(result.state.status).toBe(401)
    expect(result.state.body).toContain('credential')
    await mounted.dispose()
  })

  it('returns the committed answer and preserves the conversation id', async () => {
    const mounted = setup()
    const result = response()
    await mounted.routes[0]!.handler(request({ query: 'hello', conversation_id: 'conversation-1' }, { authorization: 'Bearer secret' }), result.response)
    expect(result.state.status).toBe(200)
    expect(JSON.parse(result.state.body)).toMatchObject({ answer: '已处理', conversation_id: 'conversation-1' })
    await mounted.dispose()
  })

  it('projects token deltas and closes an SSE turn', async () => {
    const mounted = setup()
    const streamRoute = mounted.routes[1]!
    const result = response()
    await streamRoute.handler(request({ query: 'hello', conversation_id: 'conversation-1', response_mode: 'streaming' }, { authorization: 'Bearer secret' }), result.response)
    expect(result.state.status).toBe(200)
    expect(result.state.headers['content-type']).toContain('text/event-stream')
    expect(result.state.body).toContain('"type":"token"')
    expect(result.state.body).toContain('"conversation_id":"conversation-1"')
    expect(result.state.body).toContain('"type":"done"')
    await mounted.dispose()
  })

  it('composes customer tools inside the agent scope and binds the scope to one capability', async () => {
    const mounted = setup({ customerServiceBackendBaseUrl: 'http://backend.test' })
    const result = response()
    await mounted.routes[0]!.handler(request({ query: 'hello' }, {
      authorization: 'Bearer secret',
      'x-agent-capability-token': 'capability-a',
    }), result.response)
    expect(result.state.status).toBe(200)
    const conversationId = (JSON.parse(result.state.body) as { conversation_id: string }).conversation_id
    expect(mounted.createdOptions?.setup).toEqual(expect.any(Function))

    const register = vi.fn()
    await (mounted.createdOptions?.setup as ((ctx: unknown) => unknown) | undefined)?.({ tools: { register } })
    expect(register).toHaveBeenCalledTimes(2)

    const mismatch = response()
    await mounted.routes[0]!.handler(request({ query: 'again', conversation_id: conversationId }, {
      authorization: 'Bearer secret',
      'x-agent-capability-token': 'capability-b',
    }), mismatch.response)
    expect(mismatch.state.status).toBe(401)
    await mounted.dispose()
  })

  it('allows a guest knowledge turn without mounting customer business tools', async () => {
    const mounted = setup({ customerServiceBackendBaseUrl: 'http://backend.test' })
    const result = response()
    await mounted.routes[0]!.handler(request({ query: '公开 FAQ' }, { authorization: 'Bearer secret' }), result.response)
    expect(result.state.status).toBe(200)
    expect(mounted.createdOptions?.setup).toBeUndefined()
    await mounted.dispose()
  })

  it('serves the same contract through a real webserver composition', async () => {
    const ctx = new Context()
    const events: SessionEvent[] = []
    const agent = fakeAgent('snapshot-conversation' as SessionId, events)
    const handle: AgentHandle = { agent, dispose: async () => {} }
    ctx.provide('agents', {
      create: async () => handle,
      resume: async () => handle,
      get: () => agent,
    } as unknown as AgentRegistry)
    const web = ctx.plugin(WebServer, { host: '127.0.0.1', port: 0 })
    await web
    apply(ctx, { provider: 'mock', model: 'mock', serviceToken: 'test', pathPrefix: '/api/v1/customer-service', maxBodyBytes: 8192 })
    const result = await fetch(`http://127.0.0.1:${String(ctx.webServer.port)}/api/v1/customer-service/messages`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: 'Bearer test' },
      body: JSON.stringify({ query: 'snapshot', conversation_id: 'snapshot-conversation' }),
    })
    expect(result.status).toBe(200)
    expect(await result.json()).toMatchObject({ answer: '已处理', conversation_id: 'snapshot-conversation' })
    await ctx.fiber.dispose()
  }, 60_000)
})
