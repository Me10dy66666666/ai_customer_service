<template>
  <div class="sla-page">
    <div class="sla-topbar">
      <div class="topbar-left">
        <h1>SLA 绩效看板</h1>
        <span class="badge badge-sla">客服 · SLA 达成</span>
      </div>
      <div class="topbar-right">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="→" start-placeholder="起" end-placeholder="止" value-format="YYYY-MM-DD" class="dp" @change="onDateChange" />
        <button class="refresh-btn" :disabled="store.loading" @click="refresh">↻</button>
      </div>
    </div>

    <div class="sla-stage" v-loading="store.loading">
      <div class="stat-row stat-row-4">
        <div class="stat sla-response">
          <div class="stat-num">
            {{ slaOverviewCard.responseRate }}
            <span class="unit">%</span>
          </div>
          <div class="stat-lab">响应SLA达成率</div>
          <div class="stat-tag" :class="slaOverviewCard.responseTag">{{ slaOverviewCard.responseLabel }}</div>
        </div>
        <div class="stat sla-resolution">
          <div class="stat-num">
            {{ slaOverviewCard.resolutionRate }}
            <span class="unit">%</span>
          </div>
          <div class="stat-lab">解决SLA达成率</div>
          <div class="stat-tag" :class="slaOverviewCard.resolutionTag">{{ slaOverviewCard.resolutionLabel }}</div>
        </div>
        <div class="stat sla-avg-resp">
          <div class="stat-num">{{ slaOverviewCard.avgResponseTime }}</div>
          <div class="stat-lab">平均有效响应时间</div>
        </div>
        <div class="stat sla-avg-reso">
          <div class="stat-num">{{ slaOverviewCard.avgResolutionTime }}</div>
          <div class="stat-lab">平均有效解决时间</div>
        </div>
      </div>

      <div class="ch-card">
        <h3>SLA 达成率趋势</h3>
        <div ref="chartTrendRef" class="chart-box"></div>
      </div>

      <div class="overtime-row">
        <div class="ch-card ch-card-half">
          <h3>超时工单占比</h3>
          <div ref="chartOvertimeRef" class="chart-box chart-box-sm"></div>
        </div>
        <div class="ch-card ch-card-half overtime-avg-card">
          <h3>平均超时时长</h3>
          <div class="overtime-num">{{ slaOverviewCard.avgOvertime }}</div>
        </div>
      </div>

      <div class="tbl">
        <table>
          <thead>
            <tr>
              <th>排名</th>
              <th>客服ID</th>
              <th>工单数</th>
              <th>SLA达标数</th>
              <th>SLA达成率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in slaTeamRankingData" :key="item.agentId">
              <td>
                <span class="rank-badge" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span>
              </td>
              <td>{{ item.agentId }}</td>
              <td>{{ item.totalWorkOrders }}</td>
              <td>{{ item.slaMetCount }}</td>
              <td>
                <span class="sla-rate" :class="slaRateClass(item.slaRate)">{{ item.slaRate }}%</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/shared/stores/authStore'
import { useAgentInsightStore } from '@/shared/stores/agentInsightStore'
import echarts from '@/shared/charts/echarts'

const authStore = useAuthStore()
const store = useAgentInsightStore()

const agentId = computed(() => authStore.userId)

const fmtD = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const d = new Date()
const monthStart = new Date(d.getFullYear(), d.getMonth(), 1)
const dateRange = ref([fmtD(monthStart), fmtD(new Date())])

function onDateChange() {
  refresh()
}

function formatSeconds(sec) {
  if (sec == null) return '-'
  const total = Math.round(sec)
  const min = Math.floor(total / 60)
  const s = total % 60
  if (min > 0) return `${min}分${s}秒`
  return `${s}秒`
}

function formatMinutes(min) {
  if (min == null) return '-'
  const total = Math.round(min)
  const h = Math.floor(total / 60)
  const m = total % 60
  if (h > 0) return `${h}小时${m}分钟`
  return `${m}分钟`
}

function slaRateLabel(rate) {
  if (rate == null) return { label: '未知', cls: 'flat' }
  if (rate >= 90) return { label: '优秀', cls: 'good' }
  if (rate >= 75) return { label: '良好', cls: 'warn' }
  return { label: '需改进', cls: 'bad' }
}

function slaRateClass(rate) {
  if (rate >= 90) return 'good'
  if (rate >= 75) return 'warn'
  return 'bad'
}

const slaOverviewCard = computed(() => {
  const ov = store.slaOverview
  const respRate = ov?.responseComplianceRate != null ? Number(ov.responseComplianceRate).toFixed(1) : '-'
  const resoRate = ov?.resolutionComplianceRate != null ? Number(ov.resolutionComplianceRate).toFixed(1) : '-'
  const respTag = slaRateLabel(ov?.responseComplianceRate)
  const resoTag = slaRateLabel(ov?.resolutionComplianceRate)
  return {
    responseRate: respRate,
    responseLabel: respTag.label,
    responseTag: respTag.cls,
    resolutionRate: resoRate,
    resolutionLabel: resoTag.label,
    resolutionTag: resoTag.cls,
    avgResponseTime: formatSeconds(ov?.avgEffectiveResponseSeconds),
    avgResolutionTime: formatMinutes(ov?.avgEffectiveResolutionMinutes),
    avgOvertime: formatMinutes(ov?.avgOvertimeMinutes)
  }
})

