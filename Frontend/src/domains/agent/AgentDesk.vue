<template>
  <div class="desk-shell">
    <aside class="desk-queue">
      <div class="queue-head">
        <h2 class="queue-title">待接入</h2>
        <div class="queue-head-right">
          <span class="queue-count" v-if="store.queueSize">{{ store.queueSize }}</span>
          <span class="queue-online" :class="{ off: !store.online }">{{ store.online ? '● 在线' : '● 离线' }}</span>
        </div>
      </div>

      <div class="queue-search-bar">
        <div class="queue-search-input-wrap">
          <label for="agent-search" class="sr-only">搜索会话</label>
          <svg class="queue-search-icon" width="14" height="14" viewBox="0 0 16 16" fill="none"><circle cx="7" cy="7" r="4.5" stroke="currentColor" stroke-width="1.5"/><path d="M10.5 10.5L14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          <input id="agent-search" name="agent-search" v-model="store.searchKeyword" type="text" class="queue-search-input" placeholder="搜索会话…" />
        </div>
      </div>
      <div class="queue-filter-bar">
        <button v-for="opt in statusFilterOptions" :key="opt.value" class="queue-filter-chip" :class="{ 'queue-filter-chip--active': store.statusFilter === opt.value }" @click="store.statusFilter = opt.value">
          {{ opt.label }}
        </button>
      </div>

      <div class="queue-body" v-if="store.filteredQueue.length">
        <div v-for="s in store.filteredQueue" :key="s.sessionId" class="queue-card"
             :class="[cardClass(s), { selected: s.sessionId === store.activeSessionId, completed: s.completed }]"
             :style="cardGradientStyle(s)"
             @click="s.completed ? store.viewHistory(s.sessionId) : store.claimSession(s.sessionId)">
          <div class="qc-top">
            <span class="qc-avatar">{{ s.userId ? '用户#' + s.userId : '游客' }}</span>
            <span class="qc-tag">{{ s.completed ? '已完成' : s.status === 'HUMAN' ? '已接入' : intentLabel(s.intent) }}</span>
            <span v-if="s.priority && !s.completed" class="priority-tag priority-tag-sm" :class="'priority-' + s.priority.toLowerCase()">{{ priorityLabel(s.priority) }}</span>
            <span v-else-if="!s.priority && !s.completed" class="priority-tag priority-tag-sm priority-pending">分析中…</span>
          </div>
          <p class="qc-preview">{{ s.lastMessage || '用户请求人工客服' }}</p>
          <div v-if="s.tags" class="qc-tags">
            <span v-for="tag in s.tags.split(',')" :key="tag" class="qc-tag-item">{{ tag.trim() }}</span>
          </div>
          <div class="qc-foot">
            <span class="qc-time">{{ waitTime(s.timestamp) }}</span>
            <span class="qc-estimate" v-if="s.estimatedWait && !s.completed">预计等待{{ formatWait(s.estimatedWait) }}</span>
          </div>
        </div>
      </div>

      <div class="queue-empty" v-else>
        <div class="empty-mark">—</div>
        <p v-if="store.searchKeyword || store.statusFilter !== 'all'">暂无匹配会话</p>
        <p v-else>暂无待接入会话</p>
        <div v-if="!store.connected" class="conn-status">
          <p class="queue-sub">WS 未连接</p>
          <p class="queue-sub" v-if="store.reconnectAttempts > 0">
            正在重连 ({{ store.reconnectAttempts }}/5)…
          </p>
          <button class="ctx-btn" style="margin-top: 8px" @click="store.connect()">
            重新连接
          </button>
        </div>
        <p class="queue-sub" v-else-if="!store.online">离线中</p>
      </div>
    </aside>

    <main class="desk-chat" :class="{ idle: !store.activeSessionId }">
      <template v-if="!store.activeSessionId">
        <div class="chat-idle">
          <span class="idle-icon">✦</span>
          <p class="idle-text">选择一个会话开始接待</p>
        </div>
      </template>

      <template v-else>
        <header class="chat-bar">
          <div class="chat-bar-left">
            <span class="chat-bar-name">会话 {{ store.activeSessionId?.substring(0, 12) + '…' }}</span>
            <span class="chat-bar-status" :class="store.viewOnly ? 'off' : 'on'">{{ store.viewOnly ? '● 查看历史' : '● 接待中' }}</span>
          </div>
          <div class="chat-bar-acts">
            <template v-if="store.viewOnly">
              <button class="bar-btn" @click="store.clearView()">退出查看</button>
            </template>
            <template v-else>
              <button class="bar-btn" @click="requestSatisfaction">评价</button>
              <button class="bar-btn" @click="store.transferToAi()">转回 AI</button>
              <button class="bar-btn" @click="showTransferDlg = true">转接</button>
              <button class="bar-btn bar-btn-end" @click="store.closeSession()">结束</button>
            </template>
          </div>
        </header>

        <div class="chat-thread" ref="threadRef">
          <div v-for="(m, i) in visibleChatMessages" :key="i"
               class="cm-row" :class="m.from === 'agent' ? 'cm-user' : 'cm-agent'">
            <div class="cm-bubble" :class="m.from === 'agent' ? 'cm-bub-user' : 'cm-bub-agent'">
              {{ m.content }}
            </div>
            <span class="cm-time">{{ m.time }}</span>
          </div>
        </div>

        <div class="chat-compose">
          <label for="agent-compose" class="sr-only">输入回复</label>
          <input id="agent-compose" name="agent-compose" v-model="composeText" @keyup.enter="sendMsg" placeholder="输入回复…"
                 class="compose-in" :disabled="store.viewOnly" />
          <button class="compose-btn" @click="sendMsg" :disabled="!composeText.trim() || store.viewOnly">发送</button>
        </div>
      </template>
    </main>

    <aside class="desk-context">
      <div class="ctx-tabs">
        <button class="ctx-tab" :class="{ active: contextTab === 'workorder' }" @click="contextTab = 'workorder'">工单</button>
        <button class="ctx-tab" :class="{ active: contextTab === 'history' }" @click="contextTab = 'history'">会话历史</button>
      </div>

      <template v-if="contextTab === 'workorder'">
        <div class="ctx-section">
          <h3 class="ctx-title">工单管理</h3>
          <button class="ctx-link" @click="$router.push('/admin/work-orders')">查看全部工单 →</button>
        </div>
        <div class="ctx-section">
          <h3 class="ctx-title">快捷提交工单</h3>
          <div class="ctx-field">
            <label class="sr-only" for="quick-wo-title">工单标题</label>
            <input id="quick-wo-title" name="quick-wo-title" v-model="quickWO.title" placeholder="工单标题" class="ctx-inp" />
          </div>
          <div class="ctx-field">
            <label class="sr-only">工单类型</label>
            <div class="kr-cat-dropdown" style="width:100%">
              <button class="kr-cat-selected" style="width:100%;text-align:left;display:flex;align-items:center;justify-content:space-between" @click.stop="quickWoTypeOpen = !quickWoTypeOpen">
                <span>{{ quickWO.type === '售前' ? '售前咨询' : '售后服务' }}</span><span>▼</span>
              </button>
              <div v-if="quickWoTypeOpen" class="kr-cat-menu" style="width:100%;min-width:auto">
                <div class="kr-cat-menu-item" :class="{ active: quickWO.type === '售前' }"
                     @click="quickWO.type = '售前'; quickWoTypeOpen = false">售前咨询</div>
                <div class="kr-cat-menu-item" :class="{ active: quickWO.type === '售后' }"
                     @click="quickWO.type = '售后'; quickWoTypeOpen = false">售后服务</div>
              </div>
            </div>
          </div>
          <div class="ctx-field">
            <label class="sr-only" for="quick-wo-desc">问题描述</label>
            <textarea id="quick-wo-desc" name="quick-wo-desc" v-model="quickWO.desc" rows="3" placeholder="问题描述" class="ctx-inp"></textarea>
          </div>
          <button class="ctx-btn" :disabled="!quickWO.title || submittingWO" @click="submitQuickWO">
            {{ submittingWO ? '提交中…' : '创建工单' }}
          </button>
        </div>
      </template>

      <template v-if="contextTab === 'history'">
        <div v-if="!store.activeSessionId" class="ctx-empty">选择左侧会话查看详情</div>
        <template v-else>
          <div v-if="store.sessionSummary" class="ctx-section ctx-summary">
            <div class="ctx-summary-head">
              <span class="ctx-summary-label">AI 总结</span>
              <span class="priority-tag" :class="'priority-' + store.sessionSummary.priority?.toLowerCase()">
                {{ priorityLabel(store.sessionSummary.priority) }}
              </span>
            </div>
            <p class="ctx-summary-text">{{ store.sessionSummary.content }}</p>
          </div>
          <div v-if="unifiedTimeline.length" class="ctx-section">
            <h3 class="ctx-title">聊天记录</h3>
            <div class="ctx-chat-log">
              <div v-for="(m, i) in unifiedTimeline" :key="i" class="ctx-log-row">
                <span class="ctx-log-sender" :class="'log-' + m.cssClass">{{ m.label }}</span>
                <span class="ctx-log-content">{{ m.content }}</span>
              </div>
            </div>
          </div>
          <div v-if="!store.sessionSummary && !unifiedTimeline.length" class="ctx-empty">暂无聊天记录</div>
        </template>
      </template>
    </aside>

    <Teleport to="body">
      <div v-if="showTransferDlg" class="overlay" @click.self="showTransferDlg = false">
        <div class="modal modal-sm">
          <h3 class="modal-title">转接会话</h3>
          <p class="modal-desc">输入目标客服 ID</p>
          <label for="transfer-target" class="sr-only">目标客服 ID</label>
          <input id="transfer-target" name="transfer-target" v-model="transferTargetId" type="number" placeholder="客服 ID" class="modal-input" />
          <div class="modal-acts">
            <button class="btn-ghost" @click="showTransferDlg = false">取消</button>
            <button class="btn-brand" @click="doTransfer" :disabled="!transferTargetId">确认转接</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onBeforeUnmount } from 'vue'

