/**
 * 防御性 Markdown → 安全 HTML 转换器。
 *
 * 只允许白名单标签，所有属性/标签未在白名单中会被剥除。
 * 该模块无外部依赖，保护嵌入方站点免受 XSS 攻击。
 */

const ALLOWED_TAGS = new Set([
  "b",
  "i",
  "em",
  "strong",
  "a",
  "p",
  "br",
  "ul",
  "ol",
  "li",
  "code",
  "pre",
  "blockquote",
  "h1",
  "h2",
  "h3",
  "h4",
  "h5",
  "h6",
  "table",
  "thead",
  "tbody",
  "tr",
  "th",
  "td",
  "hr",
  "span",
  "div",
]);

const ALLOWED_ATTRS: Record<string, Set<string>> = {
  a: new Set(["href", "title"]),
  span: new Set(["class"]),
  div: new Set(["class"]),
};

const ALLOWED_PROTOCOLS = new Set(["http:", "https:", "mailto:"]);

/** 简易 Markdown → 安全 HTML。只处理最常见语法，防止注入。 */
function markdownToHtml(raw: string): string {
  return (
    raw
      // 代码块 ```...```
      .replace(/```(\w*)\n([\s\S]*?)```/g, (_m, lang, code) => {
        const escaped = escapeHtml(String(code).trimEnd());
        return `<pre><code>${escaped}</code></pre>`;
      })
      // 行内代码 `...`
      .replace(/`([^`]+)`/g, (_m, code) => `<code>${escapeHtml(code)}</code>`)
      // 粗体 **...**
      .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
      // 斜体 *...*
      .replace(/\*(.+?)\*/g, "<em>$1</em>")
      // 链接 [text](url)
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_m, text, hrefStr) => {
        const href = escapeAttr(hrefStr);
        return `<a href="${href}">${escapeHtml(text)}</a>`;
      })
      // 无序列表项 - ...
      .replace(/^- (.+)$/gm, "<li>$1</li>")
      // 将连续 <li> 包裹为 <ul>（两次替换解决相邻问题）
      .replace(/((?:<li>.*<\/li>\n?)+)/g, "<ul>$1</ul>")
      // 换行 → <br>
      .replace(/\n\n/g, "<br><br>")
      .replace(/\n/g, "<br>")
  );
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function escapeAttr(value: string): string {
  return value.replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

function sanitizeHtml(rawHtml: string): string {
  const doc = new DOMParser().parseFromString(rawHtml, "text/html");

  function walk(node: Node): string | null {
    if (node.nodeType === Node.TEXT_NODE) {
      return node.textContent ?? "";
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return null;

    const el = node as Element;
    const tag = el.tagName.toLowerCase();

    // 不在白名单的标签：只保留其子内容
    if (!ALLOWED_TAGS.has(tag)) {
      let inner = "";
      for (let i = 0; i < el.childNodes.length; i++) {
        const childResult = walk(el.childNodes[i]);
        if (childResult !== null) inner += childResult;
      }
      return inner;
    }

    // 构建属性
    let attrs = "";
    const allowedAttrs = ALLOWED_ATTRS[tag];
    if (allowedAttrs) {
      for (const name of allowedAttrs) {
        const value = el.getAttribute(name);
        if (value === null) continue;
        // a 标签 href 需校验协议
        if (tag === "a" && name === "href") {
          if (!isSafeUrl(value)) continue;
        }
        attrs += ` ${name}="${escapeAttr(value)}"`;
      }
    }

    let children = "";
    for (let i = 0; i < el.childNodes.length; i++) {
      const childResult = walk(el.childNodes[i]);
      if (childResult !== null) children += childResult;
    }

    // 自闭合标签
    if (tag === "br" || tag === "hr") {
      return `<${tag}${attrs}>`;
    }

    return `<${tag}${attrs}>${children}</${tag}>`;
  }

  let result = "";
  for (let i = 0; i < doc.body.childNodes.length; i++) {
    const childResult = walk(doc.body.childNodes[i]);
    if (childResult !== null) result += childResult;
  }
  return result;
}

function isSafeUrl(url: string): boolean {
  const lower = url.trim().toLowerCase();
  if (lower.startsWith("javascript:") || lower.startsWith("data:")) {
    return false;
  }
  if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("mailto:")) {
    return true;
  }
  // 相对路径允许
  return !lower.includes(":");
}

/**
 * 将 Markdown 文本转为安全的 HTML 字符串。
 * 两步：Markdown → HTML → 白名单净化。
 */
export function renderMarkdown(raw: string): string {
  if (!raw) return "";
  const html = markdownToHtml(raw);
  return sanitizeHtml(html);
}

/**
 * 安全地生成唯一 ID。
 */
export function generateId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}
