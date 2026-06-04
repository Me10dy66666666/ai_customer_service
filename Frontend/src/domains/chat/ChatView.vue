<template>
  <div class="chat-shell">
    <div class="chat-body">
      <aside class="user-sidebar">
        <div class="user-sidebar-brand">
          <div class="user-avatar">{{ auth.username ? auth.username[0] : 'U' }}</div>
          <div class="user-info" v-if="auth.isLoggedIn.value">
            <span class="user-name">{{ auth.username }}</span>
            <span class="user-role">用户</span>
          </div>
          <div class="user-info" v-else>
            <span class="user-name">未登录</span>
            <router-link to="/login" class="user-login-link">去登录</router-link>
          </div>
        </div>
        <nav class="user-nav">
          <button class="user-nav-item" :class="{ on: view === 'chat' }" @click="switchView('chat')">
            <span class="user-nav-icon">💬</span> 对话
          </button>
          <button class="user-nav-item" :class="{ on: view === 'orders' }" @click="switchView('orders')">
            <span class="user-nav-icon">📦</span> 订单
          </button>
          <button class="user-nav-item" :class="{ on: view === 'work-orders' }" @click="switchView('work-orders')">
            <span class="user-nav-icon">📋</span> 工单
            <span class="user-nav-badge" v-if="wo.hasUnreadWoUpdate">●</span>
          </button>
        </nav>
        <div class="user-sidebar-footer" v-if="auth.isLoggedIn.value">
          <button class="user-logout" @click="handleLogout">退出登录</button>
        </div>
      </aside>

      <main class="stage">
        <!-- ── Chat ── -->
        <div v-if="view === 'chat'" class="chat-flow">
          <div class="thread" ref="threadEl">
            <div v-if="visibleMessages.length === 0" class="thread-empty">
              <p class="empty-greet" v-if="auth.isLoggedIn.value">{{ auth.username }}，有什么可以帮您？</p>
              <p class="empty-greet" v-else>有什么可以帮您？</p>
            </div>
            <div v-for="(m, i) in visibleMessages" :key="i"
                 class="thread-msg"
                 :class="m.isUser ? 'by-user' : m.isSystem ? 'by-system' : 'by-ai'"
                 :style="{ animationDelay: '0ms' }">
              <div v-if="m.isSystem" class="msg-system">{{ m.content }}</div>
              <template v-else>
                <div class="msg-bubble" :class="m.isUser ? 'bub-user' : 'bub-ai'">
                  <template v-if="m.isUser">{{ m.content }}</template>
                  <div v-else v-html="chat.renderMarkdown(m.content)"></div>
                </div>
                <div v-if="!m.isUser && !chat.loading && i === visibleMessages.length - 1" class="msg-vote">
                  <button class="vote-btn" :class="{ on: m.feedback === 'up' }" @click="handleFeedback(i, 'up')">有帮助</button>
                  <button class="vote-btn" :class="{ on: m.feedback === 'down' }" @click="handleFeedback(i, 'down')">没有帮助</button>
                </div>
              </template>
            </div>
          </div>
          <div class="composer">
            <label for="chat-input" class="sr-only">输入你的问题</label>
            <input id="chat-input" name="chat-input" v-model="newMsg" @keyup.enter="handleSend"
                   placeholder="输入你的问题…" :disabled="chat.loading" class="compose-input" />
            <button class="btn-brand" @click="handleSend" :disabled="chat.loading || !newMsg.trim()">发送</button>
            <button v-if="auth.isLoggedIn.value && !chat.humanSessionActive" class="btn-ghost"
                    @click="transferToHuman"
                    :disabled="chat.transferring">转人工</button>
            <button v-if="auth.isLoggedIn.value && chat.humanSessionActive" class="btn-back-to-ai"
                    @click="endHumanSession">转回 AI</button>
            <span v-if="chat.transferring" class="transfer-hint">排队中 (#{{ chat.waitPosition }}) 预计 {{ chat.waitTimeDisplay }}
              <button class="cancel-wait-btn" @click="cancelWaiting">取消排队</button>
            </span>
          </div>
        </div>

        <!-- ── Orders ── -->
        <div v-if="view === 'orders'" class="list-view">
          <div class="list-head">
            <h2 class="list-title">历史订单</h2>
            <button class="btn-brand btn-compact" @click="handleSyncOrders" :disabled="order.syncing">
              {{ order.syncing ? '同步中…' : '同步订单' }}
            </button>
          </div>
          <div v-if="order.loading" class="list-hint">加载中…</div>
          <div v-else-if="!order.orders.length" class="list-hint">暂无订单数据</div>
          <div v-else class="record-stack">
            <div v-for="o in order.orders" :key="o.id" class="record-card">
              <div class="rec-row">
                <span class="rec-no">{{ o.orderNo }}</span>
                <span class="pill" :class="o.orderStatus === '已完成' ? 'pill-ok' : 'pill-in'">{{ o.orderStatus }}</span>
              </div>
              <div class="rec-body">
                <h3 class="rec-title">{{ o.productName }}</h3>
                <p class="rec-meta">{{ o.productModel }}</p>
                <div class="rec-foot">
                  <span class="rec-price">¥{{ o.totalAmount }}</span>
                  <span class="rec-date">{{ new Date(o.createTime).toLocaleDateString() }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ── Work Orders ── -->
        <div v-if="view === 'work-orders'" class="list-view">
          <div class="list-head">
            <h2 class="list-title">我的工单</h2>
            <div class="wo-filter-row">
              <input v-model="woSearch" placeholder="搜索工单标题或编号..." class="wo-search-input" />
              <select v-model="woTypeFilter" class="wo-type-select">
                <option value="all">全部类型</option>
                <option value="售前">售前</option>
                <option value="售后">售后</option>
              </select>
            </div>
            <button class="btn-brand btn-compact" @click="wo.openDialog(chat.sessionId)">提交工单</button>
          </div>
          <div v-if="wo.loading" class="list-hint">加载中…</div>
          <div v-else-if="!filteredWorkOrders.length" class="list-hint">暂无工单记录</div>
          <div v-else class="record-stack">
            <div v-for="w in filteredWorkOrders" :key="w.id" class="record-card wo-user-card" @click="openWoDetail(w)">
              <div class="wo-user-card-left">
                <div class="rec-row">
                  <span class="rec-no">#{{ w.id }}</span>
                  <span class="pill" :class="'pill-' + w.status">{{ wo.getStatusLabel(w.status) }}</span>
                </div>
                <h3 class="rec-title">{{ w.title }}</h3>
                <p class="rec-meta">{{ w.description }}</p>
                <div class="rec-foot">
                  <span class="chip">{{ w.type }}</span>
                  <span class="rec-date">{{ new Date(w.createTime).toLocaleDateString() }}</span>
                </div>
                <div class="rec-card-actions">
                  <button class="rec-contact-btn" @click.stop="contactForWorkOrder(w)">📨 联系客服</button>
                  <div v-if="w.status === 'completed' && w.rating == null" class="wo-rate-wrapper">
                    <button class="btn-rate" @mouseenter="woRateHover = w.id" @click.prevent.stop>评价</button>
                    <div v-if="woRateHover === w.id" class="wo-star-panel" @mouseleave="woRateHover = null">
                      <span v-for="s in 5" :key="s" class="wo-star"
                            :class="{ on: woStarHover >= s }"
                            @mouseenter="woStarHover = s"
                            @click.stop="submitWoRate(w.id, s)">★</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="wo-user-card-right">
                <div class="wo-user-timeline-title">操作记录</div>
                <div class="wo-user-timeline" v-if="w._auditLogs && w._auditLogs.length">
                  <div v-for="log in w._auditLogs.slice(0, 4)" :key="log.id" class="wo-user-timeline-item">
                    <span class="wo-user-timeline-time">{{ fmtAuditTime(log.createTime) }}</span>
                    <span class="wo-user-timeline-action">{{ log.action }}</span>
                  </div>
                </div>
                <div v-else class="wo-user-timeline-empty">暂无操作记录</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Work Order Detail Popup -->
        <Teleport to="body">
          <div v-if="woDetailDlg" class="overlay" @click.self="woDetailDlg = null">
            <div class="modal wo-detail-modal">
              <h3 class="modal-title">工单 #{{ woDetailDlg.id }} — {{ woDetailDlg.title }}</h3>
              <div class="wo-detail-popup-body">
                <div class="wo-detail-popup-meta">
                  <span>状态：{{ wo.getStatusLabel(woDetailDlg.status) }}</span>
                  <span>类型：{{ woDetailDlg.type || '-' }}</span>
                  <span>提交时间：{{ new Date(woDetailDlg.createTime).toLocaleString() }}</span>
                </div>
                <p v-if="woDetailDlg.description" class="wo-detail-popup-desc">{{ woDetailDlg.description }}</p>
                <div class="wo-detail-popup-timeline">
                  <h4 class="wo-detail-popup-section-title">完整操作记录</h4>
                  <div v-if="woDetailDlg._auditLogs && woDetailDlg._auditLogs.length" class="wo-detail-popup-loglist">
                    <div v-for="log in woDetailDlg._auditLogs" :key="log.id" class="wo-detail-popup-logitem">
                      <span class="wo-detail-popup-logtime">{{ fmtAuditTime(log.createTime) }}</span>
                      <span class="wo-detail-popup-logaction">{{ log.action }}</span>
                      <span v-if="log.detail" class="wo-detail-popup-logdetail">{{ log.detail }}</span>
                    </div>
                  </div>
                  <div v-else class="wo-user-timeline-empty">暂无操作记录</div>
                </div>
              </div>
              <div class="modal-acts">
                <button class="btn-ghost" @click="woDetailDlg = null">关闭</button>
                <button class="btn-brand" @click="contactFromPopup">联系客服</button>
              </div>
            </div>
          </div>
        </Teleport>
      </main>
    </div>

    <!-- ── Work Order Dialog ── -->
    <Teleport to="body">
      <div v-if="wo.showDialog" class="overlay" @click.self="wo.closeDialog">
        <div class="modal">
          <h3 class="modal-title">提交新工单</h3>
          <div class="modal-field">
            <label class="modal-label" for="wo-title">标题</label>
            <input id="wo-title" name="wo-title" v-model="wo.newWorkOrder.title" placeholder="工单标题" class="modal-input" />
          </div>
          <div class="modal-field">
            <label class="modal-label" for="wo-type">类型</label>
            <select id="wo-type" name="wo-type" v-model="wo.newWorkOrder.type" class="modal-input">
              <option value="售前">售前咨询</option>
              <option value="售后">售后服务</option>
            </select>
          </div>
          <div class="modal-field">
            <label class="modal-label" for="wo-desc">描述</label>
            <textarea id="wo-desc" name="wo-desc" v-model="wo.newWorkOrder.description" rows="4" placeholder="请详细描述问题" class="modal-input"></textarea>
          </div>
          <div class="modal-acts">
            <button class="btn-ghost" @click="wo.closeDialog">取消</button>
            <button class="btn-brand" @click="handleSubmitWorkOrder" :disabled="wo.submitting">
              {{ wo.submitting ? '提交中…' : '提交' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ── Login Prompt ── -->
    <Teleport to="body">
      <div v-if="showLoginHint" class="overlay" @click.self="showLoginHint = false">
        <div class="modal modal-sm">
          <h3 class="modal-title">需要登录</h3>
          <p class="modal-desc">登录后即可查看订单和工单</p>
          <div class="modal-acts">
            <button class="btn-ghost" @click="showLoginHint = false">暂不</button>
            <router-link to="/login" class="btn-brand">去登录</router-link>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ── Star Rating ── -->
    <Teleport to="body">
      <div v-if="showStars || chat.showSatisfaction" class="overlay" @click.self="closeStars">
        <div class="modal modal-sm">
          <h3 class="modal-title">评价本次服务</h3>
          <div class="star-line">
            <button v-for="s in 5" :key="s" class="star" :class="{ on: s <= rating }" @click="rating = s">★</button>
          </div>
          <div class="star-legend">
            <span>非常不满意</span><span>非常满意</span>
          </div>
          <div class="modal-acts">
            <button class="btn-ghost" @click="closeStars">跳过</button>
            <button class="btn-brand" @click="submitStars" :disabled="submittingStars">
              {{ submittingStars ? '提交中…' : '提交评价' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/shared/composables/useAuth'
import { useChatStore } from '@/shared/stores/chatStore'
import { useOrderStore } from '@/shared/stores/orderStore'
import { useWorkOrderStore } from '@/shared/stores/workOrderStore'
import http from '@/core/axios'

const router = useRouter()
const auth = useAuth()
const chat = useChatStore()
const order = useOrderStore()
const wo = useWorkOrderStore()

const newMsg = ref('')
const threadEl = ref(null)
const view = ref('chat')
const showLoginHint = ref(false)
const showStars = ref(false)
const rating = ref(0)
const submittingStars = ref(false)
const hasRated = ref(false)
const hasUnreadWoUpdate = ref(false)
const woDetailDlg = ref(null)
const woRateHover = ref(null)
const woStarHover = ref(0)
const woSearch = ref('')
const woTypeFilter = ref('all')

const visibleMessages = computed(() => {
  return chat.messages.filter(m => {
    if (!m.isSystem) return true
    return m.systemType === 'session_status'
  })
})

const filteredWorkOrders = computed(() => {
  let orders = wo.workOrders
  // 类型筛选
  if (woTypeFilter.value !== 'all') {
    orders = orders.filter(w => w.type === woTypeFilter.value)
  }
  // 关键词搜索（按标题或ID）
  if (woSearch.value.trim()) {
    const kw = woSearch.value.trim().toLowerCase()
    orders = orders.filter(w => 
      (w.title && w.title.toLowerCase().includes(kw)) ||
      String(w.id).includes(kw)
    )
  }
  return orders
})

const switchView = async (v) => {
  if (v !== 'chat' && !auth.isLoggedIn.value) { showLoginHint.value = true; return }
  view.value = v
  if (v === 'orders') await order.fetchOrders()
  else if (v === 'work-orders') {
    wo.hasUnreadWoUpdate = false
    await wo.fetchWorkOrders()
    wo.workOrders.forEach(w => { fetchUserAuditLogs(w) })
  }
}

const fetchUserAuditLogs = async (workOrder) => {
  try {
    const res = await http.get(`/api/work-orders/${workOrder.id}/audit-logs?userVisible=true`)
    if (res.data.code === 200) {
      workOrder._auditLogs = res.data.data || []
    }
  } catch { /* ignore */ }
}

const fmtAuditTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const scrollThread = () => nextTick(() => { if (threadEl.value) threadEl.value.scrollTop = threadEl.value.scrollHeight })

const handleSend = async () => {
  const c = newMsg.value.trim()
  if (!c) return
  newMsg.value = ''
  await chat.sendMessage(c, { roles: auth.roles.value, userId: auth.userId.value })
  scrollThread()
}

const handleSyncOrders = async () => { const r = await order.sync(); if (!r.success && r.message) alert(r.message) }
const handleSubmitWorkOrder = async () => { const r = await wo.submit(); if (!r.success && r.message) alert(r.message) }

const handleFeedback = async (i, t) => {
  const m = chat.messages[i]
  if (m.isUser || m.feedback) return
  const s = t === 'up' ? 5 : 1
  const r = await chat.submitRating(s, auth.userId.value, null)
  if (r.code === 200) chat.setMessageFeedback(i, t)
}

const transferToHuman = () => {
  if (chat.transferring.value || chat.humanSessionActive.value) return
  chat.transferToHuman({ roles: auth.roles.value, userId: auth.userId.value })
}

const contactForWorkOrder = (workOrder) => {
  view.value = 'chat'
  if (!chat.humanSessionActive.value && !chat.transferring.value) {
    chat.transferToHuman({ roles: auth.roles.value, userId: auth.userId.value })
  }
  chat.messages.push({
    content: `[工单 #${workOrder.id}] ${workOrder.title}`,
    isUser: true
  })
  chat.sendWorkOrderContact(workOrder.id, workOrder.title).catch(() => {})
}

const openWoDetail = (workOrder) => {
  woDetailDlg.value = workOrder
  woRateHover.value = null
  woStarHover.value = 0
}

const contactFromPopup = () => {
  const w = woDetailDlg.value
  woDetailDlg.value = null
  if (w) contactForWorkOrder(w)
}

const submitWoRate = async (workOrderId, rating) => {
  try {
    const res = await http.post(`/api/work-orders/${workOrderId}/rate`, {
      rating,
      userId: auth.userId.value
    })
    if (res.data.code === 200) {
      const target = wo.workOrders.find(w => w.id === workOrderId)
      if (target) target.rating = rating
      woRateHover.value = null
      woStarHover.value = 0
    }
  } catch { /* ignore */ }
}

const endHumanSession = () => {
  chat.endHumanSession()
}

const cancelWaiting = () => {
  chat.cancelWaiting()
}

const endChat = () => { if (!hasRated.value) { showStars.value = true; hasRated.value = true } }
const submitStars = async () => {
  if (rating.value === 0) { alert('请选择评分'); return }
  submittingStars.value = true
  try { await chat.submitRating(rating.value, auth.userId.value, chat.currentAgentId || null); showStars.value = false; chat.showSatisfaction = false; rating.value = 0 }
  catch { alert('提交失败') }
  finally { submittingStars.value = false }
}
const closeStars = () => { showStars.value = false; chat.showSatisfaction = false; rating.value = 0 }

watch(() => chat.showSatisfaction, (v) => { if (v) showStars.value = true })
watch(visibleMessages, () => scrollThread())

const handleLogout = () => { auth.logout(); chat.disconnect(); view.value = 'chat'; router.push('/login') }

onMounted(async () => {
  auth.checkLoginStatus()
  chat.initSession()
  const isGuest = !auth.isLoggedIn
  await chat.loadHistory(isGuest)
  chat.connectWebSocket().then(() => {
    chat.restoreSessionStatus(isGuest)
  }).catch(() => {})
})
onBeforeUnmount(() => { chat.disconnect() })
</script>

<style scoped>
/* ── Shell ── */
.chat-shell { height: 100%; display: flex; flex-direction: column; overflow: hidden; background: var(--base); }

/* ── User Sidebar ── */
.chat-body { flex: 1; display: flex; min-height: 0; overflow: hidden; }
.user-sidebar {
  width: 240px; flex-shrink: 0; background: var(--nav-bg);
  display: flex; flex-direction: column;
}
.user-sidebar-brand {
  padding: var(--s-5); display: flex; align-items: center; gap: var(--s-3);
  border-bottom: 1px solid oklch(0.22 0.018 210);
}
.user-avatar {
  width: 40px; height: 40px; border-radius: var(--radius-full);
  background: var(--brand); display: flex; align-items: center;
  justify-content: center; color: #fff; font-weight: 700;
  font-size: 16px; flex-shrink: 0;
}
.user-info { display: flex; flex-direction: column; }
.user-name { color: #fff; font-size: 14px; font-weight: 600; }
.user-role { color: var(--nav-text); font-size: 11px; }
.user-login-link { color: var(--brand); font-size: 12px; text-decoration: none; }
.user-login-link:hover { text-decoration: underline; }
.user-nav { flex: 1; padding: var(--s-4) var(--s-3); display: flex; flex-direction: column; gap: var(--s-1); }
.user-nav-item {
  display: flex; align-items: center; gap: var(--s-3); padding: var(--s-3) var(--s-4);
  border: none; border-radius: var(--radius-md); background: transparent;
  font-size: 14px; font-family: var(--font-body); color: var(--nav-text); cursor: pointer;
  transition: all .15s ease; text-align: left; width: 100%;
}
.user-nav-item:hover { background: oklch(0.24 0.018 210); color: #fff; }
.user-nav-item.on { background: var(--nav-active); color: #fff; font-weight: 600; }
.user-nav-icon { width: 20px; text-align: center; font-size: 15px; }
.user-nav-badge {
  margin-left: auto; background: var(--danger); color: #fff;
  font-size: 10px; font-weight: 700; padding: 1px 7px; border-radius: var(--radius-full);
}
.user-sidebar-footer {
  padding: var(--s-4) var(--s-5); border-top: 1px solid oklch(0.22 0.018 210);
}
.user-logout {
  width: 100%; padding: var(--s-2) var(--s-4);
  border: 1px solid oklch(0.28 0.018 210); border-radius: var(--radius-md);
  background: transparent; font-size: 13px; font-family: var(--font-body);
  color: var(--nav-text); cursor: pointer; transition: all .15s ease;
}
.user-logout:hover { background: oklch(0.22 0.018 210); color: #fff; }

/* ── Stage ── */
.stage { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }

/* ── Chat Flow ── */
.chat-flow { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.thread { flex: 1; overflow-y: auto; padding: var(--s-6); display: flex; flex-direction: column; gap: var(--s-5); }
.thread-empty { margin: auto; text-align: center; display: flex; flex-direction: column; align-items: center; }
.empty-greet { font-family: var(--font-heading); font-size: 18px; font-style: italic; color: var(--ink-soft); opacity: 0.55; margin: 0 0 var(--s-5) 0; }

.thread-msg { display: flex; flex-direction: column; max-width: 78%; animation: slideUp var(--dur-normal) var(--ease-soft); }
.by-user { align-self: flex-end; align-items: flex-end; }
.by-ai { align-self: flex-start; align-items: flex-start; }
.by-system { align-self: center; }

.msg-system {
  font-size: var(--text-2xs); color: var(--ink-muted);
  background: var(--base-alt); padding: var(--s-1) var(--s-3);
  border-radius: var(--radius-full); max-width: 90%; text-align: center;
}

.msg-bubble { padding: var(--s-3) var(--s-5); border-radius: var(--radius-lg); font-size: var(--text-sm); line-height: var(--leading-relaxed); word-break: break-word; }
.bub-user { background: var(--brand); color: #fff; border-bottom-right-radius: var(--radius-sm); }
.bub-ai { background: var(--surface); color: var(--ink); box-shadow: var(--shadow-xs); border-bottom-left-radius: var(--radius-sm); }
.bub-ai :deep(p) { margin: var(--s-2) 0; }
.bub-ai :deep(p:first-child) { margin-top: 0; }
.bub-ai :deep(p:last-child) { margin-bottom: 0; }
.bub-ai :deep(code) { background: var(--base); padding: 1px 4px; border-radius: 3px; font-size: var(--text-3xs); font-family: var(--font-mono); }
.bub-ai :deep(pre) { background: var(--base); padding: var(--s-3); border-radius: var(--radius-sm); overflow-x: auto; }
.bub-ai :deep(pre code) { background: none; padding: 0; }
.bub-ai :deep(details) { margin: var(--s-2) 0; }
.bub-ai :deep(details summary) { color: var(--ink-muted); cursor: pointer; font-size: var(--text-2xs); }

.msg-vote { display: flex; gap: var(--s-2); margin-top: var(--s-1); }
.vote-btn { padding: 2px 10px; border: 1px solid var(--border); border-radius: var(--radius-full); background: var(--surface); font-size: var(--text-2xs); font-family: var(--font-body); color: var(--ink-muted); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); }
.vote-btn:hover, .vote-btn.on { border-color: var(--brand); color: var(--brand); background: var(--brand-pale); }

@keyframes slideUp { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }

/* ── Composer ── */
.composer { display: flex; gap: var(--s-3); padding: var(--s-4) var(--s-6); background: var(--surface); border-top: 1px solid var(--border-light); align-items: center; flex-shrink: 0; flex-wrap: wrap; }
.compose-input { flex: 1; min-width: 160px; padding: var(--s-3) var(--s-5); border: 1.5px solid var(--border); border-radius: var(--radius-full); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base); transition: border-color var(--dur-fast) var(--ease-soft); }
.compose-input:focus { border-color: var(--brand); box-shadow: 0 0 0 3px var(--brand-soft); outline: none; }
.compose-input::placeholder { color: var(--ink-muted); font-weight: var(--weight-light); }

/* ── Buttons ── */
.btn-brand {
  padding: var(--s-3) var(--s-6); border: none; border-radius: var(--radius-full);
  font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body);
  color: #fff; background: var(--brand); cursor: pointer; text-decoration: none;
  display: inline-flex; align-items: center; gap: var(--s-2); white-space: nowrap;
  transition: background var(--dur-fast) var(--ease-soft), transform var(--dur-fast) var(--ease-out);
}
.btn-brand:hover:not(:disabled) { background: var(--brand-deep); transform: translateY(-1px); }
.btn-brand:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-ghost {
  padding: var(--s-3) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-full);
  font-size: var(--text-sm); font-weight: var(--weight-medium); font-family: var(--font-body);
  color: var(--ink-soft); background: var(--surface); cursor: pointer;
  transition: all var(--dur-fast) var(--ease-soft); white-space: nowrap;
}
.btn-ghost:hover { border-color: var(--ink-muted); color: var(--ink); }

.btn-back-to-ai {
  padding: var(--s-3) var(--s-4); border: 1.5px solid var(--agent); border-radius: var(--radius-full);
  font-size: var(--text-sm); font-weight: var(--weight-medium); font-family: var(--font-body);
  color: var(--agent); background: var(--agent-soft); cursor: pointer;
  transition: all var(--dur-fast) var(--ease-soft); white-space: nowrap;
}
.btn-back-to-ai:hover { background: var(--agent); color: #fff; }

.transfer-hint { font-size: var(--text-2xs); color: var(--warning); font-weight: var(--weight-medium); white-space: nowrap; display: inline-flex; align-items: center; gap: var(--s-2); }
.cancel-wait-btn {
  padding: 1px 8px; border: 1px solid var(--danger); border-radius: var(--radius-full);
  background: transparent; font-size: var(--text-3xs); font-family: var(--font-body);
  color: var(--danger); cursor: pointer; white-space: nowrap;
  transition: all var(--dur-fast);
}
.cancel-wait-btn:hover { background: var(--danger-soft); }

.btn-compact { padding: var(--s-2) var(--s-4); font-size: var(--text-sm); }

/* ── List views ── */
.list-view { flex: 1; overflow-y: auto; padding: var(--s-6); }
.list-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--s-6); }
.list-title { font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-semibold); color: var(--ink); }
.list-hint { text-align: center; padding: var(--s-20) var(--s-4); color: var(--ink-muted); font-size: var(--text-sm); }

.record-stack { display: flex; flex-direction: column; gap: var(--s-4); }
.record-card { background: var(--surface); border-radius: var(--radius-xl); padding: var(--s-5); box-shadow: var(--shadow-xs); transition: box-shadow var(--dur-fast) var(--ease-soft); }
.record-card:hover { box-shadow: var(--shadow-sm); }
.rec-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--s-3); }
.rec-no { font-family: var(--font-mono); font-size: var(--text-xs); color: var(--ink-soft); }
.rec-body { display: flex; flex-direction: column; gap: var(--s-2); }
.rec-title { font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--ink); }
.rec-meta { font-size: var(--text-sm); color: var(--ink-muted); }
.rec-foot { display: flex; justify-content: space-between; align-items: center; padding-top: var(--s-3); border-top: 1px solid var(--border-light); }
.rec-price { font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--danger); }
.rec-date { font-size: var(--text-2xs); color: var(--ink-muted); }
.rec-contact-btn {
  padding: var(--s-2) var(--s-4);
  border: 1.5px solid var(--brand); border-radius: var(--radius-md);
  background: var(--brand-soft); font-size: var(--text-sm);
  font-weight: var(--weight-semibold); font-family: var(--font-body);
  color: var(--brand); cursor: pointer;
  transition: all var(--dur-fast);
}
.rec-contact-btn:hover { background: var(--brand); color: #fff; }

.rec-card-actions {
  margin-top: var(--s-3); display: flex; gap: var(--s-3); align-items: center;
}

.wo-user-card { display: flex; gap: var(--s-4); }
.wo-user-card-left { flex: 1; min-width: 0; }
.wo-user-card-right {
  flex: 0 0 200px; border-left: 1px solid var(--border-light);
  padding-left: var(--s-4); display: flex; flex-direction: column; gap: var(--s-2);
}
.wo-user-timeline-title {
  font-size: var(--text-2xs); font-weight: var(--weight-semibold);
  color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.05em;
}
.wo-user-timeline {
  display: flex; flex-direction: column; gap: var(--s-1); max-height: 140px; overflow-y: auto;
}
.wo-user-timeline-item {
  display: flex; flex-direction: column; gap: 1px;
  padding: 3px 0 3px 10px; border-left: 2px solid var(--border-light);
  font-size: var(--text-2xs);
}
.wo-user-timeline-time { color: var(--ink-muted); font-size: var(--text-3xs); }
.wo-user-timeline-action { color: var(--ink); font-weight: var(--weight-medium); }
.wo-user-timeline-empty { font-size: var(--text-2xs); color: var(--ink-muted); }
.wo-detail-modal { max-width: 520px; }
.wo-detail-popup-body { display: flex; flex-direction: column; gap: var(--s-4); margin: var(--s-4) 0; }
.wo-detail-popup-meta {
  display: flex; gap: var(--s-4); font-size: var(--text-sm); color: var(--ink-soft);
  flex-wrap: wrap;
}
.wo-detail-popup-desc {
  font-size: var(--text-sm); color: var(--ink); line-height: var(--leading-relaxed);
  padding: var(--s-3); background: var(--base); border-radius: var(--radius-md);
  margin: 0; max-height: 80px; overflow-y: auto;
}
.wo-detail-popup-section-title {
  font-size: var(--text-xs); font-weight: var(--weight-semibold);
  color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.04em;
  margin: 0 0 var(--s-2);
}
.wo-detail-popup-loglist {
  display: flex; flex-direction: column; gap: var(--s-1); max-height: 200px; overflow-y: auto;
}
.wo-detail-popup-logitem {
  display: flex; flex-direction: column; gap: 1px;
  padding: 6px 0 6px 12px; border-left: 2px solid var(--border-light);
  font-size: var(--text-xs);
}
.wo-detail-popup-logtime { font-size: var(--text-3xs); color: var(--ink-muted); }
.wo-detail-popup-logaction { color: var(--ink); font-weight: var(--weight-semibold); }
.wo-detail-popup-logdetail { color: var(--ink-soft); font-size: var(--text-2xs); }

.pill { display: inline-block; font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 10px; border-radius: var(--radius-full); }
.pill-ok { background: var(--success-soft); color: var(--success); }
.pill-in { background: var(--warning-soft); color: var(--warning); }
.pill-pending { background: var(--warning-soft); color: var(--warning); }
.pill-processing { background: var(--brand-soft); color: var(--brand); }
.pill-completed { background: var(--success-soft); color: var(--success); }
.pill-cancelled { background: var(--danger-soft); color: var(--danger); }

.chip { display: inline-block; font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 8px; border-radius: var(--radius-sm); background: var(--brand-pale); color: var(--brand); }

/* ── Modal ── */
.overlay { position: fixed; inset: 0; background: oklch(0.18 0.018 210 / 0.35); display: flex; align-items: center; justify-content: center; z-index: 200; padding: var(--s-4); animation: fadeIn var(--dur-fast) var(--ease-soft); }
.modal { background: var(--surface); border-radius: var(--radius-2xl); padding: var(--s-8); width: 100%; max-width: 440px; box-shadow: var(--shadow-xl); animation: popIn var(--dur-normal) var(--ease-out); }
.modal-sm { max-width: 360px; }
.modal-title { font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-semibold); color: var(--ink); margin-bottom: var(--s-2); }
.modal-desc { font-size: var(--text-sm); color: var(--ink-soft); margin-bottom: var(--s-6); }
.modal-field { margin-bottom: var(--s-4); }
.modal-label { display: block; font-size: var(--text-2xs); font-weight: var(--weight-medium); color: var(--ink-soft); text-transform: uppercase; letter-spacing: 0.08em; margin-bottom: var(--s-2); }
.modal-input { width: 100%; padding: var(--s-3) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-md); font-family: var(--font-body); color: var(--ink); background: var(--base); resize: vertical; transition: border-color var(--dur-fast) var(--ease-soft); }
.modal-input:focus { border-color: var(--brand); box-shadow: 0 0 0 3px var(--brand-soft); outline: none; }
.modal-acts { display: flex; justify-content: flex-end; gap: var(--s-3); margin-top: var(--s-6); }

.star-line { display: flex; justify-content: center; gap: var(--s-3); margin: var(--s-4) 0; }
.star { font-size: 2rem; border: none; background: none; color: var(--border); cursor: pointer; transition: color var(--dur-fast) var(--ease-soft), transform var(--dur-fast) var(--ease-out); }
.star.on { color: oklch(0.72 0.18 85); }
.star:hover { transform: scale(1.2); }
.star-legend { display: flex; justify-content: space-between; font-size: var(--text-3xs); color: var(--ink-muted); margin-bottom: var(--s-4); }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes popIn { from { opacity: 0; transform: scale(0.94) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }

.wo-rate-wrapper {
  position: relative; display: inline-flex; align-items: center;
}
.btn-rate {
  padding: var(--s-2) var(--s-4); border: 1.5px solid oklch(0.58 0.170 45);
  border-radius: var(--radius-md); background: oklch(0.95 0.040 45);
  font-size: var(--text-sm); font-weight: var(--weight-semibold);
  color: oklch(0.50 0.150 45); cursor: pointer;
  transition: all var(--dur-fast); white-space: nowrap;
}
.btn-rate:hover { background: oklch(0.58 0.170 45); color: #fff; }
.wo-star-panel {
  position: absolute; left: 100%; top: 50%; transform: translateY(-50%);
  display: flex; gap: 2px; padding: 4px 8px; margin-left: 4px;
  background: var(--surface); border: 1px solid var(--border-light);
  border-radius: 10px; box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  white-space: nowrap; z-index: 100;
}
.wo-star {
  font-size: 24px; color: var(--border-light); cursor: pointer;
  transition: color 0.12s, transform 0.12s; line-height: 1;
  user-select: none;
}
.wo-star:hover { transform: scale(1.15); }
.wo-star.on { color: oklch(0.58 0.170 45); }

.wo-filter-row {
  display: flex; gap: 8px; align-items: center; flex: 1; margin: 0 12px;
}
.wo-search-input {
  flex: 1; max-width: 200px;
  padding: 6px 12px; border: 1px solid var(--border); border-radius: 8px;
  font-size: 13px; color: var(--ink); background: var(--surface);
  outline: none; transition: border-color 0.15s;
}
.wo-search-input:focus { border-color: var(--brand); }
.wo-search-input::placeholder { color: var(--ink-muted); }
.wo-type-select {
  padding: 6px 10px; border: 1px solid var(--border); border-radius: 8px;
  font-size: 13px; color: var(--ink); background: var(--surface);
  outline: none; cursor: pointer;
}
.wo-type-select:focus { border-color: var(--brand); }
</style>
