<template>
  <div class="ud-page" id="user-report-content">
    <div class="ud-filter">
      <select v-model="filters.userType" class="ud-sel">
        <option value="All">全部类型</option>
        <option value="UNREGISTERED">未注册</option>
        <option value="REGISTERED">已注册</option>
        <option value="High-Potential">高潜力</option>
        <option value="Member">会员</option>
      </select>
      <input v-model="filters.userId" placeholder="用户 ID" class="ud-inp" />
      <el-date-picker v-model="dateRange" type="daterange" range-separator="→" start-placeholder="起" end-placeholder="止" value-format="YYYY-MM-DD" class="ud-dp" />
      <button class="btn-brand btn-sm" @click="fetchData">查询</button>
      <button class="btn-ghost btn-sm" @click="clear">清空</button>
      <button class="btn-ghost btn-sm" @click="doExport">导出 PDF</button>
    </div>

    <div class="ud-chart" v-if="chartData.length > 0">
      <h3>用户类型分布</h3>
      <div ref="chartEl" class="ud-cvs"></div>
    </div>

    <div class="tbl">
      <table v-loading="store.loading">
        <thead><tr><th>用户 ID</th><th>会话 ID</th><th>类型</th><th>标签</th><th>总消费</th><th>频次</th><th>满意度</th><th>最近互动</th></tr></thead>
        <tbody>
          <tr v-if="(store.profiles || []).length === 0"><td colspan="8" class="empty">暂无数据</td></tr>
          <tr v-for="p in (store.profiles || [])" :key="p.userId || p.sessionId">
            <td>{{ p.userId || '-' }}</td>
            <td class="mono">{{ p.sessionId ? p.sessionId.substring(0, 10) + '…' : '-' }}</td>
            <td><span class="tag" :class="'t-' + typeCls(p.userType || (p.userId ? 'REGISTERED' : 'UNREGISTERED'))">{{ p.userType || (p.userId ? '已注册' : '未注册') }}</span></td>
            <td><span v-if="p.tags" class="tag t-agent">{{ p.tags }}</span><span v-else>-</span></td>
            <td class="mono">{{ p.totalSpending ? '¥' + p.totalSpending : '-' }}</td>
            <td>{{ p.purchaseFrequency ?? '-' }}</td>
            <td>{{ p.satisfaction ?? '-' }}</td>
            <td class="date">{{ fmt(p.lastPurchaseTime || p.lastServiceTime) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useUserProfileStore } from '@/shared/stores/userProfileStore'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

const store = useUserProfileStore()
const filters = reactive({ userType: 'All', userId: '' })
const dateRange = ref([])
const chartEl = ref(null)
let chartInst = null

const chartData = computed(() => {
  const map = {}
  const profiles = store.profiles || []
  profiles.forEach(p => {
    const t = p.userType || (p.userId ? 'REGISTERED' : 'UNREGISTERED')
    map[t] = (map[t] || 0) + 1
  })
  return Object.entries(map)
})

const typeCls = (t) => ({ UNREGISTERED: 'muted', REGISTERED: 'pri', 'High-Potential': 'war', Member: 'agent' }[t] || 'muted')
const fmt = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

const typeColors = {
  UNREGISTERED: 'oklch(0.60 0.015 210)', REGISTERED: 'oklch(0.52 0.135 175)',
  'High-Potential': 'oklch(0.62 0.160 75)', Member: 'oklch(0.48 0.120 310)'
}

const initChart = () => {
  if (!chartEl.value) return
  chartInst?.dispose()
  chartInst = echarts.init(chartEl.value)
  updateChart()
}

const updateChart = () => {
  if (!chartInst) return
  const data = chartData.value
  if (!data.length) {
    chartInst.setOption({ graphic: [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: 'oklch(0.60 0.015 210)', fontSize: 14 } }] })
    return
  }
  chartInst.setOption({
    tooltip: { trigger: 'item' }, graphic: [],
    series: [{ type: 'pie', radius: ['44%', '70%'], label: { formatter: '{b}\n{d}%' },
      data: data.map(([k, v]) => ({ name: k, value: v, itemStyle: { color: typeColors[k] } })),
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.1)' } } }]
  })
}

