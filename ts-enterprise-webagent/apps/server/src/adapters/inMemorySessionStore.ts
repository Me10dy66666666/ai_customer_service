import type { SessionState, SessionStateStore, ChatMessageRecord } from "@enterprise-webagent/core";

/**
 * 内存会话状态存储（对齐 Backend RedisSessionStateAdapter）
 *
 * 用于开发/测试环境，生产环境应替换为 Redis 实现
 */
export class InMemorySessionStore implements SessionStateStore {
  private readonly sessions: Map<string, SessionState> = new Map();
  private readonly ttlTimers: Map<string, NodeJS.Timeout> = new Map();

  public constructor(private readonly defaultTtlSeconds: number = 86400) {}

  async getSession(sessionId: string): Promise<SessionState | null> {
    return this.sessions.get(sessionId) ?? null;
  }

  async saveSession(state: SessionState): Promise<void> {
    state.updatedAt = new Date().toISOString();
    this.sessions.set(state.sessionId, state);
    this.refreshTtl(state.sessionId);
  }

  async deleteSession(sessionId: string): Promise<void> {
    this.sessions.delete(sessionId);
    this.clearTtl(sessionId);
  }

  async setAiBlocked(sessionId: string, blocked: boolean): Promise<void> {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.aiBlocked = blocked;
      session.updatedAt = new Date().toISOString();
    }
  }

  async isAiBlocked(sessionId: string): Promise<boolean> {
    const session = this.sessions.get(sessionId);
    return session?.aiBlocked ?? false;
  }

  /**
   * 追加对话记录到会话
   */
  async appendMessage(
    sessionId: string,
    message: ChatMessageRecord
  ): Promise<void> {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.chatHistory.push(message);
      session.updatedAt = new Date().toISOString();
    }
  }

  /**
   * 获取所有会话（供管理界面使用）
   */
  getAllSessions(): SessionState[] {
    return Array.from(this.sessions.values());
  }

  private refreshTtl(sessionId: string): void {
    this.clearTtl(sessionId);
    const timer = setTimeout(() => {
      this.sessions.delete(sessionId);
      this.ttlTimers.delete(sessionId);
    }, this.defaultTtlSeconds * 1000);
    this.ttlTimers.set(sessionId, timer);
  }

  private clearTtl(sessionId: string): void {
    const timer = this.ttlTimers.get(sessionId);
    if (timer) {
      clearTimeout(timer);
      this.ttlTimers.delete(sessionId);
    }
  }
}
