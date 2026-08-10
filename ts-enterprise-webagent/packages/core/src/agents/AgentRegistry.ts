import type { AgentMetadata, AgentMetrics } from "@enterprise-webagent/shared";
import type { IAgent } from "./IAgent.js";

/**
 * Agent 注册中心
 *
 * 职责：
 * 1. 管理所有已接入 Agent 的生命周期（注册、注销、启用、禁用）
 * 2. 统一收集各 Agent 的运行指标
 * 3. 提供 Agent 发现与路由能力
 */
export class AgentRegistry {
  private readonly agents: Map<string, IAgent> = new Map();
  private readonly metricsHistory: Map<string, AgentMetrics[]> = new Map();

  /**
   * 注册 Agent
   */
  public register(agent: IAgent): void {
    const id = agent.metadata.id;
    if (this.agents.has(id)) {
      throw new Error(`Agent with id "${id}" is already registered`);
    }
    this.agents.set(id, agent);
  }

  /**
   * 注销 Agent
   */
  public unregister(agentId: string): void {
    if (!this.agents.has(agentId)) {
      throw new Error(`Agent with id "${agentId}" is not registered`);
    }
    this.agents.delete(agentId);
  }

  /**
   * 获取指定 Agent
   */
  public get(agentId: string): IAgent | undefined {
    return this.agents.get(agentId);
  }

  /**
   * 获取所有已注册 Agent
   */
  public getAll(): IAgent[] {
    return Array.from(this.agents.values());
  }

  /**
   * 获取所有已启用 Agent
   */
  public getEnabled(): IAgent[] {
    return this.getAll().filter((agent) => agent.metadata.enabled);
  }

  /**
   * 按类型获取 Agent
   */
  public getByType(type: AgentMetadata["type"]): IAgent[] {
    return this.getAll().filter((agent) => agent.metadata.type === type);
  }

  /**
   * 获取所有 Agent 的元信息列表（供管理界面使用）
   */
  public getAllMetadata(): AgentMetadata[] {
    return this.getAll().map((agent) => agent.metadata);
  }

  /**
   * 获取所有 Agent 的运行指标快照（供管理界面使用）
   */
  public getAllMetrics(): AgentMetrics[] {
    return this.getAll().map((agent) => agent.getMetrics());
  }

  /**
   * 健康检查所有 Agent
   */
  public async healthCheckAll(): Promise<Map<string, { healthy: boolean; reason?: string }>> {
    const results = new Map<string, { healthy: boolean; reason?: string }>();
    const promises = this.getAll().map(async (agent) => {
      const result = await agent.healthCheck();
      results.set(agent.metadata.id, result);
    });
    await Promise.all(promises);
    return results;
  }

  /**
   * 周期性采集指标（供定时任务调用）
   */
  public collectMetrics(): void {
    for (const agent of this.getAll()) {
      const metrics = agent.getMetrics();
      const history = this.metricsHistory.get(agent.metadata.id) ?? [];
      history.push({ ...metrics });
      // 保留最近 100 条记录
      if (history.length > 100) {
        history.shift();
      }
      this.metricsHistory.set(agent.metadata.id, history);
    }
  }

  /**
   * 获取指定 Agent 的历史指标
   */
  public getMetricsHistory(agentId: string): AgentMetrics[] {
    return this.metricsHistory.get(agentId) ?? [];
  }

  /**
   * Agent 总数
   */
  public get count(): number {
    return this.agents.size;
  }

  /**
   * 已启用 Agent 总数
   */
  public get enabledCount(): number {
    return this.getEnabled().length;
  }
}