import { useAgentStore } from '@/shared/stores/agentStore'
import { useWorkOrderStore } from '@/shared/stores/workOrderStore'
import { useAuthStore } from '@/shared/stores/authStore'
import { useRouter } from 'vue-router'

const router = useRouter()
const store = useAgentStore()
const woStore = useWorkOrderStore()
const authStore = useAuthStore()
const composeText = ref('')
const threadRef = ref(null)
const showTransferDlg = ref(false)
const transferTargetId = ref(null)

const quickWO = reactive({ title: '', type: '售后', desc: '' })
const quickWoTypeOpen = ref(false)
const submittingWO = ref(false)
const contextTab = ref('history')

const intentFilterOptions = [
  { value: '售前', label: '售前' },
  { value: '售后', label: '售后' },
  { value: '投诉', label: '投诉' },
  { value: '建议', label: '建议' },
  { value: 'other', label: '其他' }
]

const statusFilterOptions = [
  { value: 'all', label: '全部' },
  { value: 'waiting', label: '待接入' },
  { value: 'active', label: '已接入' },
  { value: 'completed', label: '已完成' }
]

const visibleChatMessages = computed(() => {
  return store.activeMessages.filter(m => m.from !== 'system')
})

const intentLabel = (i) => i || '—'
const priorityLabel = (p) => {
  if (!p) return ''
  const map = { LOW: '低', MEDIUM: '中', HIGH: '高' }
  return map[p.toUpperCase()] || p
}
const senderLabel = (t) => {
  if (!t) return ''
  const map = { USER: '用户', AGENT: '客服', SYSTEM: '系统' }
  return map[t.toUpperCase()] || t
}
const isDifySummaryMessage = (m) => {
  if (!m || m.senderType !== 'SYSTEM' || !m.content) return false
  try {
    const parsed = JSON.parse(m.content)
    return parsed.priority && parsed.summary
  } catch {
    return false
  }
}

