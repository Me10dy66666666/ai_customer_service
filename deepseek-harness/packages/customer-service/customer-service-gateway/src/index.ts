/**
 * Customer-service HTTP BFF. It owns the narrow transport contract consumed by
 * Backend: authenticated JSON requests become durable DSH turns, while the
 * streaming endpoint projects committed text deltas as SSE. Session ownership,
 * resume, serialization, and event demultiplexing stay behind this seam.
 * @module @deepseek-ai/dsh-customer-service-gateway
 */

import type { Context } from '@deepseek-ai/cordis'
import type { AgentHandle, AgentSetup } from '@deepseek-ai/dsh-agent'
import { createUserMessage, type StreamChunk } from '@deepseek-ai/dsh-llm'
import type { WebServer } from '@deepseek-ai/dsh-host-webserver'
import { SessionId, type SessionEvent } from '@deepseek-ai/dsh-session'
import { registerCustomerServiceTools } from '@deepseek-ai/dsh-tool-customer-service'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { randomUUID, timingSafeEqual } from 'node:crypto'
import z from '@deepseek-ai/schemastery'

export const name = 'customer-service-gateway'
export const inject = ['agents', 'webServer']

const DEFAULT_PATH_PREFIX = '/api/v1/customer-service'
const DEFAULT_MAX_BODY_BYTES = 256 * 1024

/** Runtime settings for the trusted Backend-to-DSH boundary. */
export interface Config {
  /** Provider route selected by the host's model composition. */
  provider: string
  /** Model selected by the host's model composition. */
  model: string
  /** Bearer token accepted from the trusted Backend gateway. */
  serviceToken: string
  /** Base path for the blocking and streaming message routes. */
  pathPrefix: string
  /** Maximum JSON request body retained in memory. */
  maxBodyBytes: number
  /** Java backend URL used for per-session customer-service tools; blank disables scoped tools. */
  customerServiceBackendBaseUrl?: string
  /** Local-only fallback token for ACP/dev callers that cannot forward a request header. */
  customerServiceCapabilityToken?: string
  /** Timeout applied to the scoped customer-service business tools. */
  customerServiceToolTimeoutMs?: number
}

export const Config: z<Config> = z.object({
  provider: z.string().required(),
  model: z.string().required(),
  serviceToken: z.string().required(),
  pathPrefix: z.string().default(DEFAULT_PATH_PREFIX),
  maxBodyBytes: z.natural().min(1024).max(8 * 1024 * 1024).default(DEFAULT_MAX_BODY_BYTES),
  customerServiceBackendBaseUrl: z.string().default(''),
  customerServiceCapabilityToken: z.string().default(''),
  customerServiceToolTimeoutMs: z.number().min(100).max(30_000).default(5_000),
})

interface MessageRequest {
  query: string
  user?: string
  conversation_id?: string
  inputs?: Record<string, unknown>
  response_mode?: 'blocking' | 'streaming'
}

interface ManagedSession {
  readonly id: SessionId
  readonly handle: AgentHandle
  /** Effective capability identity bound at session composition time. Never model-visible. */
  readonly capabilityToken?: string
  busy: boolean
}

interface ResolvedConfig extends Config {
  customerServiceBackendBaseUrl: string
  customerServiceCapabilityToken: string
  customerServiceToolTimeoutMs: number
}

interface JsonResponse {
  answer: string
  conversation_id: string
  usage?: unknown
  tool_calls?: readonly unknown[]
  handoff?: unknown
}

interface ToolCallFact {
  name: string
  outcome: 'success' | 'error'
  latencyMs?: number
}

interface TurnFacts {
  toolCalls: ToolCallFact[]
  usage?: unknown
  handoff?: string
}

