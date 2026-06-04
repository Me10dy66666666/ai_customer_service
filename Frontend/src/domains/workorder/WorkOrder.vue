<template>
  <div class="wo-shell">
    <header class="wo-topbar">
      <h2 class="wo-topbar-title">工单管理</h2>
      <div class="wo-topbar-actions">
        <button class="wo-btn wo-btn-brand" @click="openCreateDialog">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
          新建工单
        </button>
        <button class="wo-btn wo-btn-outline" @click="exportOrders">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 2v9M4 7l4 4 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M2 12v1.5A1.5 1.5 0 003.5 15h9a1.5 1.5 0 001.5-1.5V12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
          导出
        </button>
      </div>
    </header>

    <div class="wo-body">
      <aside class="wo-list-panel">
        <div class="wo-list-search">
          <label for="wo-search" class="sr-only">搜索工单</label>
          <svg class="wo-search-icon" width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="7" cy="7" r="4.5" stroke="currentColor" stroke-width="1.5"/><path d="M10.5 10.5L14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          <input id="wo-search" name="wo-search" v-model="searchKeyword" type="text" class="wo-search-input" placeholder="搜索工单标题…" />
        </div>
        <div class="wo-list-filter-bar">
          <button v-for="opt in statusFilterOptions" :key="opt.value" class="wo-filter-chip" :class="{ 'wo-filter-chip--active': statusFilter === opt.value }" @click="statusFilter = opt.value">
            {{ opt.label }}
            <span v-if="opt.count !== undefined" class="wo-filter-chip-count">{{ opt.count }}</span>
          </button>
        </div>
        <div class="wo-list-scroll" v-loading="store.loading">
          <div v-if="sortedList.length === 0" class="wo-list-empty">暂无匹配工单</div>
          <button v-for="wo in sortedList" :key="wo.id" class="wo-list-item"
                  :class="[{ 'wo-list-item--active': selectedId === wo.id }, 'wo-li-sla--' + getListSlaLevel(wo)]"
                  @click="selectOrder(wo)">
            <span class="wo-list-item-content">
              <span class="wo-list-item-id">#{{ wo.id }}</span>
              <span class="wo-list-item-badge" :class="'wo-pri-badge--' + wo.priority">{{ priorityLabel(wo.priority) }}</span>
            </span>
            <span class="wo-list-item-title">{{ wo.title }}</span>
            <div class="wo-list-item-foot">
              <span class="wo-list-item-status" :class="'wo-status--' + wo.status">{{ statusLabel(wo.status) }}</span>
              <button v-if="wo.status === 'pending' && !wo.handlerId"
                      class="wo-btn wo-btn-claim-card"
                      @click.stop="claimFromCard(wo)">认领</button>
            </div>
          </button>
        </div>
        <div class="wo-list-pager" v-if="store.totalCount > store.pageSize">
          <button class="wo-pager-btn" :disabled="store.currentPage <= 1" @click="goPage(store.currentPage - 1)">‹</button>
          <span class="wo-pager-info">{{ store.currentPage }} / {{ totalPages }}</span>
          <button class="wo-pager-btn" :disabled="store.currentPage >= totalPages" @click="goPage(store.currentPage + 1)">›</button>
        </div>
      </aside>

      <main class="wo-detail-panel" v-if="selected">
        <div class="wo-detail-header">
          <h3 class="wo-detail-id">工单详情 #{{ selected.id }}</h3>
          <button class="wo-btn wo-btn-ghost-sm" @click="selected = null; selectedId = null">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            关闭
          </button>
        </div>

        <div class="wo-sla-bar wo-sla-bar--paused" v-if="isSlaPaused && selected.status !== 'completed' && selected.status !== 'cancelled'">
          <div class="wo-sla-progress wo-sla-progress--paused" :style="{ width: slaPercent + '%' }"></div>
          <span class="wo-sla-label wo-sla-label--paused">SLA 已暂停</span>
        </div>
        <div class="wo-sla-bar" v-else-if="selected.slaDeadline && selected.status !== 'completed' && selected.status !== 'cancelled'" :class="'wo-sla-bar--' + slaLevel">
          <div class="wo-sla-progress" :style="{ width: slaPercent + '%' }" :class="'wo-sla-progress--' + slaLevel"></div>
          <span class="wo-sla-label">剩余 {{ slaRemaining }}</span>
        </div>
        <div class="wo-sla-bar wo-sla-bar--done" v-if="selected.slaDeadline && (selected.status === 'completed' || selected.status === 'cancelled')">
          <div class="wo-sla-progress wo-sla-progress--done" style="width: 100%"></div>
          <span class="wo-sla-label wo-sla-label--done">{{ selected.status === 'completed' ? '已完成' : '已取消' }}</span>
        </div>

        <div class="wo-detail-three-col">
          <!-- LEFT: Meta + Description -->
          <div class="wo-col-left">
            <div class="wo-col-section-label">基本信息</div>
            <div class="wo-meta-title">{{ selected.title }}</div>
            <div class="wo-meta-grid">
              <div class="wo-meta-cell">
                <label class="wo-detail-label" for="wo-edit-status">状态</label>
                <select id="wo-edit-status" name="wo-edit-status" v-model="editStatus" class="wo-detail-select" @change="handleStatusChange">
                  <option value="pending">待处理</option>
                  <option value="processing">处理中</option>
                  <option value="completed">已完成</option>
                  <option value="cancelled">已取消</option>
                </select>
              </div>
              <div class="wo-meta-cell" v-if="selected.handlerId">
                <label class="wo-detail-label">负责人</label>
                <span class="wo-detail-value">{{ selected.handlerId }}</span>
              </div>
              <div class="wo-meta-cell">
                <label class="wo-detail-label">类型</label>
                <span class="wo-detail-value">{{ selected.type || '-' }}</span>
              </div>
              <div class="wo-meta-cell" v-if="selected.userPhone">
                <label class="wo-detail-label">联系电话</label>
                <span class="wo-detail-value" style="display:flex;align-items:center;gap:6px">
                  <span>{{ phoneDisplay }}</span>
                  <button class="wo-phone-eye-btn" @click="togglePhoneView" :title="phoneRevealed ? '隐藏' : '查看完整号码'">
                    {{ phoneRevealed ? '🙈' : '👁️' }}
                  </button>
                  <button v-if="phoneRevealed" class="wo-phone-copy-btn" @click="copyPhone" title="复制号码">📋</button>
                </span>
              </div>
              <div class="wo-meta-cell" v-if="selected.emotionLevel">
                <label class="wo-detail-label">情绪</label>
                <span class="wo-detail-value">{{ selected.emotionLevel }}</span>
              </div>
            </div>

            <div class="wo-desc-box" v-if="selected.description">
              <span class="wo-desc-badge">工单描述</span>
              <p class="wo-detail-desc-text">{{ selected.description }}</p>
            </div>

            <div class="wo-tags-row">
              <span class="wo-tag wo-tag-pri" :class="'wo-tag-pri--' + selected.priority">{{ priorityLabel(selected.priority) }}</span>
              <span class="wo-tag wo-tag-st" :class="'wo-tag-st--' + selected.status">{{ statusLabel(selected.status) }}</span>
              <button class="wo-tag-edit-toggle" @click="editingTags = !editingTags">{{ editingTags ? '保存' : '编辑' }}</button>
            </div>
            <div v-if="!editingTags && parsedTags.length" class="wo-tags-row">
              <span v-for="(tag, idx) in parsedTags" :key="idx" class="wo-tag wo-tag--dify">{{ tag }}</span>
            </div>
            <div v-if="editingTags" class="wo-tags-row">
              <label for="wo-edit-tags" class="sr-only">编辑标签</label>
              <input id="wo-edit-tags" name="wo-edit-tags" v-model="editTagsValue" class="wo-tags-input" placeholder="逗号分隔" @blur="saveTags" @keyup.enter="saveTags" />
            </div>
          </div>

          <!-- MIDDLE: AI Analysis -->
          <div class="wo-col-mid">
            <div class="wo-col-section-label">AI 分析摘要</div>
            <div class="wo-ai-card-new">
              <div class="wo-ai-head-row">
                <span class="wo-ai-icon">💡</span>
                <span class="wo-ai-title">AI 分析摘要</span>
              </div>
              <p class="wo-ai-summary-new" v-if="selected.summary">{{ selected.summary }}</p>
              <p class="wo-ai-summary-new wo-ai-summary-placeholder" v-else>暂无 AI 分析摘要</p>
              <div class="wo-ai-tags-row" v-if="selected.matchingSkill || selected.dispatchConfidence">
                <span v-if="selected.matchingSkill" class="wo-ai-tag-new">匹配技能：{{ selected.matchingSkill }}</span>
                <span v-if="selected.dispatchConfidence" class="wo-ai-tag-new">置信度：{{ (selected.dispatchConfidence * 100).toFixed(0) }}%</span>
                <span v-if="selected.bizTag" class="wo-ai-tag-new">业务：{{ selected.bizTag }}</span>
              </div>
            </div>
          </div>

          <!-- RIGHT: Audit Log (Timeline) -->
          <div class="wo-col-right">
            <div class="wo-col-section-label">操作记录</div>
            <div class="wo-audit-list">
              <!-- sla_pause_log events are automatically recorded by WorkOrderApplicationService
                   and appear in the audit timeline alongside other status change events -->
              <div v-if="auditLogs.length === 0 && !auditLoading" class="wo-audit-empty">暂无操作记录</div>
              <div v-for="(log, idx) in auditLogs" :key="idx" class="wo-audit-item" :class="'wo-audit--' + log.eventType.toLowerCase()">
                <div class="wo-audit-body">
                  <span class="wo-audit-time">{{ fmtTime(log.createTime) }}</span>
                  <span class="wo-audit-action">{{ log.action }}</span>
                  <span v-if="log.detail" class="wo-audit-detail">{{ log.detail }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="wo-action-bar">
          <div class="wo-sla-dropdown" v-if="selected.status === 'processing' && !isSlaPaused">
            <button class="wo-btn wo-btn-outline wo-btn-sla-pause" @click="slaDropdownVisible = !slaDropdownVisible">
              ⏸ 暂停 SLA
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="M2 3l3 3 3-3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
            </button>
            <div class="wo-sla-dropdown-menu" v-if="slaDropdownVisible" @click.stop>
              <button v-for="reason in slaPauseReasons" :key="reason.value"
                      class="wo-sla-dropdown-item"
                      @click="handlePauseSla(reason.value)">{{ reason.label }}</button>
            </div>
          </div>
          <button class="wo-btn wo-btn-brand wo-btn-sla-resume" v-if="isSlaPaused" @click="handleResumeSla">
            ▶ 恢复 SLA
          </button>
          <button class="wo-btn wo-btn-brand" @click="openMiniChat" v-if="selected.sessionId">
            📨 转入会话沟通
          </button>
          <button class="wo-btn wo-btn-brand" @click="noteDlg = true">
            📝 记录备注
          </button>
          <button class="wo-btn wo-btn-outline" @click="handleMarkComplete">标记完成</button>
          <button class="wo-btn wo-btn-outline" @click="handleTransfer">转移</button>
        </div>
      </main>

      <!-- Draggable Resizable Chat Popup -->
      <div v-if="showMiniChat && selected" class="chat-popup-overlay">
        <div class="chat-popup"
             :style="{ left: popupX + 'px', top: popupY + 'px', width: popupW + 'px', height: popupH + 'px' }"
             @mousedown.stop>
          <div class="chat-popup-head" @mousedown="startDrag" @touchstart.passive="startDragTouch">
            <span class="chat-popup-title">💬 工单 #{{ selected.id }} — {{ selected.userNickname || '用户#' + selected.userId }}</span>
            <div class="chat-popup-head-actions">
              <button class="chat-popup-end-btn" @click.stop="endMiniSession" :disabled="chatEnding">结束会话</button>
              <button class="chat-popup-close-btn" @click="showMiniChat = false; stopChatPoll()">✕</button>
            </div>
          </div>
          <div class="chat-popup-body" ref="miniChatBody">
            <div v-if="miniChatMessages.length === 0" class="mini-chat-empty">输入消息开始沟通…</div>
            <div v-for="(m, i) in miniChatMessages" :key="i"
                 class="mini-msg" :class="m.role === 'agent' ? 'mini-msg--agent' : m.role === 'system' ? 'mini-msg--system' : 'mini-msg--user'">
              <span v-if="m.role !== 'system'" class="mini-msg-role">{{ m.role === 'agent' ? '我' : '用户' }}</span>
              <span class="mini-msg-content">{{ m.content }}</span>
            </div>
          </div>
          <div class="chat-popup-input-bar">
            <label for="wo-mini-compose" class="sr-only">输入回复</label>
            <input id="wo-mini-compose" name="wo-mini-compose" v-model="miniComposeText" @keyup.enter="sendMiniMsg" placeholder="输入回复…" class="chat-popup-input" />
            <button class="chat-popup-send-btn" @click="sendMiniMsg" :disabled="!miniComposeText.trim()">发送</button>
          </div>
          <div class="chat-popup-resize-handle" @mousedown="startResize" @touchstart.passive="startResizeTouch"></div>
        </div>
      </div>

      <div class="wo-detail-empty" v-if="!selected">
        <div class="wo-empty-state">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="var(--ink-muted)" stroke-width="1.2" stroke-linecap="round"><rect x="3" y="3" width="18" height="18" rx="3"/><path d="M8 9h8M8 13h5M8 17h7"/></svg>
          <p class="wo-empty-title">选择左侧工单查看详情</p>
          <p class="wo-empty-hint">点击列表中的工单以查看完整信息与 AI 分析</p>
        </div>
      </div>

      <aside class="wo-emergency-panel">
        <div class="wo-emergency-head">
          <span class="wo-emergency-head-icon">⚠</span>
          <span class="wo-emergency-head-text">紧急处理 <span class="wo-emergency-head-count">{{ emergencyOrders.length }}</span></span>
        </div>
        <div class="wo-emergency-list">
          <div v-if="emergencyOrders.length === 0" class="wo-emergency-empty">无紧急工单</div>
          <button v-for="wo in emergencyOrders" :key="wo.id"
                  class="wo-list-item" :class="'wo-em-sl--' + getEmergencyLevel(wo)"
                  @click="selectOrder(wo)">
            <span class="wo-list-item-content">
              <span class="wo-list-item-id">#{{ wo.id }}</span>
              <span class="wo-em-pri-badge" :class="'wo-em-pri-badge--' + wo.priority">{{ priorityLabel(wo.priority) }}</span>
            </span>
            <span class="wo-list-item-title">{{ wo.title }}</span>
            <div class="wo-list-item-foot">
              <span class="wo-list-item-status" :class="'wo-em-status--' + wo.status">{{ statusLabel(wo.status) }}</span>
              <span class="wo-em-deadline">{{ fmtSlaRemaining(wo.slaDeadline) }}</span>
            </div>
          </button>
        </div>
      </aside>
    </div>

    <Teleport to="body">
      <div v-if="createDlg" class="wo-overlay" @click.self="createDlg = false">
        <div class="wo-modal">
          <h3 class="wo-modal-title">新建工单</h3>
          <div class="wo-modal-field">
            <label class="wo-modal-label" for="wo-create-title">标题 <span class="wo-required">*</span></label>
            <input id="wo-create-title" name="wo-create-title" v-model="createForm.title" class="wo-modal-input" placeholder="请输入工单标题" maxlength="200" />
          </div>
          <div class="wo-modal-field">
            <label class="wo-modal-label">类型</label>
            <div class="kr-cat-dropdown" style="width:100%">
              <button class="kr-cat-selected" style="width:100%;text-align:left;display:flex;align-items:center;justify-content:space-between" @click.stop="createTypeOpen = !createTypeOpen">
                <span>{{ createForm.type }}</span><span>▼</span>
              </button>
              <div v-if="createTypeOpen" class="kr-cat-menu" style="width:100%;min-width:auto">
                <div v-for="opt in createTypeOptions" :key="opt" class="kr-cat-menu-item"
                     :class="{ active: createForm.type === opt }"
                     @click="createForm.type = opt; createTypeOpen = false">
                  {{ opt }}
                </div>
              </div>
            </div>
          </div>
          <div class="wo-modal-field">
            <label class="wo-modal-label" for="wo-create-priority">优先级</label>
            <select id="wo-create-priority" name="wo-create-priority" v-model="createForm.priority" class="wo-modal-input">
              <option value="low">低</option><option value="medium">中</option><option value="high">高</option>
            </select>
          </div>
          <div class="wo-modal-field">
            <label class="wo-modal-label" for="wo-create-desc">描述 <span class="wo-required">*</span></label>
            <textarea id="wo-create-desc" name="wo-create-desc" v-model="createForm.description" class="wo-modal-input wo-modal-textarea" rows="4" placeholder="请输入工单描述"></textarea>
          </div>
          <div class="wo-modal-acts">
            <button class="wo-btn wo-btn-ghost" @click="createDlg = false">取消</button>
            <button class="wo-btn wo-btn-brand" @click="submitCreate" :disabled="store.submitting">{{ store.submitting ? '提交中…' : '创建工单' }}</button>
          </div>
        </div>
      </div>
      <div v-if="transferDlg" class="wo-overlay" @click.self="transferDlg = false">
        <div class="wo-modal">
          <h3 class="wo-modal-title">转移工单 #{{ selected?.id }}</h3>
          <div class="wo-modal-field">
            <label class="wo-modal-label" for="wo-transfer-target">目标处理人</label>
            <input id="wo-transfer-target" name="wo-transfer-target" v-model="transferTarget" class="wo-modal-input" placeholder="请输入处理人ID（数字）" />
          </div>
          <div class="wo-modal-field">
            <label class="wo-modal-label" for="wo-transfer-reason">转移原因</label>
            <textarea id="wo-transfer-reason" name="wo-transfer-reason" v-model="transferReason" class="wo-modal-input wo-modal-textarea" rows="3" placeholder="请输入转移原因"></textarea>
          </div>
          <div class="wo-modal-acts">
            <button class="wo-btn wo-btn-ghost" @click="transferDlg = false">取消</button>
            <button class="wo-btn wo-btn-brand" @click="confirmTransfer">确认转移</button>
          </div>
        </div>
      </div>
      <div v-if="noteDlg" class="wo-overlay" @click.self="noteDlg = false">
        <div class="wo-modal">
          <h3 class="wo-modal-title">记录备注 — 工单 #{{ selected?.id }}</h3>
          <div class="wo-modal-field">
            <label class="wo-modal-label" for="wo-note-content">备注内容（仅客服内部可见）</label>
            <textarea id="wo-note-content" name="wo-note-content" v-model="noteContent" class="wo-modal-input wo-modal-textarea" rows="4" placeholder="输入内部备注…"></textarea>
          </div>
          <div class="wo-modal-acts">
            <button class="wo-btn wo-btn-ghost" @click="noteDlg = false">取消</button>
            <button class="wo-btn wo-btn-brand" @click="submitNote" :disabled="!noteContent.trim()">保存备注</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useWorkOrderStore } from '@/shared/stores/workOrderStore'