const stripThinkContent = (content) => {
  if (!content) return ''
  return content.replace(/<think>[\s\S]*?<\/think>/g, '').trim()
}

const parseTimestamp = (timeStr) => {
  if (!timeStr) return 0
  const parsed = new Date(timeStr).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

const unifiedTimeline = computed(() => {
  const items = []

  for (const m of store.sessionAiMessages) {
    const isUser = m.role === 'user'
    items.push({
      cssClass: isUser ? 'user' : 'ai',
      label: isUser ? '用户' : 'AI',
      content: stripThinkContent(m.content),
      sortTime: parseTimestamp(m.time)
    })
  }

  for (const m of store.sessionHistory) {
    if (isDifySummaryMessage(m)) continue
    const senderType = m.senderType?.toLowerCase() || 'system'
    const labelMap = { user: '用户', agent: '客服', system: '系统' }
    items.push({
      cssClass: senderType,
      label: labelMap[senderType] || senderType,
      content: m.content,
      sortTime: parseTimestamp(m.createTime)
    })
  }

  items.sort((a, b) => a.sortTime - b.sortTime)
  return items
})
const waitTime = (ts) => {
  if (!ts) return ''
  const s = Math.floor((Date.now() - ts) / 1000)
  return s < 60 ? `${s}秒前` : s < 3600 ? `${Math.floor(s / 60)}分钟前` : `${Math.floor(s / 3600)}小时前`
}
const formatWait = (sec) => {
  if (!sec) return ''
  if (sec < 60) return `${sec}秒`
  if (sec < 3600) return `${Math.round(sec / 60)}分钟`
  return `${Math.round(sec / 3600)}小时`
}

const sendMsg = () => {
  if (store.viewOnly) return
  const c = composeText.value.trim()
  if (!c) return
  store.sendMessage(c)
  composeText.value = ''
}

const doTransfer = () => {
  if (!transferTargetId.value) return
  store.transferToAgent(parseInt(transferTargetId.value))
  showTransferDlg.value = false
  transferTargetId.value = null
}

const requestSatisfaction = () => {
  store.requestSatisfaction()
}

const submitQuickWO = async () => {
  if (!quickWO.title) return
  submittingWO.value = true
  try {
    woStore.newWorkOrder.title = quickWO.title
    woStore.newWorkOrder.type = quickWO.type
    woStore.newWorkOrder.description = quickWO.desc
    woStore.newWorkOrder.sessionId = store.activeSessionId
    const entry = store.queue.find(q => q.sessionId === store.activeSessionId)
    const customerUserId = entry ? entry.userId : null
    const r = await woStore.submit({ userId: customerUserId, creatorAgentId: authStore.userId })
    if (r.success) { quickWO.title = ''; quickWO.desc = '' }
    else if (r.message) alert(r.message)
  } finally { submittingWO.value = false }
}

const slaNow = ref(Date.now())
let slaTimer = null

const cardUrgency = (entry) => {
  if (!entry || entry.completed || !entry.timestamp) return null
  const elapsed = (slaNow.value - entry.timestamp) / 1000
  const estimated = entry.estimatedWait || 300
  if (estimated <= 0) return null
  const remaining = Math.max(0, (estimated - elapsed) / estimated)
  let level
  if (remaining <= 0) level = 'breach'
  else if (remaining <= 0.15) level = 'critical'
  else if (remaining <= 0.25) level = 'warning'
  else if (remaining <= 0.50) level = 'caution'
  else level = 'healthy'
  return { level, remaining }
}

const cardClass = (entry) => {
  const urgency = cardUrgency(entry)
  return urgency ? `qc-sla--${urgency.level}` : ''
}

const slaLevelColors = {
  healthy: 'oklch(0.50 0.150 150)',
  caution: 'oklch(0.62 0.160 75)',
  warning: 'oklch(0.65 0.18 50)',
  critical: 'oklch(0.50 0.170 20)',
  breach: 'oklch(0.50 0.170 20)'
}

const cardGradientStyle = (entry) => {
  const urgency = cardUrgency(entry)
  if (!urgency) return {}
  const color = slaLevelColors[urgency.level] || slaLevelColors.healthy
  return { background: `linear-gradient(90deg, ${color}10 0%, ${color}04 30%, transparent 100%)` }
}

const onDocClick = (e) => {
  if (!quickWoTypeOpen.value) return
  if (e.target.closest('.kr-cat-dropdown')) return
  quickWoTypeOpen.value = false
}

onMounted(() => {
  store.connect().catch((err) => {
    if (import.meta.env.DEV) { console.error('Agent WebSocket 连接失败:', err.message || err) }
  })
  slaTimer = setInterval(() => slaNow.value = Date.now(), 1000)
  document.addEventListener('click', onDocClick)
})

onBeforeUnmount(() => {
  store.disconnect()
  if (slaTimer) { clearInterval(slaTimer); slaTimer = null }
  document.removeEventListener('click', onDocClick)
})
</script>

<style scoped>
.desk-shell { display: flex; height: 100%; overflow: hidden; gap: 1px; background: var(--border-light); }

.desk-queue {
  width: 280px; flex-shrink: 0; background: var(--surface);
  display: flex; flex-direction: column; overflow: hidden;
}
.queue-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--s-5) var(--s-5) var(--s-4); border-bottom: 1px solid var(--border-light); flex-shrink: 0;
}
.queue-title { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); }
.queue-head-right { display: flex; align-items: center; gap: var(--s-2); }
.queue-count {
  font-size: var(--text-2xs); font-weight: var(--weight-semibold); color: #fff;
  background: var(--agent); padding: 1px 8px; border-radius: var(--radius-full);
}
.queue-online {
  font-size: var(--text-2xs); font-weight: var(--weight-medium); color: var(--success);
}
.queue-online.off { color: var(--danger); }

