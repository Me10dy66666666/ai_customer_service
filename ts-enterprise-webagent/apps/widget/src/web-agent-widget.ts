import type { AgentMessage, WidgetConfig, WorkOrderAction } from "./lib/types";
import { sendChatMessage } from "./lib/api";
import { renderMarkdown, generateId } from "./lib/sanitizer";

/* ── 常量 ── */
const STYLES = /* css */ `
:host {
  --wa-brand: #4c6ef5;
  --wa-brand-deep: #3b5bdb;
  --wa-bg: #f8f9fa;
  --wa-surface: #ffffff;
  --wa-ink: #212529;
  --wa-ink-soft: #868e96;
  --wa-border: #dee2e6;
  --wa-radius: 12px;

  display: block;
  width: 100%;
  max-width: 420px;
  height: 600px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-size: 14px;
  color: var(--wa-ink);
  box-sizing: border-box;
}

*,
*::before,
*::after {
  box-sizing: inherit;
}

/* ── 容器 ── */
.container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--wa-bg);
  border: 1px solid var(--wa-border);
  border-radius: var(--wa-radius);
  overflow: hidden;
}

/* ── 头部 ── */
.header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--wa-brand);
  color: #fff;
  flex-shrink: 0;
}
.header-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255,255,255,0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
}
.header-title {
  font-weight: 600;
  font-size: 15px;
}
.header-sub {
  font-size: 11px;
  opacity: 0.75;
}

/* ── 消息区 ── */
.thread {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  scroll-behavior: smooth;
}
.thread-empty {
  margin: auto;
  text-align: center;
  color: var(--wa-ink-soft);
  font-size: 13px;
}

.msg {
  display: flex;
  flex-direction: column;
  max-width: 85%;
  animation: fadeUp 0.25s ease-out;
}
.msg-user {
  align-self: flex-end;
  align-items: flex-end;
}
.msg-agent {
  align-self: flex-start;
  align-items: flex-start;
}
.msg-system {
  align-self: center;
}

.bubble {
  padding: 8px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}
.bubble-user {
  background: var(--wa-brand);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble-agent {
  background: var(--wa-surface);
  color: var(--wa-ink);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

/* agent bubble 内 Markdown 样式 */
.bubble-agent p {
  margin: 4px 0;
}
.bubble-agent p:first-child {
  margin-top: 0;
}
.bubble-agent code {
  background: #e9ecef;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.bubble-agent pre {
  background: #e9ecef;
  padding: 8px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 12px;
}
.bubble-agent pre code {
  background: none;
  padding: 0;
}
.bubble-agent ul,
.bubble-agent ol {
  padding-left: 18px;
  margin: 4px 0;
}
.bubble-agent table {
  border-collapse: collapse;
  width: 100%;
  font-size: 12px;
}
.bubble-agent th,
.bubble-agent td {
  border: 1px solid var(--wa-border);
  padding: 4px 6px;
  text-align: left;
}

.msg-system-text {
  font-size: 11px;
  color: var(--wa-ink-soft);
  background: #e9ecef;
  padding: 3px 10px;
  border-radius: 20px;
}

/* ── 工单按钮 ── */
.wo-action {
  margin-top: 6px;
  padding: 8px 0;
  text-align: center;
}
.wo-btn {
  padding: 6px 16px;
  border: 1.5px solid var(--wa-brand);
  border-radius: 20px;
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  color: var(--wa-brand);
  cursor: pointer;
  transition: all 0.15s;
}
.wo-btn:hover {
  background: var(--wa-brand);
  color: #fff;
}

/* ── 输入区 ── */
.composer {
  display: flex;
  gap: 6px;
  padding: 10px 12px;
  background: var(--wa-surface);
  border-top: 1px solid var(--wa-border);
  flex-shrink: 0;
}
.composer-input {
  flex: 1;
  min-width: 0;
  padding: 8px 14px;
  border: 1.5px solid var(--wa-border);
  border-radius: 20px;
  font-size: 13px;
  font-family: inherit;
  color: var(--wa-ink);
  background: var(--wa-bg);
  outline: none;
  transition: border-color 0.15s;
}
.composer-input:focus {
  border-color: var(--wa-brand);
}
.composer-input::placeholder {
  color: var(--wa-ink-soft);
}
.composer-input:disabled {
  opacity: 0.5;
}
.composer-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 20px;
  background: var(--wa-brand);
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  color: #fff;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
  flex-shrink: 0;
}
.composer-btn:hover:not(:disabled) {
  background: var(--wa-brand-deep);
}
.composer-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ── 加载 ── */
.loading-dots {
  display: inline-flex;
  gap: 3px;
  padding: 4px 0;
}
.loading-dots span {
  width: 6px;
  height: 6px;
  background: var(--wa-ink-soft);
  border-radius: 50%;
  animation: dotPulse 1.4s infinite ease-in-out both;
}
.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes dotPulse {
  0%, 80%, 100% { transform: scale(0); opacity: 0.3; }
  40% { transform: scale(1); opacity: 1; }
}
`;