const slaTeamRankingData = computed(() => {
  const ranking = store.slaTeamRanking?.ranking || []
  return ranking.map(r => ({
    ...r,
    slaRate: r.slaRate != null ? Number(r.slaRate).toFixed(1) : '-'
  }))
})

const chartTrendRef = ref(null)
const chartOvertimeRef = ref(null)
let charts = []

const clr = {
  pri: 'oklch(0.52 0.135 175)',
  suc: 'oklch(0.50 0.150 150)',
  war: 'oklch(0.62 0.160 75)',
  dan: 'oklch(0.50 0.170 20)'
}

function renderTrendChart() {
  if (!chartTrendRef.value) return
  let chart = echarts.getInstanceByDom(chartTrendRef.value)
  if (!chart) {
    chart = echarts.init(chartTrendRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const trend = store.slaTrend
    const dates = trend?.dates || []
    const rates = trend?.complianceRates || []
    chart.setOption({
      tooltip: { trigger: 'axis', formatter: p => `${p[0].axisValue}<br/>SLA达成率: ${p[0].value}%` },
      grid: { left: 44, right: 12, top: 8, bottom: 20 },
      xAxis: { type: 'category', data: dates.length ? dates : ['暂无'], axisLabel: { color: '#7b8c9a', fontSize: 11 } },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%', color: '#7b8c9a', fontSize: 11 } },
      series: [{
        type: 'line',
        data: dates.length ? rates : [0],
        smooth: true,
        itemStyle: { color: clr.pri },
        lineStyle: { color: clr.pri, width: 2 },
        markLine: {
          silent: true,
          data: [{ yAxis: 90, lineStyle: { color: clr.suc, type: 'dashed' }, label: { formatter: '90%' } }]
        }
      }]
    })
  }, 0)
}

function renderOvertimeChart() {
  if (!chartOvertimeRef.value) return
  let chart = echarts.getInstanceByDom(chartOvertimeRef.value)
  if (!chart) {
    chart = echarts.init(chartOvertimeRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const ov = store.slaOverview
    if (!ov || ov.overtimeRatio == null) {
      chart.setOption({
        title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } },
        series: [{ type: 'pie', radius: ['55%', '78%'], center: ['50%', '52%'], data: [{ value: 1, name: '暂无', itemStyle: { color: '#eee' } }], label: { show: false }, silent: true }]
      })
      return
    }
    const overtimePct = Number(ov.overtimeRatio)
    const compliantPct = 100 - overtimePct
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
      series: [{
        type: 'pie',
        radius: ['55%', '78%'],
        center: ['50%', '52%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, position: 'outside', formatter: '{b}\n{d}%', fontSize: 11, color: '#3a4f5f' },
        emphasis: { itemStyle: { shadowBlur: 12, shadowColor: 'oklch(0 0 0 / 0.18)' } },
        data: [
          { value: compliantPct || 0, name: '达标', itemStyle: { color: clr.suc } },
          { value: overtimePct || 0, name: '超时', itemStyle: { color: clr.dan } }
        ]
      }]
    })
  }, 0)
}

function disposeCharts() {
  charts.forEach(c => c.dispose())
  charts = []
}

function renderAll() {
  setTimeout(() => {
    renderTrendChart()
    renderOvertimeChart()
  }, 50)
}

let refreshTimer = null

async function refresh() {
  if (!agentId.value) return
  store.loading = true
  try {
    await Promise.all([
      store.fetchSlaOverview(agentId.value, dateRange.value[0], dateRange.value[1]),
      store.fetchSlaTrend(agentId.value, dateRange.value[0], dateRange.value[1]),
      store.fetchTeamSlaRanking(dateRange.value[0], dateRange.value[1])
    ])
  } finally {
    store.loading = false
  }
  renderAll()
}

onMounted(async () => {
  if (!agentId.value) return
  store.loading = true
  try {
    await Promise.all([
      store.fetchSlaOverview(agentId.value, dateRange.value[0], dateRange.value[1]),
      store.fetchSlaTrend(agentId.value, dateRange.value[0], dateRange.value[1]),
      store.fetchTeamSlaRanking(dateRange.value[0], dateRange.value[1])
    ])
  } finally {
    store.loading = false
  }
  renderAll()
  refreshTimer = setInterval(refresh, 30000)
})

onUnmounted(() => {
  clearInterval(refreshTimer)
  disposeCharts()
})
</script>

