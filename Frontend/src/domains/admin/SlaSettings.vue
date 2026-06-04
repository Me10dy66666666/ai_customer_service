<template>
  <div class="sla-shell">
    <header class="sla-topbar">
      <h2 class="sla-topbar-title">超时设置</h2>
      <button class="sla-btn sla-btn-brand" @click="openCreate">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        新建配置
      </button>
    </header>

    <div class="sla-table-wrap">
      <table class="sla-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>业务标签</th>
            <th>优先级</th>
            <th>响应时限 (分)</th>
            <th>解决时限 (分)</th>
            <th>升级时限 (分)</th>
            <th>紧急阈值</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="configs.length === 0 && !loading">
            <td colspan="9" class="sla-empty">暂无 SLA 配置，点击"新建配置"添加</td>
          </tr>
          <tr v-for="cfg in configs" :key="cfg.id">
            <td class="sla-mono">{{ cfg.id }}</td>
            <td>
              <span class="sla-biz-tag">{{ cfg.bizTag || '-' }}</span>
            </td>
            <td>
              <span class="sla-pri-badge" :class="'sla-pri--' + cfg.priority">{{ priorityLabel(cfg.priority) }}</span>
            </td>
            <td class="sla-mono">{{ cfg.responseMinutes ?? '-' }}</td>
            <td class="sla-mono">{{ cfg.resolutionMinutes ?? '-' }}</td>
            <td class="sla-mono">{{ cfg.escalationMinutes ?? '-' }}</td>
            <td class="sla-mono">{{ cfg.emergencyThreshold ?? '-' }}</td>
            <td>
              <span class="sla-status-dot" :class="cfg.isActive === 1 ? 'sla-active' : 'sla-inactive'"></span>
              {{ cfg.isActive === 1 ? '启用' : '禁用' }}
            </td>
            <td class="sla-actions">
              <button class="sla-btn sla-btn-ghost-sm" @click="openEdit(cfg)">编辑</button>
              <button v-if="cfg.isActive === 1" class="sla-btn sla-btn-ghost-sm sla-btn-warn" @click="toggleActive(cfg)">禁用</button>
              <button v-else class="sla-btn sla-btn-ghost-sm sla-btn-ok" @click="toggleActive(cfg)">启用</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Teleport to="body">
      <div v-if="dlgVisible" class="sla-overlay" @click.self="dlgVisible = false">
        <div class="sla-modal">
          <h3 class="sla-modal-title">{{ editingId ? '编辑配置' : '新建配置' }}</h3>
          <div class="sla-modal-field">
            <label class="sla-modal-label">业务标签 <span class="sla-required">*</span></label>
            <input v-model="form.bizTag" class="sla-modal-input" placeholder="如：售后、投诉、咨询" maxlength="50" />
          </div>
          <div class="sla-modal-field">
            <label class="sla-modal-label">优先级 <span class="sla-required">*</span></label>
            <select v-model="form.priority" class="sla-modal-input">
              <option value="low">低</option>
              <option value="medium">中</option>
              <option value="high">高</option>
            </select>
          </div>
          <div class="sla-modal-field">
            <label class="sla-modal-label">响应时限（分钟）</label>
            <input v-model.number="form.responseMinutes" type="number" min="1" class="sla-modal-input" placeholder="首次响应最长时间" />
          </div>
          <div class="sla-modal-field">
            <label class="sla-modal-label">解决时限（分钟）</label>
            <input v-model.number="form.resolutionMinutes" type="number" min="1" class="sla-modal-input" placeholder="工单解决最长时间" />
          </div>
          <div class="sla-modal-field">
            <label class="sla-modal-label">升级时限（分钟）</label>
            <input v-model.number="form.escalationMinutes" type="number" min="1" class="sla-modal-input" placeholder="未响应自动升级时间" />
          </div>
          <div class="sla-modal-field">
            <label class="sla-modal-label">紧急阈值</label>
            <input v-model="form.emergencyThreshold" class="sla-modal-input" placeholder="如 0.25 表示剩余 25% 时进入紧急" />
          </div>
          <div class="sla-modal-acts">
            <button class="sla-btn sla-btn-ghost" @click="dlgVisible = false">取消</button>
            <button class="sla-btn sla-btn-brand" @click="submitForm" :disabled="submitting">
              {{ submitting ? '提交中…' : (editingId ? '保存' : '创建') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSlaConfigs, createSlaConfig, updateSlaConfig } from '@/domains/admin/slaService'

const configs = ref([])
const loading = ref(false)
const dlgVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)

const defaultForm = () => ({
  bizTag: '',
  priority: 'medium',
  responseMinutes: null,
  resolutionMinutes: null,
  escalationMinutes: null,
  emergencyThreshold: ''
})

const form = ref(defaultForm())

const priorityLabel = (p) => ({ high: '高', medium: '中', low: '低' }[p] || p)

