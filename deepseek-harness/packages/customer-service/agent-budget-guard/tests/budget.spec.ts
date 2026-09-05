import { describe, expect, it } from 'vitest'
import { AgentBudgetTracker } from '../src/index.ts'

describe('AgentBudgetTracker', () => {
  it('hard-stops steps and tool calls at configured limits and resets on a new turn', () => {
    const tracker = new AgentBudgetTracker({ maxStepsPerTurn: 2, maxToolCallsPerTurn: 2 })
    const agent = {}

    expect(tracker.allowStep(agent, 1, 1)).toBe(true)
    expect(tracker.allowTool(agent)).toBe(true)
    expect(tracker.allowTool(agent)).toBe(true)
    expect(tracker.allowTool(agent)).toBe(false)
    expect(tracker.allowStep(agent, 1, 3)).toBe(false)

    expect(tracker.allowStep(agent, 2, 1)).toBe(true)
    expect(tracker.allowTool(agent)).toBe(true)
  })
})