const fetchData = async () => {
  try {
    await store.search({
      userType: filters.userType === 'All' ? '' : filters.userType,
      userId: filters.userId || undefined,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined
    })
    nextTick(updateChart)
  } catch { ElMessage.error('查询失败') }
}

const clear = () => { filters.userType = 'All'; filters.userId = ''; dateRange.value = []; fetchData() }

const doExport = () => {
  const { jsPDF, html2canvas } = window
  if (!html2canvas || !jsPDF) { ElMessage.error('导出库未加载'); return }
  const el = document.getElementById('user-report-content')
  if (!el) return
  html2canvas(el, { scale: 2, useCORS: true }).then(c => {
    const pdf = new jsPDF('p', 'mm', 'a4')
    pdf.addImage(c.toDataURL('image/png'), 'PNG', 0, 0, pdf.internal.pageSize.getWidth(), (c.height * pdf.internal.pageSize.getWidth()) / c.width)
    pdf.save('用户数据.pdf')
  }).catch(() => ElMessage.error('导出失败'))
}

onMounted(() => { fetchData(); nextTick(initChart) })
onBeforeUnmount(() => { chartInst?.dispose() })
</script>

<style scoped>
.ud-page { display: flex; flex-direction: column; gap: var(--s-6); }
.ud-filter { display: flex; gap: var(--s-3); align-items: center; flex-wrap: wrap; }
.ud-sel { padding: var(--s-2) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--surface); cursor: pointer; }
.ud-sel:focus { border-color: var(--brand); outline: none; }
.ud-inp { width: 120px; padding: var(--s-2) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--surface); }
.ud-inp:focus { border-color: var(--brand); outline: none; }
.ud-dp { width: 240px; }

.btn-brand { padding: var(--s-2) var(--s-5); border: none; border-radius: var(--radius-md); font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body); color: #fff; background: var(--brand); cursor: pointer; display: inline-flex; align-items: center; gap: var(--s-2); transition: background var(--dur-fast) var(--ease-soft); }
.btn-brand:hover { background: var(--brand-deep); }
.btn-ghost { padding: var(--s-2) var(--s-5); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink-soft); background: var(--surface); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); }
.btn-ghost:hover { border-color: var(--ink-muted); color: var(--ink); }
.btn-sm { padding: var(--s-2) var(--s-4); font-size: var(--text-sm); }

.ud-chart { background: var(--surface); border-radius: var(--radius-lg); padding: var(--s-5); box-shadow: var(--shadow-xs); }
.ud-chart h3 { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); margin-bottom: var(--s-4); }
.ud-cvs { width: 100%; height: 260px; }

.tbl { background: var(--surface); border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-xs); }
.tbl table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
.tbl th { text-align: left; padding: var(--s-3) var(--s-4); background: var(--base-alt); color: var(--ink-soft); font-weight: var(--weight-semibold); font-size: var(--text-2xs); text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1px solid var(--border-light); }
.tbl td { padding: var(--s-3) var(--s-4); color: var(--ink); border-bottom: 1px solid var(--border-light); }
.tbl tbody tr:last-child td { border-bottom: none; }
.tbl tbody tr:hover { background: var(--brand-pale); }
.empty { text-align: center; color: var(--ink-muted); padding: var(--s-12) !important; }
.mono { font-family: var(--font-mono); font-size: var(--text-2xs); }
.date { font-size: var(--text-2xs); color: var(--ink-muted); white-space: nowrap; }

.tag { display: inline-block; font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 8px; border-radius: var(--radius-full); }
.t-muted { background: var(--base); color: var(--ink-muted); }
.t-pri { background: var(--brand-soft); color: var(--brand); }
.t-war { background: var(--warning-soft); color: var(--warning); }
.t-agent { background: var(--agent-soft); color: var(--agent); }
</style>