const fetchConfigs = async () => {
  loading.value = true
  try {
    const res = await getSlaConfigs()
    if (res.data.code === 200) {
      configs.value = res.data.data || []
    }
  } catch (err) {
    ElMessage.error('获取 SLA 配置失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  form.value = defaultForm()
  dlgVisible.value = true
}

const openEdit = (cfg) => {
  editingId.value = cfg.id
  form.value = {
    bizTag: cfg.bizTag || '',
    priority: cfg.priority || 'medium',
    responseMinutes: cfg.responseMinutes,
    resolutionMinutes: cfg.resolutionMinutes,
    escalationMinutes: cfg.escalationMinutes,
    emergencyThreshold: cfg.emergencyThreshold != null ? String(cfg.emergencyThreshold) : ''
  }
  dlgVisible.value = true
}

const submitForm = async () => {
  if (!form.value.bizTag.trim()) {
    ElMessage.warning('请输入业务标签')
    return
  }
  submitting.value = true
  try {
    const payload = {
      bizTag: form.value.bizTag.trim(),
      priority: form.value.priority,
      responseMinutes: form.value.responseMinutes,
      resolutionMinutes: form.value.resolutionMinutes,
      escalationMinutes: form.value.escalationMinutes,
      emergencyThreshold: form.value.emergencyThreshold ? parseFloat(form.value.emergencyThreshold) : null
    }
    if (editingId.value) {
      await updateSlaConfig(editingId.value, payload)
      ElMessage.success('配置已更新')
    } else {
      await createSlaConfig(payload)
      ElMessage.success('配置已创建')
    }
    dlgVisible.value = false
    fetchConfigs()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const toggleActive = async (cfg) => {
  const newActive = cfg.isActive === 1 ? 0 : 1
  try {
    await updateSlaConfig(cfg.id, { ...cfg, isActive: newActive })
    cfg.isActive = newActive
    ElMessage.success(newActive === 1 ? '已启用' : '已禁用')
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  fetchConfigs()
})
</script>

<style scoped>
.sla-shell { display: flex; flex-direction: column; height: 100%; padding: var(--s-5) var(--s-6); gap: var(--s-5); }
.sla-topbar { display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; }
.sla-topbar-title { font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }

.sla-btn { display: inline-flex; align-items: center; gap: var(--s-2); padding: var(--s-2) var(--s-4); border: none; border-radius: var(--radius-md); font-size: var(--text-sm); font-weight: var(--weight-medium); font-family: var(--font-body); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); white-space: nowrap; }
.sla-btn-brand { background: var(--brand); color: #fff; }
.sla-btn-brand:hover { background: var(--brand-deep); }
.sla-btn-ghost { background: var(--surface); color: var(--ink-soft); border: 1.5px solid var(--border); }
.sla-btn-ghost:hover { border-color: var(--ink-muted); color: var(--ink); }
.sla-btn-ghost-sm { padding: 2px 10px; font-size: var(--text-2xs); border: 1px solid var(--border-light); border-radius: var(--radius-sm); background: transparent; font-family: var(--font-body); color: var(--ink-soft); cursor: pointer; transition: all var(--dur-fast); }
.sla-btn-ghost-sm:hover { border-color: var(--ink-muted); color: var(--ink); }
.sla-btn-warn { color: var(--danger); }
.sla-btn-warn:hover { border-color: var(--danger); color: var(--danger); }
.sla-btn-ok { color: var(--success); }
.sla-btn-ok:hover { border-color: var(--success); color: var(--success); }

.sla-table-wrap { flex: 1; overflow: auto; border: 1px solid var(--border-light); border-radius: var(--radius-lg); background: var(--surface); }
.sla-table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); font-family: var(--font-body); }
.sla-table th { padding: var(--s-3) var(--s-4); font-size: var(--text-2xs); font-weight: var(--weight-semibold); color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.05em; text-align: left; border-bottom: 1px solid var(--border-light); background: var(--base); white-space: nowrap; }
.sla-table td { padding: var(--s-3) var(--s-4); color: var(--ink); border-bottom: 1px solid var(--border-light); }
.sla-table tbody tr:hover { background: var(--base); }
.sla-table tbody tr:last-child td { border-bottom: none; }
.sla-empty { text-align: center; padding: var(--s-12) var(--s-4); color: var(--ink-muted); font-size: var(--text-sm); }
.sla-mono { font-family: var(--font-mono); font-size: var(--text-xs); color: var(--ink-soft); }

.sla-biz-tag {
  font-size: var(--text-xs); font-weight: var(--weight-medium);
  padding: 2px 10px; border-radius: var(--radius-full);
  background: var(--brand-pale); color: var(--brand-deep);
}

.sla-pri-badge {
  font-size: var(--text-3xs); font-weight: var(--weight-semibold);
  padding: 1px 8px; border-radius: var(--radius-full);
  text-transform: uppercase; letter-spacing: 0.02em;
}
.sla-pri--high { background: var(--danger-soft); color: var(--danger); }
.sla-pri--medium { background: var(--warning-soft); color: var(--warning); }
.sla-pri--low { background: var(--success-soft); color: var(--success); }

.sla-status-dot {
  display: inline-block; width: 6px; height: 6px; border-radius: 50%; margin-right: 6px; vertical-align: middle;
}
.sla-active { background: var(--success); }
.sla-inactive { background: var(--ink-muted); }

.sla-actions { display: flex; gap: var(--s-2); white-space: nowrap; }

.sla-overlay { position: fixed; inset: 0; background: oklch(0.15 0.02 210 / 0.45); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.sla-modal { background: var(--surface); border-radius: var(--radius-xl); padding: var(--s-8); width: 90%; max-width: 480px; display: flex; flex-direction: column; gap: var(--s-5); box-shadow: var(--shadow-xl); }
.sla-modal-title { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }
.sla-modal-field { display: flex; flex-direction: column; gap: var(--s-1); }
.sla-modal-label { font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--ink-soft); }
.sla-required { color: var(--danger); }
.sla-modal-input { padding: var(--s-2) var(--s-3); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base); outline: none; transition: border-color var(--dur-fast) var(--ease-soft); }
.sla-modal-input:focus { border-color: var(--brand); }
.sla-modal-acts { display: flex; justify-content: flex-end; gap: var(--s-3); padding-top: var(--s-3); }
</style>