<style scoped>
.sla-page { display: flex; flex-direction: column; height: 100%; }
.sla-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 28px; border-bottom: 1px solid var(--border-light);
  background: var(--surface); min-height: 60px;
}
.topbar-left { display: flex; align-items: center; gap: 16px; }
.sla-topbar h1 { font-family: var(--font-heading); font-size: 22px; font-weight: 700; color: var(--ink); }
.badge { font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 9999px; }
.badge-sla { background: oklch(0.90 0.040 175); color: oklch(0.45 0.100 175); }
.topbar-right { display: flex; align-items: center; gap: 16px; }
.date-range {
  display: flex; align-items: center; gap: 8px; background: var(--base);
  border: 1px solid var(--border); border-radius: 10px;
  padding: 6px 12px; font-size: 13px; color: var(--ink-soft);
  transition: border 150ms;
}
.date-range:hover { border-color: var(--brand); }
.date-icon { font-size: 14px; }
.refresh-btn {
  width: 36px; height: 36px; border-radius: 10px; border: 1px solid var(--border);
  background: var(--base); display: flex; align-items: center; justify-content: center;
  cursor: pointer; font-size: 16px; color: var(--ink-soft);
  transition: all 150ms;
}
.refresh-btn:hover { border-color: var(--brand); color: var(--brand); }
.sla-stage { flex: 1; overflow-y: auto; padding: 28px; }
.stat-row { display: grid; gap: 16px; margin-bottom: 24px; }
.stat-row-4 { grid-template-columns: repeat(4, 1fr); }
.stat {
  background: var(--surface); border-radius: 16px; padding: 20px;
  box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04);
  display: flex; flex-direction: column; gap: 8px;
  transition: transform 150ms cubic-bezier(0.34,1.56,0.64,1), box-shadow 150ms cubic-bezier(0.34,1.56,0.64,1);
  position: relative; overflow: hidden;
}
.stat::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; }
.stat.sla-response::before { background: var(--brand); }
.stat.sla-resolution::before { background: oklch(0.48 0.120 310); }
.stat.sla-avg-resp::before { background: oklch(0.58 0.170 45); }
.stat.sla-avg-reso::before { background: var(--success); }
.stat:hover { transform: translateY(-2px); box-shadow: 0 8px 24px oklch(0.25 0.01 250 / 0.08); }
.stat-num { font-family: var(--font-heading); font-size: clamp(24px,3.5vw,32px); font-weight: 700; color: var(--ink); line-height: 1.1; }
.stat-num .unit { font-size: 0.45em; font-weight: 400; color: var(--ink-muted); margin-left: 2px; }
.stat-lab { font-size: 12px; color: var(--ink-muted); font-weight: 500; }
.stat-tag { font-size: 11px; font-weight: 600; align-self: flex-start; padding: 2px 8px; border-radius: 9999px; }
.stat-tag.good { background: oklch(0.92 0.050 150); color: oklch(0.45 0.130 150); }
.stat-tag.warn { background: oklch(0.94 0.070 75); color: oklch(0.55 0.140 75); }
.stat-tag.bad { background: oklch(0.93 0.040 20); color: oklch(0.45 0.150 20); }
.stat-tag.flat { background: var(--base-alt); color: var(--ink-muted); }

.ch-card {
  background: var(--surface); border-radius: 16px; padding: 20px;
  box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04);
  margin-bottom: 16px;
}
.ch-card h3 { font-family: var(--font-heading); font-size: clamp(17px,2.2vw,19px); font-weight: 600; color: var(--ink); margin-bottom: 12px; }
.chart-box { width: 100%; height: 320px; }
.chart-box-sm { height: 260px; }

.overtime-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.ch-card-half { margin-bottom: 0; }
.overtime-avg-card { display: flex; flex-direction: column; }
.overtime-num {
  font-family: var(--font-heading);
  font-size: clamp(36px,5vw,52px);
  font-weight: 700;
  color: var(--ink);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.tbl { background: var(--surface); border-radius: 16px; overflow: hidden; box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04); }
.tbl table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
.tbl th { text-align: left; padding: var(--s-3) var(--s-4); background: var(--base-alt); color: var(--ink-soft); font-weight: var(--weight-semibold); font-size: var(--text-2xs); text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1px solid var(--border-light); }
.tbl td { padding: var(--s-3) var(--s-4); color: var(--ink); border-bottom: 1px solid var(--border-light); }
.tbl tbody tr:last-child td { border-bottom: none; }
.tbl tbody tr:hover { background: var(--brand-pale); }

.rank-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 8px;
  font-size: 12px; font-weight: 700; color: var(--ink-muted);
  background: var(--base-alt);
}
.rank-badge.rank-1 { background: oklch(0.85 0.090 85); color: oklch(0.45 0.100 85); }
.rank-badge.rank-2 { background: oklch(0.88 0.020 210); color: oklch(0.50 0.040 210); }
.rank-badge.rank-3 { background: oklch(0.87 0.050 55); color: oklch(0.48 0.080 55); }

.sla-rate { font-weight: 600; }
.sla-rate.good { color: var(--success); }
.sla-rate.warn { color: oklch(0.55 0.140 75); }
.sla-rate.bad { color: var(--danger); }

@media (prefers-reduced-motion: reduce) {
  .stat:hover { transform: none; }
}
</style>