class HttpError extends Error {
  public constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'HttpError'
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function normalizePathPrefix(value: string): string {
  const normalized = `/${value.trim().replace(/^\/+|\/+$/g, '')}`
  if (normalized === '/') throw new Error('customer-service gateway pathPrefix must not be root')
  if (normalized.includes('//')) throw new Error('customer-service gateway pathPrefix must not contain empty segments')
  return normalized
}

function validateConfig(config: Config): ResolvedConfig {
  const pathPrefix = normalizePathPrefix(config.pathPrefix)
  const serviceToken = config.serviceToken.trim()
  if (serviceToken === '') {
    throw new Error('customer-service gateway serviceToken is required')
  }
  if (config.serviceToken.includes('\n') || config.serviceToken.includes('\r')) {
    throw new Error('customer-service gateway serviceToken must not contain line breaks')
  }
  const customerServiceBackendBaseUrl = config.customerServiceBackendBaseUrl?.trim() ?? ''
  if (customerServiceBackendBaseUrl !== '') {
    try {
      new URL(customerServiceBackendBaseUrl)
    } catch {
      throw new Error('customer-service gateway customerServiceBackendBaseUrl must be an absolute URL')
    }
  }
  const customerServiceCapabilityToken = config.customerServiceCapabilityToken ?? ''
  if (customerServiceCapabilityToken.includes('\n') || customerServiceCapabilityToken.includes('\r')) {
    throw new Error('customer-service gateway customerServiceCapabilityToken must not contain line breaks')
  }
  return {
    provider: config.provider,
    model: config.model,
    serviceToken,
    maxBodyBytes: config.maxBodyBytes,
    pathPrefix,
    customerServiceBackendBaseUrl,
    customerServiceCapabilityToken,
    customerServiceToolTimeoutMs: config.customerServiceToolTimeoutMs ?? 5_000,
  }
}

function bearerToken(req: IncomingMessage): string | undefined {
  const raw = req.headers.authorization
  if (typeof raw !== 'string') return undefined
  const match = /^Bearer\s+([^\s]+)$/i.exec(raw.trim())
  return match?.[1]
}

function customerServiceCapabilityToken(req: IncomingMessage, config: ResolvedConfig): string | undefined {
  const raw = req.headers['x-agent-capability-token']
  if (Array.isArray(raw)) throw new HttpError(400, 'x-agent-capability-token must be a single value')
  if (raw !== undefined && typeof raw !== 'string') throw new HttpError(400, 'x-agent-capability-token must be a string')
  const forwarded = raw?.trim()
  if (forwarded !== undefined && forwarded === '') throw new HttpError(401, 'agent capability credential is empty')
  return forwarded ?? (config.customerServiceCapabilityToken === '' ? undefined : config.customerServiceCapabilityToken)
}

function sameSecret(supplied: string | undefined, expected: string): boolean {
  if (supplied === undefined) return false
  const suppliedBytes = Buffer.from(supplied)
  const expectedBytes = Buffer.from(expected)
  return suppliedBytes.length === expectedBytes.length && timingSafeEqual(suppliedBytes, expectedBytes)
}

function authorize(req: IncomingMessage, config: Config): void {
  if (config.serviceToken !== '' && !sameSecret(bearerToken(req), config.serviceToken)) {
    throw new HttpError(401, 'missing or invalid gateway credential')
  }
}

async function readJson(req: IncomingMessage, maxBytes: number): Promise<unknown> {
  const contentType = req.headers['content-type']
  if (typeof contentType !== 'string' || contentType.split(';', 1)[0]?.trim().toLowerCase() !== 'application/json') {
    throw new HttpError(415, 'content-type must be application/json')
  }
  const declared = req.headers['content-length']
  if (declared !== undefined && Number.isFinite(Number(declared)) && Number(declared) > maxBytes) {
    throw new HttpError(413, 'request body is too large')
  }
  const chunks: Buffer[] = []
  let received = 0
  for await (const chunk of req) {
    const candidate: unknown = chunk
    const buffer = typeof candidate === 'string'
      ? Buffer.from(candidate)
      : candidate instanceof Uint8Array
        ? Buffer.from(candidate)
        : (() => { throw new HttpError(400, 'request body contains an unsupported chunk') })()
    received += buffer.byteLength
    if (received > maxBytes) throw new HttpError(413, 'request body is too large')
    chunks.push(buffer)
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown
  } catch {
    throw new HttpError(400, 'request body is not valid JSON')
  }
}

function parseMessageRequest(value: unknown): MessageRequest {
  if (!isRecord(value) || typeof value.query !== 'string' || value.query.trim() === '') {
    throw new HttpError(400, 'query must be a non-empty string')
  }
  if (value.query.length > 16_000) throw new HttpError(413, 'query is too long')
  if (value.user !== undefined && typeof value.user !== 'string') throw new HttpError(400, 'user must be a string')
  if (value.conversation_id !== undefined && typeof value.conversation_id !== 'string') {
    throw new HttpError(400, 'conversation_id must be a string')
  }
  if (value.conversation_id !== undefined && value.conversation_id.length > 200) {
    throw new HttpError(400, 'conversation_id is too long')
  }
  if (value.inputs !== undefined && !isRecord(value.inputs)) throw new HttpError(400, 'inputs must be an object')
  const responseMode = value.response_mode
  if (responseMode !== undefined && responseMode !== 'blocking' && responseMode !== 'streaming') {
    throw new HttpError(400, 'response_mode must be blocking or streaming')
  }
  return {
    query: value.query,
    ...value.user === undefined ? {} : { user: value.user },
    ...value.conversation_id === undefined ? {} : { conversation_id: value.conversation_id },
    ...value.inputs === undefined ? {} : { inputs: value.inputs },
    ...responseMode === undefined ? {} : { response_mode: responseMode },
  }
}

function textFromContent(content: readonly { type: string; text?: string }[]): string {
  return content.flatMap(block => block.type === 'text' && typeof block.text === 'string' ? [block.text] : []).join('')
}

function textFromChunk(chunk: StreamChunk): string {
  return chunk.type === 'text-delta' ? chunk.text : ''
}

function collectTurnFacts(events: readonly SessionEvent[]): TurnFacts {
  const calls = new Map<string, ToolCallFact>()
  const callStartedAt = new Map<string, number>()
  let usage: unknown
  let handoff: string | undefined
  for (const event of events) {
    if (event.type === 'tool/call') {
      const fact: ToolCallFact = { name: event.data.name, outcome: 'success' }
      const callId = String(event.data.callId)
      calls.set(callId, fact)
      callStartedAt.set(callId, event.time)
      if (event.data.name.toLowerCase().includes('handoff')) handoff = event.data.name
    } else if (event.type === 'tool/result') {
      const source = event.data.message.source
      const callId = String(source.callId)
      const fact = calls.get(callId)
      if (fact !== undefined) {
        if (event.data.error !== undefined) fact.outcome = 'error'
        const startedAt = callStartedAt.get(callId)
        if (startedAt !== undefined) fact.latencyMs = Math.max(0, event.time - startedAt)
      }
    } else if (event.type === 'assistant/message' && event.data.usage !== undefined) {
      usage = event.data.usage
    }
  }
  return {
    toolCalls: [...calls.values()],
    ...usage === undefined ? {} : { usage },
    ...handoff === undefined ? {} : { handoff },
  }
}

function requestId(req: IncomingMessage): string {
  const forwarded = req.headers['x-request-id']
  if (typeof forwarded === 'string' && forwarded.trim() !== '') return forwarded.trim().slice(0, 128)
  return randomUUID()
}

function logTurn(
  ctx: Context,
  config: ResolvedConfig,
  requestIdValue: string,
  managed: ManagedSession,
  events: readonly SessionEvent[],
  startedAt: number,
): void {
  const facts = collectTurnFacts(events)
  ctx.logger.info(JSON.stringify({
    event: 'customer-service.turn',
    request_id: requestIdValue,
    session_id: String(managed.id),
    agent_id: String(managed.handle.agent.id),
    model: config.model,
    latency_ms: Date.now() - startedAt,
    token_usage: facts.usage ?? null,
    tool_calls: facts.toolCalls,
    tool_latency: facts.toolCalls.map(call => ({ name: call.name, latency_ms: call.latencyMs ?? null })),
    retrieval_count: facts.toolCalls.filter(call => call.name === 'search_knowledge').length,
    handoff: facts.handoff ?? null,
  }))
}

function writeJson(res: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body)
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(payload) })
  res.end(payload)
}