/* ── HTML 模板 ── */
const TEMPLATE = /* html */ `
<div class="container">
  <div class="header">
    <div class="header-avatar">AI</div>
    <div>
      <div class="header-title">智能客服</div>
      <div class="header-sub">AI 助手 7×24h</div>
    </div>
  </div>
  <div class="thread" id="thread" role="log" aria-live="polite"></div>
  <div class="composer">
    <input class="composer-input" id="input" type="text" placeholder="输入你的问题…" autocomplete="off" />
    <button class="composer-btn" id="sendBtn" type="button">发送</button>
  </div>
</div>
`;

/* ── Web Component ── */
export class WebAgentWidget extends HTMLElement {
  static elementName = "web-agent-widget";

  /* 配置 */
  private config: WidgetConfig = {};

  /* DOM 引用 */
  private threadEl!: HTMLElement;
  private inputEl!: HTMLInputElement;
  private sendBtn!: HTMLButtonElement;

  /* 状态 */
  private messages: AgentMessage[] = [];
  private loading = false;
  private history: string[] = [];
  private mounted = false;

  constructor() {
    super();
    this.attachShadow({ mode: "open" });
  }

  /* ─── 生命周期 ─── */

  connectedCallback(): void {
    this.readConfig();
    this.renderInitial();
    this.bindEvents();
    this.mounted = true;
  }

  disconnectedCallback(): void {
    this.mounted = false;
  }

  /* ─── 公开方法 ─── */

  /** 通过 JS 更新配置（可多次调用）。 */
  updateConfig(partial: Partial<WidgetConfig>): void {
    this.config = { ...this.config, ...partial };
    const color = this.config.themeColor;
    if (color) {
      this.style.setProperty("--wa-brand", color);
    }
    if (this.config.placeholder && this.inputEl) {
      this.inputEl.placeholder = this.config.placeholder;
    }
  }

  /** 外部注入消息。 */
  addMessage(content: string, role: AgentMessage["role"]): void {
    const msg: AgentMessage = {
      id: generateId(),
      role,
      content,
      timestamp: Date.now(),
    };
    this.messages.push(msg);
    if (this.mounted) this.renderMessages();
  }

  /** 清空消息并重置上下文。 */
  clearMessages(): void {
    this.messages = [];
    this.history = [];
    if (this.mounted) this.renderMessages();
  }

  /* ─── 内部 ─── */

  private readConfig(): void {
    this.config = {
      apiEndpoint:
        this.getAttribute("api-endpoint") ??
        "http://localhost:3400/api/v1/agent/customerService",
      placeholder: this.getAttribute("placeholder") ?? "输入你的问题…",
      welcomeMessage: this.getAttribute("welcome-message") ?? undefined,
      userType: Number(this.getAttribute("user-type")) || 0,
      themeColor: this.getAttribute("theme-color") ?? undefined,
      historyOrders: this.getAttribute("history-orders") ?? "",
    };
    const color = this.config.themeColor;
    if (color) {
      this.style.setProperty("--wa-brand", color);
    }
  }