import { useAuthStore } from '@/shared/stores/authStore'
import http from '@/core/axios'
import { ElMessage } from 'element-plus'

const store = useWorkOrderStore()
const authStore = useAuthStore()

const searchKeyword = ref('')
const statusFilter = ref('all')
const selected = ref(null)
const selectedId = ref(null)
const currentTimestamp = ref(Date.now())
const editStatus = ref('pending')
const editingTags = ref(false)
const editTagsValue = ref('')
const auditLogs = ref([])
const auditLoading = ref(false)

const createDlg = ref(false)
const createForm = ref({ title: '', type: '售后', priority: 'medium', description: '' })
const createTypeOpen = ref(false)
const createTypeOptions = ['售后', '咨询', '投诉', '建议', '其他']
const transferDlg = ref(false)
const transferTarget = ref('')
const transferReason = ref('')
const noteDlg = ref(false)
const noteContent = ref('')

const slaDropdownVisible = ref(false)
const slaPauseReasons = [
  { label: '等待客户回复', value: 'CUSTOMER_WAITING' },
  { label: '等待第三方', value: 'THIRD_PARTY' },
  { label: '手动挂起', value: 'MANUAL_HOLD' }
]

const showMiniChat = ref(false)
const miniChatMessages = ref([])
const miniComposeText = ref('')
const miniChatBody = ref(null)
const chatEnding = ref(false)