.queue-search-bar {
  padding: var(--s-3) var(--s-4) var(--s-2); flex-shrink: 0;
}
.queue-search-input-wrap {
  display: flex; align-items: center; gap: var(--s-2);
  padding: var(--s-1) var(--s-3); border-radius: var(--radius-md);
  border: 1.5px solid var(--border); background: var(--base);
  transition: border-color var(--dur-fast);
}
.queue-search-input-wrap:focus-within { border-color: var(--agent); }
.queue-search-icon { color: var(--ink-muted); flex-shrink: 0; }
.queue-search-input {
  flex: 1; border: none; background: transparent; font-size: var(--text-xs);
  font-family: var(--font-body); color: var(--ink); outline: none; padding: var(--s-1) 0;
}
.queue-search-input::placeholder { color: var(--ink-muted); }

.queue-filter-bar {
  display: flex; gap: var(--s-1); padding: 0 var(--s-4) var(--s-3); flex-wrap: wrap; flex-shrink: 0;
}
.queue-filter-chip {
  padding: 1px 8px; border: 1px solid var(--border-light); border-radius: var(--radius-full);
  background: transparent; font-size: var(--text-3xs); font-family: var(--font-body);
  color: var(--ink-soft); cursor: pointer; transition: all var(--dur-fast);
}
.queue-filter-chip:hover { border-color: var(--agent); color: var(--agent); }
.queue-filter-chip--active {
  background: var(--agent-soft); border-color: var(--agent); color: var(--agent);
  font-weight: var(--weight-medium);
}

