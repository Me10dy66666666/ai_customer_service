import { afterEach, describe, expect, it, vi } from 'vitest'
import { Context } from '@deepseek-ai/cordis'
import { CallId } from '@deepseek-ai/dsh-llm'
import SystemPrompt from '@deepseek-ai/dsh-system-prompt'
import ToolRuntime from '@deepseek-ai/dsh-tools'
import * as searchKnowledge from '../src/index.ts'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('search_knowledge service boundary', () => {
  it('injects the service token and clamps the result limit', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ sources: [] }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const ctx = new Context()
    await ctx.plugin(SystemPrompt)
    await ctx.plugin(ToolRuntime)
    searchKnowledge.apply(ctx, {
      dataPipelineBaseUrl: 'http://pipeline.test',
      serviceToken: 'pipeline-secret',
      timeoutMs: 5_000,
    })

    await ctx.tools.execute({
      callId: CallId('knowledge-search'),
      name: 'search_knowledge',
      arguments: { query: '退货规则', limit: 999 },
      signal: new AbortController().signal,
    })

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(init.headers).toMatchObject({ Authorization: 'Bearer pipeline-secret' })
    expect(init.body).toBe(JSON.stringify({ query: '退货规则', limit: 10 }))
  })
})