const phoneRevealed = ref(false)
const fullPhone = ref('')
const phoneMasked = computed(() => {
  const p = selected.value?.userPhone || ''
  if (p.length < 7) return p
  return p.substring(0, 3) + '****' + p.substring(p.length - 4)
})
const phoneDisplay = computed(() => phoneRevealed.value ? fullPhone.value : phoneMasked.value)

const togglePhoneView = async () => {
  if (phoneRevealed.value) {
    phoneRevealed.value = false
    return
  }
  try {
    const res = await http.get(`/api/work-orders/${selected.value.id}/user-phone?agentId=${authStore.userId}`)
    if (res.data.code === 200) {
      fullPhone.value = res.data.data.phone || selected.value.userPhone
      phoneRevealed.value = true
    }
  } catch (err) {
    ElMessage.error('获取手机号失败')
  }
}

const copyPhone = () => {
  navigator.clipboard.writeText(fullPhone.value).then(() => ElMessage.success('已复制'))
}

const popupX = ref(200)
const popupY = ref(100)
const popupW = ref(420)
const popupH = ref(380)
const isDragging = ref(false)
const isResizing = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const resizeStartW = ref(0)
const resizeStartH = ref(0)

const startDrag = (e) => { if (e.target.closest('button')) return; isDragging.value = true; dragStartX.value = e.clientX - popupX.value; dragStartY.value = e.clientY - popupY.value; document.addEventListener('mousemove', onDrag); document.addEventListener('mouseup', stopDrag) }
const startDragTouch = (e) => { const t = e.touches[0]; isDragging.value = true; dragStartX.value = t.clientX - popupX.value; dragStartY.value = t.clientY - popupY.value; document.addEventListener('touchmove', onDragTouch); document.addEventListener('touchend', stopDrag) }
const onDrag = (e) => { if (!isDragging.value) return; popupX.value = Math.max(0, Math.min(window.innerWidth - popupW.value, e.clientX - dragStartX.value)); popupY.value = Math.max(0, Math.min(window.innerHeight - 40, e.clientY - dragStartY.value)) }
const onDragTouch = (e) => { if (!isDragging.value) return; const t = e.touches[0]; popupX.value = Math.max(0, Math.min(window.innerWidth - popupW.value, t.clientX - dragStartX.value)); popupY.value = Math.max(0, Math.min(window.innerHeight - 40, t.clientY - dragStartY.value)) }
const stopDrag = () => { isDragging.value = false; document.removeEventListener('mousemove', onDrag); document.removeEventListener('mouseup', stopDrag); document.removeEventListener('touchmove', onDragTouch); document.removeEventListener('touchend', stopDrag) }

const startResize = (e) => { e.stopPropagation(); isResizing.value = true; resizeStartW.value = popupW.value; resizeStartH.value = popupH.value; dragStartX.value = e.clientX; dragStartY.value = e.clientY; document.addEventListener('mousemove', onResize); document.addEventListener('mouseup', stopResize) }
const startResizeTouch = (e) => { const t = e.touches[0]; isResizing.value = true; resizeStartW.value = popupW.value; resizeStartH.value = popupH.value; dragStartX.value = t.clientX; dragStartY.value = t.clientY; document.addEventListener('touchmove', onResizeTouch); document.addEventListener('touchend', stopResize) }
const onResize = (e) => { if (!isResizing.value) return; popupW.value = Math.max(320, resizeStartW.value + e.clientX - dragStartX.value); popupH.value = Math.max(240, resizeStartH.value + e.clientY - dragStartY.value) }
const onResizeTouch = (e) => { if (!isResizing.value) return; const t = e.touches[0]; popupW.value = Math.max(320, resizeStartW.value + t.clientX - dragStartX.value); popupH.value = Math.max(240, resizeStartH.value + t.clientY - dragStartY.value) }
const stopResize = () => { isResizing.value = false; document.removeEventListener('mousemove', onResize); document.removeEventListener('mouseup', stopResize); document.removeEventListener('touchmove', onResizeTouch); document.removeEventListener('touchend', stopResize) }

const priorityOrder = { high: 0, medium: 1, low: 2 }
const priorityLabel = (p) => ({ high: '高', medium: '中', low: '低' }[p] || p)
const statusLabel = (s) => ({ pending: '待处理', processing: '处理中', completed: '已完成', cancelled: '已取消', closed: '已关闭' }[s] || s)

const totalPages = computed(() => Math.max(1, Math.ceil(store.totalCount / store.pageSize)))

const statusCounts = computed(() => {
  const counts = { all: store.workOrders.length, pending: 0, processing: 0, completed: 0 }
  store.workOrders.forEach(w => { if (counts[w.status] !== undefined) counts[w.status]++ })
  return counts
})

const statusFilterOptions = computed(() => [
  { value: 'all', label: '全部', count: statusCounts.value.all },
  { value: 'pending', label: '待处理', count: statusCounts.value.pending },
  { value: 'processing', label: '处理中', count: statusCounts.value.processing },
  { value: 'completed', label: '已完成', count: statusCounts.value.completed }
])

const filteredList = computed(() => {
  let list = store.workOrders
  if (searchKeyword.value) { const kw = searchKeyword.value.toLowerCase(); list = list.filter(w => w.title && w.title.toLowerCase().includes(kw)) }
  if (statusFilter.value !== 'all') list = list.filter(w => w.status === statusFilter.value)
  return list
})

const sortedList = computed(() => {
  const emergencyIds = new Set(emergencyOrders.value.map(wo => wo.id))
  return [...filteredList.value]
    .filter(wo => !emergencyIds.has(wo.id))
    .sort((a, b) => (priorityOrder[a.priority] ?? 99) - (priorityOrder[b.priority] ?? 99))
})