.queue-body { flex: 1; overflow-y: auto; padding: var(--s-3); display: flex; flex-direction: column; gap: var(--s-2); }
.queue-card {
  padding: var(--s-3); border-radius: var(--radius-lg); cursor: pointer;
  border: 1px solid var(--border-light); border-left: 3px solid var(--border-light);
  transition: all var(--dur-fast) var(--ease-soft);
}
.queue-card:hover:not(.completed), .queue-card.selected { border-color: var(--agent); border-left-color: var(--agent); box-shadow: var(--shadow-sm); }
.queue-card.completed:hover { border-color: var(--border); box-shadow: var(--shadow-xs); }
.queue-card.selected { background: var(--agent-soft); }
.queue-card.completed.selected { background: var(--agent-soft); opacity: 0.75; }
.queue-card.completed { cursor: pointer; opacity: 0.55; background: var(--base-alt); }

.qc-sla--healthy { border-left-color: oklch(0.50 0.150 150); }
.qc-sla--caution { border-left-color: oklch(0.62 0.160 75); }
.qc-sla--warning { border-left-color: oklch(0.65 0.18 50); }
.qc-sla--critical { border-left-color: oklch(0.50 0.170 20); }
.qc-sla--breach { border-left-color: oklch(0.50 0.170 20); animation: qc-sla-blink 0.8s ease-in-out infinite; }