  private renderInitial(): void {
    const styleSheet = new CSSStyleSheet();
    styleSheet.replaceSync(STYLES);
    this.shadowRoot!.adoptedStyleSheets = [styleSheet];
    this.shadowRoot!.innerHTML = TEMPLATE;

    this.threadEl = this.shadowRoot!.getElementById("thread")!;
    this.inputEl = this.shadowRoot!.getElementById("input") as HTMLInputElement;
    this.sendBtn = this.shadowRoot!.getElementById("sendBtn") as HTMLButtonElement;

    if (this.config.placeholder) {
      this.inputEl.placeholder = this.config.placeholder;
    }

    // 欢迎消息
    if (this.config.welcomeMessage) {
      this.addMessage(this.config.welcomeMessage, "agent");
    }

    this.renderMessages();
  }

  private bindEvents(): void {
    this.sendBtn.addEventListener("click", () => this.handleSend());
    this.inputEl.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        this.handleSend();
      }
    });
  }

  private handleSend(): void {
    if (this.loading) return;
    const content = this.inputEl.value.trim();
    if (!content) return;

    this.inputEl.value = "";
    this.addMessage(content, "user");
    this.history.push(content);
    this.callAgent(content);
  }

  private async callAgent(userInput: string): Promise<void> {
    const endpoint =
      this.config.apiEndpoint ??
      "http://localhost:3400/api/v1/agent/customerService";

    this.setLoading(true);

    try {
      const response = await sendChatMessage(endpoint, {
        user_input: userInput,
        history: this.history.slice(-10),
        userType: this.config.userType ?? 0,
      });

      const agentMsg: AgentMessage = {
        id: generateId(),
        role: "agent",
        content: response.reply,
        timestamp: Date.now(),
        action: response.action ?? null,
      };

      this.messages.push(agentMsg);
      this.renderMessages();
    } catch (err) {
      const errorContent =
        err instanceof Error
          ? `抱歉，服务暂时不可用：${err.message}`
          : "抱歉，服务暂时不可用，请稍后重试。";
      this.messages.push({
        id: generateId(),
        role: "system",
        content: errorContent,
        timestamp: Date.now(),
      });
      this.renderMessages();
    } finally {
      this.setLoading(false);
    }
  }

  private setLoading(loading: boolean): void {
    this.loading = loading;
    this.sendBtn.disabled = loading;
    this.inputEl.disabled = loading;
    if (loading) {
      this.renderLoading();
    }
  }

  /* ─── 渲染 ─── */

  private renderMessages(): void {
    if (!this.threadEl) return;

    const fragments: string[] = [];

    if (this.messages.length === 0) {
      fragments.push(
        `<div class="thread-empty">有什么可以帮您？</div>`,
      );
    }

    for (const msg of this.messages) {
      if (msg.role === "system") {
        fragments.push(`
          <div class="msg msg-system">
            <div class="msg-system-text">${this.escapeContent(msg.content)}</div>
          </div>
        `);
        continue;
      }

      const isUser = msg.role === "user";
      const htmlContent = isUser
        ? this.escapeContent(msg.content)
        : renderMarkdown(msg.content);

      fragments.push(`
        <div class="msg ${isUser ? "msg-user" : "msg-agent"}">
          <div class="bubble ${isUser ? "bubble-user" : "bubble-agent"}">
            ${htmlContent}
          </div>
          ${this.renderWorkOrderAction(msg)}
        </div>
      `);
    }

    if (this.loading) {
      fragments.push(`
        <div class="msg msg-agent">
          <div class="bubble bubble-agent">
            <div class="loading-dots"><span></span><span></span><span></span></div>
          </div>
        </div>
      `);
    }

    this.threadEl.innerHTML = fragments.join("");
    this.threadEl.scrollTop = this.threadEl.scrollHeight;
  }

  private renderLoading(): void {
    this.renderMessages();
  }

  private renderWorkOrderAction(msg: AgentMessage): string {
    if (!msg.action || msg.action.action !== "create_work_order") return "";

    const { title, description, type } = msg.action.data;
    return `
      <div class="wo-action">
        <button class="wo-btn" data-wo-action="create"
                data-wo-title="${this.escapeAttr(title)}"
                data-wo-desc="${this.escapeAttr(description)}"
                data-wo-type="${this.escapeAttr(type)}">
          📋 一键提交工单
        </button>
      </div>
    `;
  }

  /* ─── 安全工具 ─── */

  private escapeContent(text: string): string {
    return text
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  private escapeAttr(value: string): string {
    return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;");
  }
}