const parsedTags = computed(() => {
  if (!selected.value || !selected.value.tags) return []
  return selected.value.tags.split(',').map(t => t.trim()).filter(Boolean)
})

const isSlaPaused = computed(() => {
  return selected.value?.slaPaused === 1 || selected.value?.slaPaused === true
})

const slaPercent = computed(() => {
  if (!selected.value?.slaDeadline) return 100
  const now = currentTimestamp.value
  const deadline = new Date(selected.value.slaDeadline).getTime()
  const created = selected.value.createTime ? new Date(selected.value.createTime).getTime() : now
  if (now >= deadline) return 0
  return Math.round(((deadline - now) / Math.max(1, deadline - created)) * 100)
})

const slaLevel = computed(() => {
  if (!selected.value?.slaDeadline) return 'none'
  if (slaPercent.value <= 0) return 'breach'
  if (slaPercent.value <= 15) return 'critical'
  if (slaPercent.value <= 25) return 'warning'
  if (slaPercent.value <= 50) return 'caution'
  return 'healthy'
})

const slaRemaining = computed(() => {
  if (!selected.value?.slaDeadline) return ''
  const ms = new Date(selected.value.slaDeadline).getTime() - currentTimestamp.value
  if (ms <= 0) return '已超时'
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const emergencyOrders = computed(() => {
  const now = currentTimestamp.value
  return [...store.workOrders]
    .filter(wo => wo.slaDeadline && wo.status !== 'completed' && wo.status !== 'cancelled')
    .filter(wo => {
      const ms = new Date(wo.slaDeadline).getTime() - now
      const total = wo.createTime ? new Date(wo.slaDeadline).getTime() - new Date(wo.createTime).getTime() : 1
      return ms <= 0 || (ms / total) <= 0.25
    })
    .sort((a, b) => new Date(a.slaDeadline).getTime() - new Date(b.slaDeadline).getTime())
})

const getEmergencyLevel = (wo) => {
  if (wo.slaPaused === 1 || wo.slaPaused === true) return 'paused'
  if (!wo.slaDeadline) return 'none'
  const ms = new Date(wo.slaDeadline).getTime() - currentTimestamp.value
  if (ms <= 0) return 'breach'
  const total = wo.createTime ? new Date(wo.slaDeadline).getTime() - new Date(wo.createTime).getTime() : 1
  const ratio = ms / total
  if (ratio <= 0.15) return 'critical'
  if (ratio <= 0.25) return 'warning'
  return 'caution'
}

const fmtSlaRemaining = (deadline) => {
  if (!deadline) return ''
  const ms = new Date(deadline).getTime() - currentTimestamp.value
  if (ms <= 0) return '超时'
  const m = Math.floor(ms / 60000)
  if (m >= 60) return `${Math.floor(m / 60)}h${m % 60}m`
  return `${m}m`
}

const getListSlaLevel = (wo) => {
  if (wo.slaPaused === 1 || wo.slaPaused === true) return 'paused'
  if (!wo.slaDeadline || wo.status === 'completed' || wo.status === 'cancelled') return 'none'
  const ms = new Date(wo.slaDeadline).getTime() - currentTimestamp.value
  if (ms <= 0) return 'breach'
  const total = wo.createTime ? new Date(wo.slaDeadline).getTime() - new Date(wo.createTime).getTime() : 1
  const ratio = ms / total
  if (ratio <= 0.15) return 'critical'
  if (ratio <= 0.25) return 'warning'
  if (ratio <= 0.50) return 'caution'
  return 'healthy'
}

const fmtTime = (t) => t ? new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''

const selectOrder = (wo) => {
  selected.value = wo
  selectedId.value = wo.id
  editStatus.value = wo.status
  editingTags.value = false
  fetchAuditLogs(wo.id)
}

const fetchAuditLogs = async (workOrderId) => {
  auditLogs.value = []
  auditLoading.value = true
  try {
    const res = await http.get(`/api/work-orders/${workOrderId}/audit-logs?userVisible=false`)
    if (res.data.code === 200) auditLogs.value = res.data.data || []
  } catch (err) {
    if (import.meta.env.DEV) { console.error('Failed to fetch audit logs:', err) }
  } finally {
    auditLoading.value = false
  }
}

const handleStatusChange = async () => {
  if (!selected.value) return
  const r = await store.updateStatus(selected.value.id, editStatus.value, selected.value.handlerId, selected.value.result || '')
  if (r.success) { selected.value.status = editStatus.value; fetchAuditLogs(selected.value.id); ElMessage.success('状态已更新') }
  else { editStatus.value = selected.value.status; ElMessage.error(r.message || '状态更新失败') }
}

const openCreateDialog = () => { createForm.value = { title: '', type: '售后', priority: 'medium', description: '' }; createDlg.value = true }

const submitCreate = async () => {
  store.newWorkOrder.title = createForm.value.title
  store.newWorkOrder.type = createForm.value.type
  store.newWorkOrder.description = createForm.value.description
  store.newWorkOrder.priority = createForm.value.priority
  const r = await store.submit()
  if (r.success) { ElMessage.success('工单创建成功'); createDlg.value = false; refreshList() }
  else ElMessage.error(r.message || '创建失败')
}

const handleMarkComplete = async () => {
  if (!selected.value) return
  const r = await store.updateStatus(selected.value.id, 'completed', selected.value.handlerId, selected.value.result || '已完成')
  if (r.success) { selected.value.status = 'completed'; editStatus.value = 'completed'; fetchAuditLogs(selected.value.id); ElMessage.success('工单已标记为完成') }
  else ElMessage.error(r.message || '操作失败')
}

const handleClaim = async () => {
  if (!selected.value) return
  const r = await store.claimWorkOrderById(selected.value.id)
  if (r.success) {
    selected.value.handlerId = authStore.userId
    selected.value.status = 'processing'
    editStatus.value = 'processing'
    fetchAuditLogs(selected.value.id)
    ElMessage.success('工单认领成功')
  } else {
    ElMessage.warning(r.message || '该工单已被其他客服认领')
    refreshList()
  }
}

const claimFromCard = async (wo) => {
  const r = await store.claimWorkOrderById(wo.id)
  if (r.success) {
    wo.handlerId = authStore.userId
    wo.status = 'processing'
    if (selected.value && selected.value.id === wo.id) {
      selected.value.handlerId = authStore.userId
      selected.value.status = 'processing'
      editStatus.value = 'processing'
      fetchAuditLogs(wo.id)
    }
    ElMessage.success('工单认领成功')
  } else {
    ElMessage.warning(r.message || '该工单已被其他客服认领')
    refreshList()
  }
}

const submitNote = async () => {
  if (!noteContent.value.trim()) { ElMessage.warning('请输入备注内容'); return }
  if (!selected.value) return
  try {
    const res = await http.post(`/api/work-orders/${selected.value.id}/note`, {
      content: noteContent.value,
      agentId: String(authStore.userId)
    })
    if (res.data.code === 200) {
      ElMessage.success('备注已保存')
      noteContent.value = ''
      noteDlg.value = false
      fetchAuditLogs(selected.value.id)
    } else {
      ElMessage.error(res.data.message || '保存失败')
    }
  } catch (err) {
    ElMessage.error('备注保存失败')
  }
}

const handleTransfer = () => { transferTarget.value = ''; transferReason.value = ''; transferDlg.value = true }

const confirmTransfer = async () => {
  if (!transferTarget.value.trim()) { ElMessage.warning('请输入目标处理人ID'); return }
  if (!selected.value) return
  const targetId = Number(transferTarget.value.trim())
  if (isNaN(targetId) || targetId <= 0) { ElMessage.warning('请输入有效的处理人ID（数字）'); return }
  const r = await store.transferToAgent(selected.value.id, targetId, transferReason.value)
  if (r.success) { ElMessage.success(`工单已转移至处理人 #${targetId}`); transferDlg.value = false; selected.value.handlerId = targetId; fetchAuditLogs(selected.value.id) }
  else ElMessage.error(r.message || '转移失败')
}

const handlePauseSla = async (reason) => {
  slaDropdownVisible.value = false
  if (!selected.value) return
  const r = await store.pauseSlaAction(selected.value.id, reason, String(authStore.userId))
  if (r.success) {
    if (selected.value) selected.value.slaPaused = 1
    ElMessage.success('SLA 已暂停')
    fetchAuditLogs(selected.value.id)
  } else {
    ElMessage.error(r.message || '暂停SLA失败')
  }
}

const handleResumeSla = async () => {
  if (!selected.value) return
  const r = await store.resumeSlaAction(selected.value.id, String(authStore.userId))
  if (r.success) {
    if (selected.value) {
      selected.value.slaPaused = 0
      if (r.data) {
        if (r.data.effectiveResponseSeconds !== undefined) selected.value.effectiveResponseSeconds = r.data.effectiveResponseSeconds
        if (r.data.effectiveResolutionSeconds !== undefined) selected.value.effectiveResolutionSeconds = r.data.effectiveResolutionSeconds
        if (r.data.slaDeadline) selected.value.slaDeadline = r.data.slaDeadline
        if (r.data.responseDeadline) selected.value.responseDeadline = r.data.responseDeadline
      }
    }
    ElMessage.success('SLA 已恢复')
    fetchAuditLogs(selected.value.id)
  } else {
    ElMessage.error(r.message || '恢复SLA失败')
  }
}

const saveTags = async () => {
  if (!selected.value) return
  selected.value.tags = editTagsValue.value
  const r = await store.updateStatus(selected.value.id, selected.value.status, selected.value.handlerId, selected.value.result || '')
  editingTags.value = false
  ElMessage.success('标签已更新')
}

const goPage = (page) => {
  store.fetchAllWorkOrders(page)
  const panel = document.querySelector('.wo-list-scroll')
  if (panel) panel.scrollTop = 0
}

const exportOrders = () => {
  const headers = ['ID', '标题', '类型', '优先级', '状态', '标签', 'AI摘要', '创建时间']
  const rows = sortedList.value.map(w => [w.id, w.title || '', w.type || '', priorityLabel(w.priority), statusLabel(w.status), w.tags || '', w.summary || '', w.createTime || ''])
  const csvContent = [headers, ...rows].map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = `工单导出_${new Date().toISOString().slice(0, 10)}.csv`
  link.click(); URL.revokeObjectURL(url); ElMessage.success('导出成功')
}

const openMiniChat = async () => {
  if (!selected.value?.sessionId) {
    ElMessage.warning('该工单未关联会话，无法发起沟通')
    return
  }
  showMiniChat.value = true
  miniChatMessages.value = [{ role: 'system', content: '已接入用户' }]
  popupX.value = Math.max(0, (window.innerWidth - popupW.value) / 2)
  popupY.value = Math.max(0, (window.innerHeight - popupH.value) / 2)
  try {
    await http.post(`/api/work-orders/${selected.value.id}/connect-session?agentId=${authStore.userId}`)
  } catch (err) {
    if (import.meta.env.DEV) { console.error('Failed to connect session:', err) }
  }
  chatStartTime.value = Date.now()
  lastPollTime.value = 0
  startChatPoll()
}

const startChatPoll = () => {
  stopChatPoll()
  chatPollTimer = setInterval(async () => {
    if (!showMiniChat.value || !selected.value?.sessionId) { stopChatPoll(); return }
    try {
      const res = await http.get(`/api/chat/session/${selected.value.sessionId}/full-history?since=${encodeURIComponent(new Date(lastPollTime.value).toISOString())}`)
      if (res.data.code === 200 && res.data.data) {
        const newMessages = res.data.data
          .filter(m => m.role === 'user')
          .filter(m => {
            const msgTime = m.time || m.createTime
            return msgTime && new Date(msgTime).getTime() >= chatStartTime.value
          })
          .map(m => ({ role: 'user', content: m.content || '' }))
        if (newMessages.length > 0) {
          const existingContents = new Set(miniChatMessages.value.map(m => m.content))
          const unique = newMessages.filter(m => !existingContents.has(m.content))
          if (unique.length > 0) {
            miniChatMessages.value.push(...unique)
            nextTick(() => { if (miniChatBody.value) miniChatBody.value.scrollTop = miniChatBody.value.scrollHeight })
          }
        }
      }
      lastPollTime.value = Date.now()
    } catch (ignored) {
      if (import.meta.env.DEV) { console.debug('Chat poll skipped:', ignored) }
    }
  }, 2000)
}

const stopChatPoll = () => {
  if (chatPollTimer) { clearInterval(chatPollTimer); chatPollTimer = null }
}

const sendMiniMsg = () => {
  const content = miniComposeText.value.trim()
  if (!content || !selected.value?.sessionId) return
  miniChatMessages.value.push({ role: 'agent', content })
  miniComposeText.value = ''
  if (store.replyToWorkOrder) {
    store.replyToWorkOrder(selected.value.id, content, authStore.userId).catch(() => {})
  }
  nextTick(() => {
    if (miniChatBody.value) miniChatBody.value.scrollTop = miniChatBody.value.scrollHeight
  })
}

const endMiniSession = async () => {
  if (!selected.value?.sessionId) return
  chatEnding.value = true
  stopChatPoll()
  try {
    await http.post(`/api/work-orders/${selected.value.id}/close-session?agentId=${authStore.userId}`)
    miniChatMessages.value.push({ role: 'system', content: '服务结束' })
    ElMessage.success('会话已结束')
  } catch (ignored) {
    if (import.meta.env.DEV) { console.debug('Close session failed:', ignored) }
    miniChatMessages.value.push({ role: 'system', content: '服务结束' })
  } finally {
    chatEnding.value = false
  }
}

const refreshList = () => store.fetchAllWorkOrders()

let slaTimer = null
let pollTimer = null
let chatPollTimer = null
const lastPollTime = ref(0)
const chatStartTime = ref(0)

const closeSlaDropdown = (e) => {
  if (!slaDropdownVisible.value && !createTypeOpen.value) return
  if (e.target.closest('.wo-sla-dropdown') || e.target.closest('.kr-cat-dropdown')) return
  slaDropdownVisible.value = false
  createTypeOpen.value = false
}

watch(() => store.workOrders, (newList) => {
  if (selectedId.value && newList.length > 0) {
    const found = newList.find(w => w.id === selectedId.value)
    if (found) { selected.value = found; editStatus.value = found.status }
    else { selected.value = null; selectedId.value = null }
  }
})

watch(editingTags, (val) => { if (val) editTagsValue.value = selected.value?.tags || '' })

onMounted(() => {
  store.fetchAllWorkOrders()
  slaTimer = setInterval(() => currentTimestamp.value = Date.now(), 1000)
  pollTimer = setInterval(() => store.fetchAllWorkOrders(), 5000)
  document.addEventListener('click', closeSlaDropdown)
})

onBeforeUnmount(() => {
  if (slaTimer) { clearInterval(slaTimer); slaTimer = null }
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (chatPollTimer) { clearInterval(chatPollTimer); chatPollTimer = null }
  document.removeEventListener('click', closeSlaDropdown)
})
</script>

<style scoped>
.wo-shell { display: flex; flex-direction: column; height: 100%; background: var(--base); }
.wo-topbar { display: flex; align-items: center; justify-content: space-between; padding: var(--s-5) var(--s-6); flex-shrink: 0; border-bottom: 1px solid var(--border-light); background: var(--surface); }
.wo-topbar-title { font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }
.wo-topbar-actions { display: flex; gap: var(--s-3); }
.wo-btn { display: inline-flex; align-items: center; gap: var(--s-2); padding: var(--s-2) var(--s-4); border: none; border-radius: var(--radius-md); font-size: var(--text-sm); font-weight: var(--weight-medium); font-family: var(--font-body); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); white-space: nowrap; }
.wo-btn-brand { background: var(--brand); color: #fff; }
.wo-btn-brand:hover { background: var(--brand-deep); }
.wo-btn-claim { background: var(--brand); color: #fff; font-size: var(--text-xs); padding: var(--s-1) var(--s-3); }
.wo-btn-claim:hover { background: var(--brand-deep); }
.wo-btn-outline { background: var(--surface); color: var(--ink-soft); border: 1.5px solid var(--border); }
.wo-btn-outline:hover { border-color: var(--ink-muted); color: var(--ink); }
.wo-btn-ghost-sm { background: transparent; color: var(--ink-muted); gap: var(--s-1); padding: var(--s-1) var(--s-3); font-size: var(--text-xs); border: 1px solid var(--border-light); border-radius: var(--radius-sm); cursor: pointer; font-family: var(--font-body); }
.wo-btn-ghost-sm:hover { color: var(--ink); border-color: var(--ink-muted); }
.wo-body { display: flex; flex: 1; min-height: 0; overflow: hidden; }
.wo-list-panel { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; border-right: 1px solid var(--border-light); background: var(--surface); }
.wo-list-search { display: flex; align-items: center; gap: var(--s-2); padding: var(--s-4) var(--s-4) var(--s-2); }
.wo-search-icon { color: var(--ink-muted); flex-shrink: 0; }
.wo-search-input { flex: 1; border: none; background: transparent; font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); outline: none; padding: var(--s-1) 0; }
.wo-search-input::placeholder { color: var(--ink-muted); }
.wo-list-filter-bar { display: flex; gap: var(--s-1); padding: 0 var(--s-4) var(--s-3); flex-wrap: wrap; }
.wo-filter-chip { padding: 2px 10px; border: 1px solid var(--border-light); border-radius: var(--radius-full); background: transparent; font-size: var(--text-2xs); font-family: var(--font-body); color: var(--ink-soft); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); display: inline-flex; align-items: center; gap: 4px; }
.wo-filter-chip:hover { border-color: var(--brand); color: var(--brand); }
.wo-filter-chip--active { background: var(--brand-soft); border-color: var(--brand); color: var(--brand-deep); font-weight: var(--weight-medium); }
.wo-filter-chip-count { font-size: var(--text-3xs); color: inherit; opacity: 0.7; }
.wo-list-scroll { flex: 1; overflow-y: auto; padding: 0 var(--s-3) var(--s-3); display: flex; flex-direction: column; gap: var(--s-1); }
.wo-list-empty { text-align: center; padding: var(--s-12) var(--s-4); font-size: var(--text-sm); color: var(--ink-muted); }
.wo-list-item { display: flex; flex-direction: column; gap: 2px; padding: var(--s-3) var(--s-4); border: none; border-radius: var(--radius-md); background: transparent; cursor: pointer; text-align: left; font-family: var(--font-body); transition: all var(--dur-fast) var(--ease-soft); width: 100%; border-left: 3px solid transparent; }
.wo-list-item:hover { background: var(--base); }
.wo-list-item--active { background: var(--brand-pale); box-shadow: inset 3px 0 0 var(--brand); }

.wo-li-sla--healthy { border-left-color: oklch(0.50 0.150 150); background: linear-gradient(90deg, oklch(0.50 0.150 150 / 0.06) 0%, transparent 40%); }
.wo-li-sla--caution { border-left-color: oklch(0.62 0.160 75); background: linear-gradient(90deg, oklch(0.62 0.160 75 / 0.08) 0%, transparent 40%); }
.wo-li-sla--warning { border-left-color: oklch(0.65 0.18 50); background: linear-gradient(90deg, oklch(0.65 0.18 50 / 0.10) 0%, transparent 40%); }
.wo-li-sla--critical { border-left-color: oklch(0.50 0.170 20); background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.10) 0%, transparent 40%); }
.wo-li-sla--breach { border-left-color: oklch(0.50 0.170 20); background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.12) 0%, transparent 40%); animation: wo-li-blink 0.8s ease-in-out infinite; }
@keyframes wo-li-blink { 50% { border-left-color: oklch(0.50 0.170 20 / 0.3); } }