@keyframes qc-sla-blink { 50% { border-left-color: oklch(0.50 0.170 20 / 0.3); } }

.qc-sla--healthy:hover:not(.completed), .qc-sla--healthy.selected { border-left-color: oklch(0.50 0.150 150); }
.qc-sla--caution:hover:not(.completed), .qc-sla--caution.selected { border-left-color: oklch(0.62 0.160 75); }
.qc-sla--warning:hover:not(.completed), .qc-sla--warning.selected { border-left-color: oklch(0.65 0.18 50); }
.qc-sla--critical:hover:not(.completed), .qc-sla--critical.selected { border-left-color: oklch(0.50 0.170 20); }
.qc-sla--breach:hover:not(.completed), .qc-sla--breach.selected { border-left-color: oklch(0.50 0.170 20); }
.qc-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--s-2); }
.qc-avatar { font-size: var(--text-xs); font-weight: var(--weight-medium); color: var(--agent); }
.qc-tag { font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 1px 8px; border-radius: var(--radius-full); background: var(--brand-pale); color: var(--brand); }
.qc-preview { font-size: var(--text-sm); color: var(--ink-soft); line-height: var(--leading-snug); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qc-tags {
  display: flex; flex-wrap: wrap; gap: var(--s-1); margin-top: var(--s-2);
}
.qc-tag-item {
  font-size: var(--text-3xs); font-weight: var(--weight-medium); color: var(--ink-muted);
  background: var(--base-alt); padding: 0 8px; border-radius: var(--radius-full);
  line-height: 1.6; white-space: nowrap;
}
.qc-foot { margin-top: var(--s-2); display: flex; justify-content: space-between; align-items: center; }
.qc-time { font-size: var(--text-3xs); color: var(--ink-muted); }
.qc-estimate { font-size: var(--text-3xs); color: var(--warning); font-weight: var(--weight-medium); }

.queue-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--ink-muted); }
.empty-mark { font-family: var(--font-heading); font-size: var(--text-3xl); font-weight: var(--weight-light); color: var(--border); margin-bottom: var(--s-3); }
.queue-empty p { font-size: var(--text-sm); }
.queue-sub { font-size: var(--text-2xs); color: var(--danger); }
.conn-status { display: flex; flex-direction: column; align-items: center; gap: 4px; }

.desk-chat { flex: 1; display: flex; flex-direction: column; background: var(--base); min-width: 0; }
.desk-chat.idle { align-items: center; justify-content: center; }

.chat-idle { text-align: center; }
.idle-icon { font-size: 3rem; color: var(--border); display: block; margin-bottom: var(--s-4); }
.idle-text { font-family: var(--font-heading); font-size: var(--text-xl); font-style: italic; color: var(--ink-muted); }

.chat-bar {
  height: 48px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 var(--s-5); background: var(--surface); border-bottom: 1px solid var(--border-light); flex-shrink: 0;
}
.chat-bar-left { display: flex; align-items: center; gap: var(--s-3); }
.chat-bar-name { font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--ink); }
.chat-bar-status { font-size: var(--text-2xs); color: var(--success); }
.chat-bar-status.off { color: var(--ink-muted); }
.chat-bar-acts { display: flex; gap: var(--s-2); }
.bar-btn {
  padding: var(--s-1) var(--s-3); border: 1px solid var(--border); border-radius: var(--radius-sm);
  background: var(--surface); font-size: var(--text-2xs); font-family: var(--font-body); color: var(--ink-soft); cursor: pointer;
  transition: all var(--dur-fast) var(--ease-soft);
}
.bar-btn:hover { border-color: var(--ink-muted); color: var(--ink); }
.bar-btn-end { color: var(--danger); }
.bar-btn-end:hover { border-color: var(--danger); }

.chat-thread { flex: 1; overflow-y: auto; padding: var(--s-4); display: flex; flex-direction: column; gap: var(--s-3); }
.cm-row { display: flex; flex-direction: column; max-width: 78%; animation: slideUp var(--dur-normal) var(--ease-soft); }
.cm-user { align-self: flex-end; align-items: flex-end; }
.cm-agent { align-self: flex-start; align-items: flex-start; }

