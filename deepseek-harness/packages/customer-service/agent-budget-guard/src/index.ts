import type { Context } from '@deepseek-ai/cordis'
import z from '@deepseek-ai/schemastery'
import type { PreStepDecision } from '@deepseek-ai/dsh-agent'
import type { PreToolDecision } from '@deepseek-ai/dsh-tools'

export const name = 'customer-service-budget-guard'
export const inject = ['tools']

export interface Config {
  maxStepsPerTurn: number
  maxToolCallsPerTurn: number
}

export const Config: z<Config> = z.object({
  maxStepsPerTurn: z.number().min(1).max(20).default(6),
  maxToolCallsPerTurn: z.number().min(1).max(40).default(8),
})

interface BudgetState {
  turn: number
  toolCalls: number
}

/** Pure state machine kept exported so hard-stop behavior has a deterministic unit test. */
export class AgentBudgetTracker {
  private readonly states = new WeakMap<object, BudgetState>()

  public constructor(private readonly config: Config) {}

  public allowStep(agent: object, turn: number, step: number): boolean {
    const current = this.states.get(agent)
    if (current === undefined || current.turn !== turn) {
      this.states.set(agent, { turn, toolCalls: 0 })
    }
    return step <= this.config.maxStepsPerTurn
  }

  public allowTool(agent: object): boolean {
    const state = this.states.get(agent)
    if (state === undefined) return false
    state.toolCalls += 1
    return state.toolCalls <= this.config.maxToolCallsPerTurn
  }
}

export function apply(ctx: Context, config: Config): void {
  const tracker = new AgentBudgetTracker(config)
  ctx.on('agent/pre-step', async ({ agent, turn, step }, next): Promise<PreStepDecision> => {
    if (!tracker.allowStep(agent, turn, step)) return { kind: 'reject' }
    return next()
  })
  ctx.on('tools/pre-execute', async (exec, next): Promise<PreToolDecision> => {
    if (exec.agent !== undefined && !tracker.allowTool(exec.agent)) {
      return {
        kind: 'deny',
        reason: 'AGENT_TOOL_BUDGET_EXCEEDED: stop and hand off to a human agent',
      }
    }
    return next()
  })
}