.wo-li-sla--paused { border-left-color: oklch(0.55 0.12 260); background: linear-gradient(90deg, oklch(0.55 0.12 260 / 0.06) 0%, transparent 40%); }

.wo-li-sla--healthy:hover { background: linear-gradient(90deg, oklch(0.50 0.150 150 / 0.10) 0%, var(--base) 40%); }
.wo-li-sla--caution:hover { background: linear-gradient(90deg, oklch(0.62 0.160 75 / 0.14) 0%, var(--base) 40%); }
.wo-li-sla--warning:hover { background: linear-gradient(90deg, oklch(0.65 0.18 50 / 0.16) 0%, var(--base) 40%); }
.wo-li-sla--critical:hover { background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.16) 0%, var(--base) 40%); }
.wo-li-sla--breach:hover { background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.18) 0%, var(--base) 40%); }

.wo-li-sla--paused:hover { background: linear-gradient(90deg, oklch(0.55 0.12 260 / 0.10) 0%, var(--base) 40%); }

.wo-list-item--active.wo-li-sla--healthy { background: linear-gradient(90deg, oklch(0.50 0.150 150 / 0.10) 0%, var(--brand-pale) 40%); border-left-color: oklch(0.50 0.150 150); box-shadow: inset 3px 0 0 oklch(0.50 0.150 150); }
.wo-list-item--active.wo-li-sla--caution { background: linear-gradient(90deg, oklch(0.62 0.160 75 / 0.14) 0%, var(--brand-pale) 40%); border-left-color: oklch(0.62 0.160 75); box-shadow: inset 3px 0 0 oklch(0.62 0.160 75); }
.wo-list-item--active.wo-li-sla--warning { background: linear-gradient(90deg, oklch(0.65 0.18 50 / 0.16) 0%, var(--brand-pale) 40%); border-left-color: oklch(0.65 0.18 50); box-shadow: inset 3px 0 0 oklch(0.65 0.18 50); }
.wo-list-item--active.wo-li-sla--critical { background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.16) 0%, var(--brand-pale) 40%); border-left-color: oklch(0.50 0.170 20); box-shadow: inset 3px 0 0 oklch(0.50 0.170 20); }
.wo-list-item--active.wo-li-sla--breach { background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.18) 0%, var(--brand-pale) 40%); border-left-color: oklch(0.50 0.170 20); box-shadow: inset 3px 0 0 oklch(0.50 0.170 20); }
.wo-list-item--active.wo-li-sla--paused { background: linear-gradient(90deg, oklch(0.55 0.12 260 / 0.10) 0%, var(--brand-pale) 40%); border-left-color: oklch(0.55 0.12 260); box-shadow: inset 3px 0 0 oklch(0.55 0.12 260); }
.wo-list-item-content { display: flex; align-items: center; gap: var(--s-2); }
.wo-list-item-id { font-family: var(--font-mono); font-size: var(--text-xs); color: var(--ink-soft); font-weight: var(--weight-medium); }
.wo-list-item-badge { font-size: var(--text-3xs); font-weight: var(--weight-semibold); padding: 1px 6px; border-radius: var(--radius-full); text-transform: uppercase; letter-spacing: 0.02em; }
.wo-pri-badge--high { background: var(--danger-soft); color: var(--danger); }
.wo-pri-badge--medium { background: var(--warning-soft); color: var(--warning); }
.wo-pri-badge--low { background: var(--success-soft); color: var(--success); }
.wo-list-item-title { font-size: var(--text-sm); color: var(--ink); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wo-list-item-status { font-size: var(--text-2xs); color: var(--ink-muted); }
.wo-list-item-foot { display: flex; align-items: center; justify-content: space-between; }
.wo-btn-claim-card { padding: 2px 10px; font-size: var(--text-3xs); font-weight: var(--weight-semibold); border: 1.5px solid var(--brand); border-radius: var(--radius-full); background: var(--brand-soft); color: var(--brand); cursor: pointer; transition: all var(--dur-fast); font-family: var(--font-body); white-space: nowrap; }
.wo-btn-claim-card:hover { background: var(--brand); color: #fff; }
.wo-list-pager { display: flex; align-items: center; justify-content: center; gap: var(--s-3); padding: var(--s-3); border-top: 1px solid var(--border-light); }
.wo-pager-btn { padding: var(--s-1) var(--s-3); border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--surface); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink-soft); cursor: pointer; transition: all var(--dur-fast); }
.wo-pager-btn:hover:not(:disabled) { border-color: var(--brand); color: var(--brand); }
.wo-pager-btn:disabled { opacity: 0.4; cursor: default; }
.wo-pager-info { font-size: var(--text-xs); color: var(--ink-soft); }
.wo-detail-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow-y: auto; padding: var(--s-5) var(--s-6); gap: var(--s-3); }
.wo-detail-empty { flex: 1; display: flex; align-items: center; justify-content: center; }
.wo-empty-state { text-align: center; display: flex; flex-direction: column; align-items: center; gap: var(--s-3); }
.wo-empty-title { font-size: var(--text-lg); font-weight: var(--weight-medium); color: var(--ink-soft); margin: 0; }
.wo-empty-hint { font-size: var(--text-sm); color: var(--ink-muted); margin: 0; }
.wo-detail-header { display: flex; align-items: center; justify-content: space-between; }
.wo-detail-id { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }
.wo-sla-bar { position: relative; height: 6px; background: var(--border-light); border-radius: var(--radius-full); overflow: visible; flex-shrink: 0; }
.wo-sla-progress { height: 100%; border-radius: var(--radius-full); transition: width 1s linear; }
.wo-sla-progress--healthy { background: var(--success); }
.wo-sla-progress--caution { background: var(--warning); }
.wo-sla-progress--warning { background: oklch(0.65 0.18 50); }
.wo-sla-progress--critical { background: var(--danger); }
.wo-sla-progress--breach { background: var(--danger); animation: blink 0.8s ease-in-out infinite; }
@keyframes blink { 50% { opacity: 0.5; } }
.wo-sla-bar--done { background: var(--success-soft); }
.wo-sla-progress--done { background: var(--success); }
.wo-sla-label--done { color: var(--success); font-weight: var(--weight-semibold); }
.wo-sla-label { position: absolute; top: 8px; right: 0; font-size: var(--text-3xs); font-weight: var(--weight-semibold); color: var(--ink-muted); }