.cm-bubble { padding: var(--s-3) var(--s-4); border-radius: var(--radius-lg); font-size: var(--text-sm); line-height: var(--leading-relaxed); word-break: break-word; }
.cm-bub-user { background: var(--brand); color: #fff; border-bottom-right-radius: var(--radius-sm); }
.cm-bub-agent { background: var(--surface); color: var(--ink); box-shadow: var(--shadow-xs); border-bottom-left-radius: var(--radius-sm); }
.cm-time { font-size: var(--text-3xs); color: var(--ink-muted); margin-top: 2px; }

@keyframes slideUp { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

.chat-compose { display: flex; gap: var(--s-3); padding: var(--s-4) var(--s-5); background: var(--surface); border-top: 1px solid var(--border-light); align-items: center; flex-shrink: 0; }
.compose-in { flex: 1; padding: var(--s-3) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-full); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base); transition: border-color var(--dur-fast) var(--ease-soft); }
.compose-in:focus { border-color: var(--agent); box-shadow: 0 0 0 3px var(--agent-soft); outline: none; }
.compose-in::placeholder { color: var(--ink-muted); }
.compose-btn {
  padding: var(--s-3) var(--s-6); border: none; border-radius: var(--radius-full);
  font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body);
  color: #fff; background: var(--agent); cursor: pointer;
  transition: background var(--dur-fast) var(--ease-soft);
}
.compose-btn:hover:not(:disabled) { background: oklch(0.40 0.110 310); }
.compose-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.desk-context {
  width: 272px; flex-shrink: 0; background: var(--surface);
  display: flex; flex-direction: column; gap: var(--s-4); padding: var(--s-4); overflow-y: auto;
}

.ctx-tabs {
  display: flex; gap: var(--s-1); flex-shrink: 0;
}
.ctx-tab {
  flex: 1; padding: var(--s-2) var(--s-3); border: none; border-radius: var(--radius-sm);
  font-size: var(--text-sm); font-weight: var(--weight-medium); font-family: var(--font-body);
  color: var(--ink-soft); background: var(--base-alt); cursor: pointer;
  transition: all var(--dur-fast) var(--ease-soft);
}
.ctx-tab.active {
  color: #fff; background: var(--agent);
}

.ctx-empty {
  text-align: center; font-size: var(--text-sm); color: var(--ink-muted); padding: var(--s-8) 0;
}

.ctx-summary {
  padding: var(--s-4); border-radius: var(--radius-lg); background: var(--brand-pale);
  border-left: 3px solid var(--brand);
}
.ctx-summary-head {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--s-2);
}
.ctx-summary-label {
  font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--brand-deep);
}
.ctx-summary-text {
  font-size: var(--text-sm); color: var(--ink-soft); line-height: var(--leading-relaxed); margin: 0;
  max-height: 200px; overflow-y: auto;
}

