/**
 * Model-facing `search_knowledge` tool: queries the data-pipeline vector search endpoint and
 * returns knowledge sources with citations. Named exports preserve Loader injection metadata.
 * @module @deepseek-ai/dsh-tool-search-knowledge
 */

import type { Context } from '@deepseek-ai/cordis'
import z from '@deepseek-ai/schemastery'
import { defineTool } from '@deepseek-ai/dsh-tools'

export const name = 'tool-search-knowledge'
export const inject = ['tools']

/** Model-facing tool configuration. */
export interface Config {
  /** Base URL of the data-pipeline service, e.g. http://localhost:3002. */
  dataPipelineBaseUrl: string
  /** Service credential injected by the host; it is never exposed to the model schema. */
  serviceToken: string
  /** Cooperative HTTP deadline enforced by the DSH timeout policy. */
  timeoutMs: number
}

/** Schemastery configuration for the tool consumer. */
export const Config: z<Config> = z.object({
  dataPipelineBaseUrl: z.string().required(),
  serviceToken: z.string().required(),
  timeoutMs: z.number().min(100).max(30_000).default(5_000),
})

/** One knowledge source as returned by the data-pipeline /search endpoint. */
interface KnowledgeSource {
  id: string
  title: string
  excerpt: string
  sourceType: 'knowledge_base' | 'policy' | 'faq'
  metadata: Record<string, string>
}

interface SearchResponse {
  sources?: KnowledgeSource[]
}

const SOURCE_TYPES = ['knowledge_base', 'policy', 'faq'] as const

/**
 * Register the `search_knowledge` tool on `ctx.tools`.
 * @param ctx - registrant context carrying the tool registry.
 * @param config - deployment's data-pipeline base URL.
 */
export function apply(ctx: Context, config: Config): void {
  ctx.tools.register(defineTool({
    name: 'search_knowledge',
    description:
      'Search the customer-service knowledge base for semantically relevant content. '
      + 'Use it to answer product, policy, and FAQ questions with cited sources. '
      + 'Returns a list of sources, each with a title and a text excerpt.',
    parameters: {
      query: {
        type: 'string',
        required: true,
        description: 'The search query, phrased as the customer question.',
      },
      limit: {
        type: 'integer',
        description: 'Maximum number of sources to return. Defaults to 5.',
      },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          sources: {
            type: 'array',
            required: true,
            items: {
              type: 'object',
              additionalProperties: false,
              properties: {
                id: { type: 'string', required: true },
                title: { type: 'string', required: true },
                excerpt: { type: 'string', required: true },
                sourceType: {
                  type: 'string',
                  required: true,
                  enum: [...SOURCE_TYPES],
                },
              },
            },
          },
        },
      },
      render: (_args, value) => [{
        type: 'text',
        text: value.sources.length === 0
          ? 'No relevant knowledge found.'
          : value.sources.map(s => `- ${s.title}: ${s.excerpt}`).join('\n'),
      }],
    },
    timeoutMs: config.timeoutMs,
    async execute(args, exec) {
      const limit = Math.min(Math.max(args.limit ?? 5, 1), 10)
      const response = await fetch(`${config.dataPipelineBaseUrl}/search`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${config.serviceToken}`,
        },
        body: JSON.stringify({ query: args.query, limit }),
        signal: exec.signal,
      })

      if (!response.ok) {
        throw new Error(`data-pipeline search failed: HTTP ${response.status}`)
      }

      const data = (await response.json()) as SearchResponse
      const sources = (data.sources ?? []).map(s => ({
        id: s.id,
        title: s.title,
        excerpt: s.excerpt,
        sourceType: s.sourceType,
      }))

      return { sources }
    },
    presentCall: args => ({ card: 'generic', title: 'Search knowledge', kind: 'search', rawInput: args.query }),
  }))
}