.wo-sla-bar--paused { background: var(--border-light); }
.wo-sla-progress--paused { background: oklch(0.55 0.12 260 / 0.4); }
.wo-sla-label--paused { color: oklch(0.50 0.10 260); }

/* ── Three-column layout ── */
.wo-detail-three-col {
  display: flex; gap: 18px; background: var(--surface);
  border: 1px solid var(--border-light); border-radius: var(--radius-lg);
  padding: var(--s-5);
}
.wo-col-section-label {
  font-size: var(--text-3xs); font-weight: var(--weight-bold);
  color: var(--brand); text-transform: uppercase; letter-spacing: 0.08em;
  margin-bottom: var(--s-3);
}
.wo-col-left { flex: 0 0 220px; display: flex; flex-direction: column; gap: var(--s-3); }
.wo-col-mid { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.wo-col-right { flex: 0 0 210px; display: flex; flex-direction: column; }

.wo-meta-title { font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--ink); }
.wo-meta-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 12px; }
.wo-meta-cell { display: flex; flex-direction: column; gap: var(--s-1); }
.wo-detail-label { font-size: var(--text-2xs); font-weight: var(--weight-semibold); color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.05em; }
.wo-detail-value { font-size: var(--text-sm); color: var(--ink); }
.wo-phone-eye-btn, .wo-phone-copy-btn { padding: 1px 4px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: transparent; font-size: var(--text-xs); cursor: pointer; font-family: var(--font-body); line-height: 1; }
.wo-phone-eye-btn:hover, .wo-phone-copy-btn:hover { border-color: var(--brand); background: var(--brand-pale); }
.wo-detail-select { padding: var(--s-1) var(--s-3); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base); cursor: pointer; min-width: 100px; }
.wo-detail-select:focus { border-color: var(--brand); outline: none; }