.ctx-chat-log {
  display: flex; flex-direction: column; gap: var(--s-2); max-height: 400px; overflow-y: auto;
}
.ctx-log-row {
  display: flex; gap: var(--s-2); align-items: flex-start; padding-bottom: var(--s-2); border-bottom: 1px solid var(--border-light);
}
.ctx-log-sender {
  font-size: var(--text-3xs); font-weight: var(--weight-semibold); padding: 1px 6px; border-radius: var(--radius-sm); flex-shrink: 0; min-width: 32px; text-align: center;
}
.log-user { background: var(--brand-pale); color: var(--brand-deep); }
.log-agent { background: var(--agent-soft); color: var(--agent); }
.log-ai { background: #e8f5e9; color: #2e7d32; }
.log-system { background: var(--base-alt); color: var(--ink-muted); }
.ctx-log-content {
  font-size: var(--text-xs); color: var(--ink); line-height: var(--leading-snug); word-break: break-word;
}

.priority-tag {
  font-size: var(--text-2xs); font-weight: var(--weight-semibold); padding: 1px 10px; border-radius: var(--radius-full);
}
.priority-tag-sm { font-size: var(--text-3xs); padding: 0 8px; }
.priority-low { background: #d4edda; color: #155724; }
.priority-medium { background: #fff3cd; color: #856404; }
.priority-high { background: #f8d7da; color: #721c24; }
.priority-pending { background: #e9ecef; color: #6c757d; }
.ctx-section { display: flex; flex-direction: column; gap: var(--s-3); }
.ctx-title { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); }
.ctx-link { font-size: var(--text-sm); color: var(--brand); text-decoration: none; font-weight: var(--weight-medium); cursor: pointer; background: none; border: none; text-align: left; font-family: var(--font-body); padding: 0; }
.ctx-link:hover { color: var(--brand-deep); }
.ctx-field { display: flex; flex-direction: column; }
.ctx-inp {
  width: 100%; padding: var(--s-2) var(--s-3); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base);
  resize: vertical; transition: border-color var(--dur-fast) var(--ease-soft);
}
.ctx-inp:focus { border-color: var(--agent); box-shadow: 0 0 0 3px var(--agent-soft); outline: none; }
.ctx-btn {
  padding: var(--s-2) var(--s-4); border: none; border-radius: var(--radius-md);
  font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body);
  color: #fff; background: var(--agent); cursor: pointer; align-self: flex-start;
  transition: background var(--dur-fast) var(--ease-soft);
}
.ctx-btn:hover:not(:disabled) { background: oklch(0.40 0.110 310); }
.ctx-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.overlay { position: fixed; inset: 0; background: oklch(0.18 0.018 210 / 0.35); display: flex; align-items: center; justify-content: center; z-index: 200; padding: var(--s-4); animation: fadeIn var(--dur-fast) var(--ease-soft); }
.modal { background: var(--surface); border-radius: var(--radius-2xl); padding: var(--s-8); width: 100%; max-width: 380px; box-shadow: var(--shadow-xl); animation: popIn var(--dur-normal) var(--ease-out); }
.modal-sm { max-width: 340px; }
.modal-title { font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-semibold); color: var(--ink); margin-bottom: var(--s-2); }
.modal-desc { font-size: var(--text-sm); color: var(--ink-soft); margin-bottom: var(--s-4); }
.modal-input { width: 100%; padding: var(--s-3) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-md); font-family: var(--font-body); color: var(--ink); background: var(--base); margin-bottom: var(--s-4); transition: border-color var(--dur-fast) var(--ease-soft); }
.modal-input:focus { border-color: var(--agent); box-shadow: 0 0 0 3px var(--agent-soft); outline: none; }
.modal-acts { display: flex; justify-content: flex-end; gap: var(--s-3); }
.btn-brand { padding: var(--s-2) var(--s-5); border: none; border-radius: var(--radius-md); font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body); color: #fff; background: var(--agent); cursor: pointer; transition: background var(--dur-fast) var(--ease-soft); }
.btn-brand:hover:not(:disabled) { background: oklch(0.40 0.110 310); }
.btn-brand:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-ghost { padding: var(--s-2) var(--s-5); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink-soft); background: var(--surface); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); }
.btn-ghost:hover { border-color: var(--ink-muted); color: var(--ink); }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes popIn { from { opacity: 0; transform: scale(0.94) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }

/* ── 分类下拉菜单 ── */
.kr-cat-dropdown { position: relative; display: inline-block; }
.kr-cat-selected {
  padding: var(--s-2) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); background: var(--surface); cursor: pointer; color: var(--ink);
  transition: border-color var(--dur-fast) var(--ease-soft);
}
.kr-cat-selected:hover { border-color: var(--agent); }
.kr-cat-menu {
  position: absolute; top: 100%; left: 0; z-index: 50;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  box-shadow: 0 4px 16px oklch(0.25 0.01 250 / 0.10); padding: var(--s-2); min-width: 200px; max-height: 280px; overflow-y: auto;
}
.kr-cat-menu-item {
  padding: var(--s-2) var(--s-3); font-size: var(--text-sm); cursor: pointer; border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: space-between;
}
.kr-cat-menu-item:hover { background: var(--agent-soft); }
.kr-cat-menu-item.active { color: var(--agent); font-weight: var(--weight-medium); }
</style>