function writeSseHeaders(res: ServerResponse): void {
  res.writeHead(200, {
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-cache, no-transform',
    connection: 'keep-alive',
  })
  res.flushHeaders()
}

function writeSse(res: ServerResponse, event: unknown): void {
  res.write(`data: ${JSON.stringify(event)}\n\n`)
}

function sessionKey(request: MessageRequest): string | undefined {
  const value = request.conversation_id?.trim()
  return value === undefined || value === '' ? undefined : value
}

function newSessionId(): SessionId {
  return SessionId(randomUUID())
}

/** Register the trusted Backend HTTP/SSE seam. */
export function apply(ctx: Context, rawConfig: Config): void {
  const config = validateConfig(rawConfig)
  const agents = ctx.agents
  const webServer: WebServer = ctx.webServer
  const sessions = new Map<string, ManagedSession>()
  const loading = new Map<string, Promise<ManagedSession>>()
  let closed = false

  const assertSessionCapability = (managed: ManagedSession, capabilityToken: string | undefined): ManagedSession => {
    if (managed.capabilityToken !== capabilityToken) {
      throw new HttpError(401, 'conversation capability does not match the session')
    }
    return managed
  }

  const agentSetup = (capabilityToken: string | undefined, sessionId: string): AgentSetup | undefined => {
    // Guests may use the globally composed knowledge-search tool. Business
    // tools are mounted only when Java has supplied a user/session capability.
    if (config.customerServiceBackendBaseUrl === '' || capabilityToken === undefined) return undefined
    const scopedCapabilityToken = capabilityToken
    return (agentCtx) => {
      registerCustomerServiceTools(agentCtx, {
        backendBaseUrl: config.customerServiceBackendBaseUrl,
        capabilityToken: scopedCapabilityToken,
        sessionId,
        timeoutMs: config.customerServiceToolTimeoutMs,
      })
    }
  }

  const load = async (requested: string | undefined, capabilityToken: string | undefined): Promise<ManagedSession> => {
    // A request without a conversation id is a new identity. Do not collapse
    // concurrent first turns into one shared session.
    const key = requested ?? randomUUID()
    const existing = requested === undefined ? undefined : sessions.get(requested)
    if (existing !== undefined) return assertSessionCapability(existing, capabilityToken)
    const pending = loading.get(key)
    if (pending !== undefined) return assertSessionCapability(await pending, capabilityToken)
    const promise = (async (): Promise<ManagedSession> => {
      const id = requested === undefined ? newSessionId() : SessionId(requested)
      const setup = agentSetup(capabilityToken, String(id))
      let handle: AgentHandle
      if (requested === undefined) {
        const options = { agentOptions: { provider: config.provider, model: config.model }, sessionId: id }
        handle = setup === undefined ? await agents.create(options) : await agents.create({ ...options, setup })
      } else {
        const options = { resumeSessionId: id, agentOptions: { provider: config.provider, model: config.model } }
        handle = setup === undefined ? await agents.resume(options) : await agents.resume({ ...options, setup })
      }
      const managed: ManagedSession = {
        id,
        handle,
        ...capabilityToken === undefined ? {} : { capabilityToken },
        busy: false,
      }
      sessions.set(String(id), managed)
      return managed
    })()
    loading.set(key, promise)
    try {
      return await promise
    } finally {
      loading.delete(key)
    }
  }

  const route = async (req: IncomingMessage, res: ServerResponse, streaming: boolean): Promise<void> => {
    if (req.method !== 'POST') {
      res.setHeader('allow', 'POST')
      writeJson(res, 405, { error: 'method not allowed' })
      return
    }
    if (closed) throw new HttpError(503, 'customer-service gateway is shutting down')
    authorize(req, config)
    const currentRequestId = requestId(req)
    const capabilityToken = customerServiceCapabilityToken(req, config)
    const request = parseMessageRequest(await readJson(req, config.maxBodyBytes))
    const managed = await load(sessionKey(request), capabilityToken)
    if (managed.busy) {
      writeJson(res, 409, { error: 'conversation already has an active turn', conversation_id: String(managed.id) })
      return
    }
    managed.busy = true
    const turnStartedAt = Date.now()
    try {
      const before = managed.handle.agent.session.events.length
      const message = createUserMessage({ content: [{ type: 'text', text: request.query }], source: { kind: 'user' } })
      if (streaming) {
        writeSseHeaders(res)
        const streamState = { emittedText: false, ended: false }
        const streamingTools = new Map<string, string>()
        const finish = (event: unknown): void => {
          if (streamState.ended) return
          streamState.ended = true
          writeSse(res, event)
          res.end()
        }
        const offEvent = ctx.on('session/event', (session, event: SessionEvent) => {
          if (session !== managed.handle.agent.session) return
          if (event.type === 'assistant/chunk') {
            const text = textFromChunk(event.data.chunk)
            if (text !== '') {
              streamState.emittedText = true
              writeSse(res, { type: 'token', text, conversation_id: String(managed.id) })
            }
          } else if (event.type === 'assistant/message') {
            if (!streamState.emittedText) writeSse(res, {
              type: 'message',
              text: textFromContent(event.data.message.content),
              conversation_id: String(managed.id),
            })
            if (event.data.usage !== undefined) writeSse(res, { type: 'usage', usage: event.data.usage })
          } else if (event.type === 'tool/call') {
            streamingTools.set(String(event.data.callId), event.data.name)
            writeSse(res, { type: 'tool', name: event.data.name, outcome: 'started' })
          } else if (event.type === 'tool/result') {
            const source = event.data.message.source
            writeSse(res, {
              type: 'tool',
              name: streamingTools.get(String(source.callId)) ?? 'unknown',
              call_id: String(source.callId),
              outcome: event.data.error === undefined ? 'success' : 'error',
            })
          } else if (event.type === 'turn/end') {
            finish({ type: 'done', conversation_id: String(managed.id), reason: event.data.reason })
          }
        })
        const offError = ctx.on('agent/error', ({ agent, error }) => {
          if (agent !== managed.handle.agent) return
          finish({ type: 'error', error: error instanceof Error ? error.message : String(error) })
        })
        try {
          managed.handle.agent.followup(message)
          await managed.handle.agent.whenIdle()
          logTurn(ctx, config, currentRequestId, managed, managed.handle.agent.session.events.slice(before), turnStartedAt)
          if (!streamState.ended) {
            const assistant = managed.handle.agent.session.events.slice(before).findLast(event => event.type === 'assistant/message')
            finish({ type: 'done', conversation_id: String(managed.id), ...assistant?.type === 'assistant/message' ? { usage: assistant.data.usage } : {} })
          }
        } finally {
          offEvent()
          offError()
          if (!streamState.ended) res.end()
        }
        return
      }

      managed.handle.agent.followup(message)
      await managed.handle.agent.whenIdle()
      logTurn(ctx, config, currentRequestId, managed, managed.handle.agent.session.events.slice(before), turnStartedAt)
      const assistant = managed.handle.agent.session.events.slice(before).findLast(event => event.type === 'assistant/message')
      if (assistant?.type !== 'assistant/message') throw new HttpError(502, 'agent turn produced no assistant message')
      const facts = collectTurnFacts(managed.handle.agent.session.events.slice(before))
      const body: JsonResponse = {
        answer: textFromContent(assistant.data.message.content),
        conversation_id: String(managed.id),
        ...facts.usage === undefined ? {} : { usage: facts.usage },
        ...facts.toolCalls.length === 0 ? {} : { tool_calls: facts.toolCalls },
        ...facts.handoff === undefined ? {} : { handoff: facts.handoff },
      }
      writeJson(res, 200, body)
    } finally {
      managed.busy = false
    }
  }

  const blockingPath = `${config.pathPrefix}/messages`
  const streamingPath = `${config.pathPrefix}/messages/streaming`
  const handleRequest = (
    request: IncomingMessage,
    response: ServerResponse,
    streaming: boolean,
  ): Promise<void> => route(request, response, streaming).catch((error: unknown) => {
    const status = error instanceof HttpError ? error.status : 500
    const message = error instanceof HttpError ? error.message : 'customer-service gateway failed'
    if (!response.headersSent) writeJson(response, status, { error: message })
    else response.end()
    ctx.logger.warn(JSON.stringify({
      event: 'customer-service.request.error',
      request_id: requestId(request),
      status,
      exception: error instanceof Error ? error.name : 'UnknownError',
      message: error instanceof HttpError ? error.message : 'customer-service gateway failed',
    }))
  })
  const disposeBlocking = webServer.register({ kind: 'exact', path: blockingPath, handler: (req, res) => handleRequest(req, res, false) })
  const disposeStreaming = webServer.register({ kind: 'exact', path: streamingPath, handler: (req, res) => handleRequest(req, res, true) })

  ctx.effect(() => async () => {
    closed = true
    disposeBlocking()
    disposeStreaming()
    await Promise.all([...sessions.values()].map(session => session.handle.dispose()))
    sessions.clear()
  }, 'customer-service-gateway.lifecycle')
}

export default { name, inject, Config, apply }