.wo-desc-box {
  background: var(--base); border: 1px solid var(--border-light);
  border-radius: var(--radius-md); padding: var(--s-3);
}
.wo-desc-badge {
  font-size: var(--text-3xs); font-weight: var(--weight-bold); color: var(--brand);
  text-transform: uppercase; letter-spacing: 0.05em; display: block; margin-bottom: var(--s-1);
}
.wo-detail-desc-text {
  font-size: var(--text-xs); color: var(--ink-soft); line-height: var(--leading-snug);
  margin: 0; max-height: 52px; overflow-y: auto;
}

.wo-tags-row { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.wo-tag { font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 8px; border-radius: var(--radius-full); }
.wo-tag-pri--high { background: var(--danger-soft); color: var(--danger); }
.wo-tag-pri--medium { background: var(--warning-soft); color: var(--warning); }
.wo-tag-pri--low { background: var(--success-soft); color: var(--success); }
.wo-tag-st--pending { background: var(--warning-soft); color: var(--warning); }
.wo-tag-st--processing { background: var(--brand-soft); color: var(--brand-deep); }
.wo-tag-st--completed { background: var(--success-soft); color: var(--success); }
.wo-tag-st--cancelled { background: var(--danger-soft); color: var(--danger); }
.wo-tag--dify { background: oklch(0.88 0.05 50); color: oklch(0.45 0.10 50); }
.wo-tag-edit-toggle { padding: 1px 6px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: transparent; font-size: var(--text-3xs); font-family: var(--font-body); color: var(--ink-muted); cursor: pointer; margin-left: auto; }
.wo-tags-input { flex: 1; padding: var(--s-1) var(--s-2); border: 1px solid var(--brand); border-radius: var(--radius-sm); font-size: var(--text-xs); font-family: var(--font-body); color: var(--ink); background: var(--base); outline: none; min-width: 140px; }

.wo-ai-card-new {
  flex: 1; min-height: 0; background: oklch(0.95 0.04 275 / 0.12);
  border: 1px solid oklch(0.88 0.06 275 / 0.25);
  border-radius: var(--radius-lg); padding: var(--s-5);
  display: flex; flex-direction: column; gap: var(--s-4);
}
.wo-ai-head-row { display: flex; align-items: center; gap: var(--s-2); flex-shrink: 0; }
.wo-ai-icon { font-size: 18px; line-height: 1; }
.wo-ai-title { font-size: var(--text-xs); font-weight: var(--weight-bold); color: oklch(0.4 0.10 275); text-transform: uppercase; letter-spacing: 0.05em; }
.wo-ai-summary-new { font-size: var(--text-sm); color: var(--ink); line-height: var(--leading-relaxed); margin: 0; flex: 1; min-height: 0; overflow-y: auto; }
.wo-ai-summary-placeholder { color: var(--ink-muted); font-style: italic; }
.wo-ai-tags-row { display: flex; flex-wrap: wrap; gap: var(--s-2); flex-shrink: 0; }
.wo-ai-tag-new { font-size: var(--text-2xs); padding: 3px 10px; background: oklch(0.55 0.12 275 / 0.12); color: oklch(0.4 0.10 275); border-radius: var(--radius-full); font-weight: var(--weight-medium); }

.wo-audit-list {
  flex: 1; display: flex; flex-direction: column; gap: 0;
  max-height: 280px; overflow-y: auto;
}
.wo-audit-empty { text-align: center; padding: var(--s-6) 0; font-size: var(--text-xs); color: var(--ink-muted); }
.wo-audit-item {
  display: flex; gap: 8px; padding: 7px 0 7px 12px;
  border-left: 2px solid var(--border-light); position: relative;
}
.wo-audit-item::before {
  content: ''; position: absolute; left: -5px; top: 10px;
  width: 8px; height: 8px; border-radius: 50%; background: var(--ink-muted);
}
.wo-audit--submit::before { background: var(--brand); }
.wo-audit--ai_analysis::before { background: oklch(0.55 0.10 200); }
.wo-audit--dispatch::before { background: var(--brand); }
.wo-audit--status_change::before { background: var(--success); }
.wo-audit--note::before { background: var(--agent); }
.wo-audit--complete::before, .wo-audit--cancel::before { background: var(--danger); }
.wo-audit--sla_pause_log::before { background: oklch(0.55 0.12 260); }

.wo-audit-body { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.wo-audit-time { font-size: var(--text-3xs); color: var(--ink-muted); }
.wo-audit-action { font-size: var(--text-xs); color: var(--ink); font-weight: var(--weight-semibold); }
.wo-audit-detail { font-size: var(--text-2xs); color: var(--ink-soft); line-height: var(--leading-snug); }

.wo-action-bar {
  display: flex; gap: var(--s-3); padding: var(--s-4) var(--s-5);
  background: oklch(0.98 0.005 240); border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
}

.wo-sla-dropdown { position: relative; }
.wo-btn-sla-pause { color: oklch(0.50 0.12 260); border-color: oklch(0.55 0.12 260 / 0.4); }
.wo-btn-sla-pause:hover { border-color: oklch(0.50 0.12 260); color: oklch(0.45 0.12 260); background: oklch(0.55 0.12 260 / 0.06); }
.wo-btn-sla-resume { background: oklch(0.55 0.12 260); color: #fff; }
.wo-btn-sla-resume:hover { background: oklch(0.45 0.12 260); }
.wo-sla-dropdown-menu {
  position: absolute; bottom: calc(100% + 4px); left: 0;
  background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius-md); box-shadow: var(--shadow-md);
  min-width: 160px; z-index: 100; overflow: hidden;
}
.wo-sla-dropdown-item {
  display: block; width: 100%; padding: var(--s-2) var(--s-4);
  border: none; background: transparent; font-size: var(--text-sm);
  font-family: var(--font-body); color: var(--ink); cursor: pointer;
  text-align: left; transition: background var(--dur-fast);
}
.wo-sla-dropdown-item:hover { background: var(--base); }
.wo-sla-dropdown-item + .wo-sla-dropdown-item { border-top: 1px solid var(--border-light); }

/* ── Emergency Panel ── */
.wo-emergency-panel { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; border-left: 1px solid var(--danger-soft); background: var(--surface); }
.wo-emergency-head { padding: var(--s-4) var(--s-4) var(--s-3); border-bottom: 1px solid var(--danger-soft); background: var(--danger-soft); display: flex; align-items: center; gap: var(--s-2); }
.wo-emergency-head-icon { font-size: 16px; }
.wo-emergency-head-text { font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--danger); }
.wo-emergency-head-count { font-weight: var(--weight-bold); }
.wo-emergency-list { flex: 1; overflow-y: auto; padding: 0 var(--s-3) var(--s-3); display: flex; flex-direction: column; gap: var(--s-1); }
.wo-emergency-empty { text-align: center; padding: var(--s-12) var(--s-4); font-size: var(--text-sm); color: var(--ink-muted); }

/* SLA border overrides — red-themed */
.wo-em-sl--healthy { border-left-color: var(--danger); background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.06) 0%, transparent 40%); }
.wo-em-sl--caution { border-left-color: var(--warning); background: linear-gradient(90deg, oklch(0.62 0.160 40 / 0.08) 0%, transparent 40%); }
.wo-em-sl--warning { border-left-color: oklch(0.60 0.18 35); background: linear-gradient(90deg, oklch(0.60 0.18 35 / 0.10) 0%, transparent 40%); }
.wo-em-sl--critical { border-left-color: oklch(0.48 0.175 20); background: linear-gradient(90deg, oklch(0.48 0.175 20 / 0.12) 0%, transparent 40%); }
.wo-em-sl--breach { border-left-color: oklch(0.48 0.175 20); background: linear-gradient(90deg, oklch(0.48 0.175 20 / 0.14) 0%, transparent 40%); animation: wo-em-blink 0.8s ease-in-out infinite; }
@keyframes wo-em-blink { 50% { border-left-color: oklch(0.48 0.175 20 / 0.25); } }

