<template>
  <div class="sla-shell">
    <header class="sla-topbar">
      <h2 class="sla-topbar-title">工作日历设置</h2>
      <select v-model="selectedCalendarId" class="sla-dropdown" @change="onCalendarChange">
        <option :value="null" disabled>选择日历</option>
        <option v-for="cal in calendarList" :key="cal.id" :value="cal.id">{{ cal.calendarName }}</option>
      </select>
    </header>

    <div v-if="!selectedCalendarId" class="sla-empty">
      请选择一个工作日历进行管理
    </div>

    <template v-if="selectedCalendarId && calendarDetail">
      <section class="sla-section">
        <h3 class="sla-section-title">工作日设置</h3>
        <div class="sla-modal-field">
          <label class="sla-modal-label">日历名称</label>
          <input v-model="calendarDetail.calendarName" class="sla-modal-input" maxlength="50" />
        </div>
        <div class="sla-modal-field">
          <label class="sla-modal-label">工作日</label>
          <div class="sla-checkboxes">
            <label v-for="wd in weekDayOptions" :key="wd.value" class="sla-checkbox">
              <input type="checkbox" :value="wd.value" v-model="workDaysSet" />
              {{ wd.label }}
            </label>
          </div>
        </div>
        <div class="sla-modal-field">
          <label class="sla-modal-label">工作时间段</label>
          <div v-for="(seg, idx) in segments" :key="idx" class="sla-segment-row">
            <input type="time" v-model="seg.start" class="sla-modal-input sla-time-input" />
            <span class="sla-segment-divider">—</span>
            <input type="time" v-model="seg.end" class="sla-modal-input sla-time-input" />
            <button v-if="segments.length > 1" class="sla-btn sla-btn-ghost-sm sla-btn-warn" @click="removeSegment(idx)">删除</button>
          </div>
          <button class="sla-btn sla-btn-ghost-sm sla-btn-ok" @click="addSegment">+ 添加时间段</button>
        </div>
        <div class="sla-modal-acts sla-modal-acts--left">
          <button class="sla-btn sla-btn-brand" @click="saveCalendar" :disabled="saving">
            {{ saving ? '保存中…' : '保存设置' }}
          </button>
        </div>
      </section>

      <section class="sla-section">
        <h3 class="sla-section-title">特殊日期</h3>
        <div class="sla-section-head">
          <button class="sla-btn sla-btn-brand" @click="openAddSpecialDate">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            添加特殊日期
          </button>
        </div>
        <div class="sla-table-wrap">
          <table class="sla-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>类型</th>
                <th>描述</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="specialDates.length === 0 && !loadingDates">
                <td colspan="4" class="sla-empty">暂无特殊日期</td>
              </tr>
              <tr v-for="sd in specialDates" :key="sd.id">
                <td class="sla-mono">{{ sd.specialDate }}</td>
                <td>
                  <span class="sla-pri-badge" :class="dayTypeClass(sd.dayType)">{{ sd.dayType }}</span>
                </td>
                <td>{{ sd.description || '-' }}</td>
                <td class="sla-actions">
                  <button class="sla-btn sla-btn-ghost-sm sla-btn-warn" @click="confirmDeleteSpecialDate(sd)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <Teleport to="body">
      <div v-if="dlgVisible" class="sla-overlay" @click.self="dlgVisible = false">
        <div class="sla-modal">
          <h3 class="sla-modal-title">添加特殊日期</h3>
          <div class="sla-modal-field">
            <label class="sla-modal-label">日期 <span class="sla-required">*</span></label>
            <input type="date" v-model="specialDateForm.specialDate" class="sla-modal-input" />
          </div>
          <div class="sla-modal-field">
            <label class="sla-modal-label">类型 <span class="sla-required">*</span></label>
            <select v-model="specialDateForm.dayType" class="sla-modal-input">
              <option value="HOLIDAY">HOLIDAY</option>
              <option value="WORKDAY">WORKDAY</option>
              <option value="PARTIAL">PARTIAL</option>
            </select>
          </div>
          <div class="sla-modal-field">
            <label class="sla-modal-label">描述</label>
            <input v-model="specialDateForm.description" class="sla-modal-input" placeholder="如：端午节放假" maxlength="200" />
          </div>
          <div class="sla-modal-acts">
            <button class="sla-btn sla-btn-ghost" @click="dlgVisible = false">取消</button>
            <button class="sla-btn sla-btn-brand" @click="submitSpecialDate" :disabled="submitting">
              {{ submitting ? '提交中…' : '确认添加' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { getCalendars, getCalendar, updateCalendar, getSpecialDates, addSpecialDate, deleteSpecialDate } from '@/domains/admin/workCalendarService'

const calendarList = ref([])
const selectedCalendarId = ref(null)
const calendarDetail = ref(null)
const specialDates = ref([])
const loadingDates = ref(false)
const saving = ref(false)
const submitting = ref(false)
const dlgVisible = ref(false)

const weekDayOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

const defaultSegments = () => [{ start: '09:00', end: '18:00' }]
const segments = ref(defaultSegments())

const defaultSpecialDateForm = () => ({
  specialDate: '',
  dayType: 'HOLIDAY',
  description: ''
})

const specialDateForm = ref(defaultSpecialDateForm())

const workDaysSet = computed({
  get: () => {
    if (!calendarDetail.value?.workDays) return []
    return calendarDetail.value.workDays.split(',').map(Number).filter(Boolean)
  },
  set: (val) => {
    if (calendarDetail.value) {
      calendarDetail.value.workDays = val.sort((a, b) => a - b).join(',')
    }
  }
})

const dayTypeClass = (type) => ({
  'HOLIDAY': 'sla-pri--high',
  'WORKDAY': 'sla-pri--low',
  'PARTIAL': 'sla-pri--medium'
}[type] || '')

const fetchCalendarList = async () => {
  try {
    const res = await getCalendars()
    if (res.data.code === 200) {
      calendarList.value = res.data.data || []
    }
  } catch (err) {
    console.error('获取日历列表失败:', err)
    ElMessage.error('获取日历列表失败')
  }
}

const onCalendarChange = async () => {
  if (!selectedCalendarId.value) return
  try {
    const res = await getCalendar(selectedCalendarId.value)
    if (res.data.code === 200) {
      calendarDetail.value = res.data.data
      if (calendarDetail.value.workTimeSegments) {
        try {
          const parsed = JSON.parse(calendarDetail.value.workTimeSegments)
          segments.value = parsed.map(s => ({ start: s.start, end: s.end }))
        } catch {
          segments.value = defaultSegments()
        }
      } else {
        segments.value = defaultSegments()
      }
    }
    fetchSpecialDates()
  } catch (err) {
    console.error('获取日历详情失败:', err)
    ElMessage.error('获取日历详情失败')
  }
}

const fetchSpecialDates = async () => {
  loadingDates.value = true
  try {
    const res = await getSpecialDates(selectedCalendarId.value)
    if (res.data.code === 200) {
      specialDates.value = res.data.data || []
    }
  } catch (err) {
    console.error('获取特殊日期失败:', err)
    ElMessage.error('获取特殊日期失败')
  } finally {
    loadingDates.value = false
  }
}

const addSegment = () => {
  segments.value.push({ start: '09:00', end: '18:00' })
}

const removeSegment = (idx) => {
  segments.value.splice(idx, 1)
}

const saveCalendar = async () => {
  if (!calendarDetail.value.calendarName?.trim()) {
    ElMessage.warning('请输入日历名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      calendarName: calendarDetail.value.calendarName.trim(),
      workDays: workDaysSet.value.join(','),
      workTimeSegments: JSON.stringify(segments.value.map(s => ({ start: s.start, end: s.end })))
    }
    await updateCalendar(selectedCalendarId.value, payload)
    ElMessage.success('日历设置已保存')
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const openAddSpecialDate = () => {
  specialDateForm.value = defaultSpecialDateForm()
  dlgVisible.value = true
}

const submitSpecialDate = async () => {
  if (!specialDateForm.value.specialDate) {
    ElMessage.warning('请选择日期')
    return
  }
  submitting.value = true
  try {
    await addSpecialDate(selectedCalendarId.value, specialDateForm.value)
    ElMessage.success('特殊日期已添加')
    dlgVisible.value = false
    fetchSpecialDates()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '添加失败')
  } finally {
    submitting.value = false
  }
}

const confirmDeleteSpecialDate = (sd) => {
  ElMessageBox.confirm(`确定要删除 ${sd.specialDate} 的特殊日期设置吗？`, '确认删除', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    doDeleteSpecialDate(sd)
  }).catch((err) => { console.error('删除确认取消:', err) })
}

const doDeleteSpecialDate = async (sd) => {
  try {
    await deleteSpecialDate(selectedCalendarId.value, sd.id)
    ElMessage.success('已删除')
    fetchSpecialDates()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

onMounted(() => {
  fetchCalendarList()
})
</script>

<style scoped>
.sla-shell { display: flex; flex-direction: column; height: 100%; padding: var(--s-5) var(--s-6); gap: var(--s-5); }
.sla-topbar { display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; }
.sla-topbar-title { font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }

.sla-dropdown {
  padding: var(--s-2) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--surface);
  outline: none; cursor: pointer; min-width: 200px;
}
.sla-dropdown:focus { border-color: var(--brand); }

.sla-section {
  border: 1px solid var(--border-light); border-radius: var(--radius-lg); background: var(--surface);
  padding: var(--s-5) var(--s-6); display: flex; flex-direction: column; gap: var(--s-4);
}
.sla-section-title {
  font-family: var(--font-heading); font-size: var(--text-base); font-weight: var(--weight-semibold);
  color: var(--ink); margin: 0; padding-bottom: var(--s-3); border-bottom: 1px solid var(--border-light);
}
.sla-section-head { display: flex; align-items: center; justify-content: flex-end; }

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
.sla-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.sla-modal-field { display: flex; flex-direction: column; gap: var(--s-1); }
.sla-modal-label { font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--ink-soft); }
.sla-required { color: var(--danger); }
.sla-modal-input { padding: var(--s-2) var(--s-3); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base); outline: none; transition: border-color var(--dur-fast) var(--ease-soft); }
.sla-modal-input:focus { border-color: var(--brand); }

.sla-checkboxes { display: flex; gap: var(--s-4); flex-wrap: wrap; }
.sla-checkbox { display: flex; align-items: center; gap: var(--s-1); font-size: var(--text-sm); color: var(--ink); cursor: pointer; }
.sla-checkbox input[type="checkbox"] { accent-color: var(--brand); width: 16px; height: 16px; cursor: pointer; }

.sla-segment-row { display: flex; align-items: center; gap: var(--s-2); margin-bottom: var(--s-2); }
.sla-time-input { width: 130px; }
.sla-segment-divider { color: var(--ink-muted); font-size: var(--text-sm); }

.sla-table-wrap { flex: 1; overflow: auto; border: 1px solid var(--border-light); border-radius: var(--radius-lg); background: var(--surface); }
.sla-table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); font-family: var(--font-body); }
.sla-table th { padding: var(--s-3) var(--s-4); font-size: var(--text-2xs); font-weight: var(--weight-semibold); color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.05em; text-align: left; border-bottom: 1px solid var(--border-light); background: var(--base); white-space: nowrap; }
.sla-table td { padding: var(--s-3) var(--s-4); color: var(--ink); border-bottom: 1px solid var(--border-light); }
.sla-table tbody tr:hover { background: var(--base); }
.sla-table tbody tr:last-child td { border-bottom: none; }
.sla-empty { text-align: center; padding: var(--s-12) var(--s-4); color: var(--ink-muted); font-size: var(--text-sm); }
.sla-mono { font-family: var(--font-mono); font-size: var(--text-xs); color: var(--ink-soft); }

.sla-pri-badge {
  font-size: var(--text-3xs); font-weight: var(--weight-semibold);
  padding: 1px 8px; border-radius: var(--radius-full);
  text-transform: uppercase; letter-spacing: 0.02em;
}
.sla-pri--high { background: var(--danger-soft); color: var(--danger); }
.sla-pri--medium { background: var(--warning-soft); color: var(--warning); }
.sla-pri--low { background: var(--success-soft); color: var(--success); }

.sla-actions { display: flex; gap: var(--s-2); white-space: nowrap; }

.sla-modal-acts { display: flex; justify-content: flex-end; gap: var(--s-3); padding-top: var(--s-3); }
.sla-modal-acts--left { justify-content: flex-start; }

.sla-overlay { position: fixed; inset: 0; background: oklch(0.15 0.02 210 / 0.45); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.sla-modal { background: var(--surface); border-radius: var(--radius-xl); padding: var(--s-8); width: 90%; max-width: 480px; display: flex; flex-direction: column; gap: var(--s-5); box-shadow: var(--shadow-xl); }
.sla-modal-title { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }
</style>