.wo-em-sl--paused { border-left-color: oklch(0.55 0.12 260); background: linear-gradient(90deg, oklch(0.55 0.12 260 / 0.06) 0%, transparent 40%); }

.wo-em-sl--healthy:hover { background: linear-gradient(90deg, oklch(0.50 0.170 20 / 0.10) 0%, var(--base) 40%); }
.wo-em-sl--caution:hover { background: linear-gradient(90deg, oklch(0.62 0.160 40 / 0.14) 0%, var(--base) 40%); }
.wo-em-sl--warning:hover { background: linear-gradient(90deg, oklch(0.60 0.18 35 / 0.16) 0%, var(--base) 40%); }
.wo-em-sl--critical:hover { background: linear-gradient(90deg, oklch(0.48 0.175 20 / 0.18) 0%, var(--base) 40%); }
.wo-em-sl--breach:hover { background: linear-gradient(90deg, oklch(0.48 0.175 20 / 0.20) 0%, var(--base) 40%); }

.wo-em-sl--paused:hover { background: linear-gradient(90deg, oklch(0.55 0.12 260 / 0.10) 0%, var(--base) 40%); }

/* Red-themed status & badge overrides */
.wo-em-pri-badge { font-size: var(--text-3xs); font-weight: var(--weight-semibold); padding: 1px 6px; border-radius: var(--radius-full); text-transform: uppercase; letter-spacing: 0.02em; }
.wo-em-pri-badge--high { background: oklch(0.50 0.170 20 / 0.15); color: oklch(0.48 0.175 20); }
.wo-em-pri-badge--medium { background: oklch(0.62 0.160 40 / 0.15); color: oklch(0.55 0.140 35); }
.wo-em-pri-badge--low { background: oklch(0.65 0.10 30 / 0.12); color: oklch(0.50 0.08 30); }
.wo-em-status--pending { color: oklch(0.60 0.18 35); }
.wo-em-status--processing { color: oklch(0.48 0.175 20); }
.wo-em-status--completed { color: var(--ink-muted); }
.wo-em-status--cancelled { color: var(--ink-muted); }
.wo-em-deadline { font-size: var(--text-3xs); color: oklch(0.48 0.175 20); font-weight: var(--weight-semibold); }

.wo-overlay { position: fixed; inset: 0; background: oklch(0.15 0.02 210 / 0.45); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.wo-modal { background: var(--surface); border-radius: var(--radius-xl); padding: var(--s-8); width: 90%; max-width: 480px; display: flex; flex-direction: column; gap: var(--s-5); box-shadow: var(--shadow-xl); }
.wo-modal-title { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }
.wo-modal-field { display: flex; flex-direction: column; gap: var(--s-1); }
.wo-modal-label { font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--ink-soft); }
.wo-required { color: var(--danger); }
.wo-modal-input { padding: var(--s-2) var(--s-3); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base); outline: none; transition: border-color var(--dur-fast) var(--ease-soft); }
.wo-modal-input:focus { border-color: var(--brand); }
.wo-modal-textarea { resize: vertical; }
.wo-modal-acts { display: flex; justify-content: flex-end; gap: var(--s-3); padding-top: var(--s-3); }
.wo-status--pending { }
.wo-status--processing { color: var(--brand); }
.wo-status--completed { color: var(--success); }
.wo-status--cancelled, .wo-status--closed { color: var(--danger); }

/* ── Draggable Resizable Chat Popup ── */
.chat-popup-overlay { position: fixed; inset: 0; z-index: 500; }
.chat-popup {
  position: fixed; z-index: 501;
  background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius-lg); box-shadow: var(--shadow-xl);
  display: flex; flex-direction: column; overflow: hidden;
  min-width: 320px; min-height: 240px;
}
.chat-popup-head {
  padding: 10px 14px; background: var(--agent);
  display: flex; align-items: center; justify-content: space-between;
  flex-shrink: 0; cursor: grab; user-select: none;
}
.chat-popup-head:active { cursor: grabbing; }
.chat-popup-title { font-size: var(--text-sm); font-weight: var(--weight-semibold); color: #fff; }
.chat-popup-head-actions { display: flex; align-items: center; gap: 8px; }
.chat-popup-end-btn {
  padding: 4px 12px; border: 1px solid oklch(1 0 0 / 0.35); border-radius: var(--radius-sm);
  background: oklch(1 0 0 / 0.15); color: #fff;
  font-size: var(--text-2xs); font-weight: var(--weight-semibold);
  font-family: var(--font-body); cursor: pointer;
}
.chat-popup-end-btn:hover:not(:disabled) { background: oklch(0.50 0.170 20); border-color: oklch(0.50 0.170 20); }
.chat-popup-end-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.chat-popup-close-btn {
  width: 24px; height: 24px; border: none; border-radius: 50%;
  background: oklch(1 0 0 / 0.2); color: #fff;
  font-size: 14px; line-height: 1; cursor: pointer;
}
.chat-popup-close-btn:hover { background: oklch(1 0 0 / 0.4); }
.chat-popup-body {
  flex: 1; overflow-y: auto; padding: 10px;
  display: flex; flex-direction: column; gap: 6px; background: var(--base);
}
.mini-chat-empty { text-align: center; padding: var(--s-6); font-size: var(--text-xs); color: var(--ink-muted); }
.mini-msg {
  font-size: var(--text-xs); padding: 5px 10px; border-radius: 8px; max-width: 88%;
  line-height: var(--leading-snug); display: flex; flex-direction: column; gap: 1px;
}
.mini-msg--user { background: oklch(0.94 0.04 275 / 0.2); align-self: flex-start; }
.mini-msg--agent { background: oklch(0.93 0.03 310 / 0.2); align-self: flex-end; }
.mini-msg--system { align-self: center; font-size: var(--text-2xs); color: var(--ink-muted); background: var(--border-light); padding: 2px 12px; border-radius: var(--radius-full); max-width: 90%; }
.mini-msg-role { font-size: var(--text-3xs); font-weight: var(--weight-semibold); color: var(--ink-muted); }
.mini-msg-content { color: var(--ink); }
.chat-popup-input-bar {
  display: flex; gap: 6px; padding: 8px 10px; border-top: 1px solid var(--border-light);
  background: var(--surface); flex-shrink: 0;
}
.chat-popup-input {
  flex: 1; padding: 6px 10px; border: 1px solid var(--border); border-radius: 6px;
  font-size: var(--text-xs); font-family: var(--font-body); outline: none; background: var(--base);
}
.chat-popup-input:focus { border-color: var(--agent); }
.chat-popup-send-btn {
  padding: 6px 14px; border: none; border-radius: 6px; background: var(--agent);
  color: #fff; font-size: var(--text-xs); font-weight: var(--weight-semibold);
  cursor: pointer; font-family: var(--font-body);
}
.chat-popup-send-btn:hover:not(:disabled) { background: oklch(0.40 0.110 310); }
.chat-popup-send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.chat-popup-resize-handle {
  position: absolute; bottom: 0; right: 0;
  width: 16px; height: 16px; cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, var(--border) 50%);
  border-radius: 0 0 var(--radius-lg) 0;
}

/* ── 分类下拉菜单 ── */
.kr-cat-dropdown { position: relative; display: inline-block; }
.kr-cat-selected {
  padding: var(--s-2) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); background: var(--surface); cursor: pointer; color: var(--ink);
  transition: border-color var(--dur-fast) var(--ease-soft);
}
.kr-cat-selected:hover { border-color: var(--brand); }
.kr-cat-menu {
  position: absolute; top: 100%; left: 0; z-index: 50;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  box-shadow: 0 4px 16px oklch(0.25 0.01 250 / 0.10); padding: var(--s-2); min-width: 200px; max-height: 280px; overflow-y: auto;
}
.kr-cat-menu-item {
  padding: var(--s-2) var(--s-3); font-size: var(--text-sm); cursor: pointer; border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: space-between;
}
.kr-cat-menu-item:hover { background: var(--brand-pale); }
.kr-cat-menu-item.active { color: var(--brand); font-weight: var(--weight-medium); }
</style>