<template>
  <div class="kb-insight">
    <div class="insight-topbar">
      <div class="topbar-left">
        <h1>知识数据洞察</h1>
        <span class="badge badge-kb">知识库管理员 · 个人绩效</span>
      </div>
      <div class="topbar-right">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="→" start-placeholder="起" end-placeholder="止" value-format="YYYY-MM-DD" class="dp" @change="onDateChange" />
        <button class="refresh-btn" :disabled="store.loading" @click="refresh">↻</button>
      </div>
    </div>

    <div class="insight-stage" v-loading="store.loading">
      <div class="stat-row stat-row-5">
        <div class="stat" v-for="card in statCards" :key="card.label">
          <div class="stat-num">{{ card.value }}<span class="unit" v-if="card.unit">{{ card.unit }}</span></div>
          <div class="stat-lab">{{ card.label }}</div>
        </div>
      </div>

      <div class="chart-grid">
        <div class="ch-card">
          <h3>审核量趋势</h3>
          <div ref="chartReviewRef" class="chart-box"></div>
        </div>
        <div class="ch-card">
          <h3>知识库健康度</h3>
          <div class="mini-metrics">
            <div class="mini-metric" v-for="m in miniMetrics" :key="m.label">
              <div class="mm-val">{{ m.value }}</div>
              <div class="mm-lab">{{ m.label }}</div>
            </div>
          </div>
          <div ref="chartKbHealthRef" class="chart-box" style="height:200px"></div>
        </div>
      </div>

      <div class="chart-grid chart-grid-2">
        <div class="ch-card">
          <h3>文档状态分布</h3>
          <div ref="chartDocStatusRef" class="chart-box"></div>
        </div>
        <div class="ch-card">
          <h3>知识库效能趋势</h3>
          <div ref="chartKbEffectRef" class="chart-box"></div>
        </div>
      </div>

      <div class="chart-grid chart-grid-2" style="margin-bottom:16px">
        <div class="ch-card">
          <h3>本周热门搜索词</h3>
          <div class="hot-tags">
            <span class="hot-tag" v-for="w in hotWords" :key="w.keyword">
              {{ w.keyword }} <span class="cnt">×{{ w.searchCount }}</span>
            </span>
            <span v-if="hotWords.length === 0" class="no-data">暂无数据</span>
          </div>
        </div>
        <div class="ch-card">
          <h3>⚠ 零结果搜索 · 待补充</h3>
          <div class="zero-list">
            <div class="zero-item" v-for="w in zeroWords" :key="w.keyword">
              <span>🔍 {{ w.keyword }}</span>
              <span class="z-count">×{{ w.searchCount }}</span>
            </div>
            <div v-if="zeroWords.length === 0" class="no-data">暂无零结果搜索词</div>
          </div>
        </div>
      </div>

      <div class="tbl-wrap" style="margin-top:16px">
        <div class="tbl-head">
          <h3>最近审核记录</h3>
        </div>
        <table class="tbl">
          <thead>
            <tr>
              <th>文档名称</th><th>上传时间</th><th>审核时间</th>
              <th>审核耗时</th><th>结果</th><th>审核人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in recentReviewRecords" :key="r.documentId">
              <td>{{ r.title }}</td>
              <td>{{ r.createdAt }}</td>
              <td>{{ r.reviewedAt }}</td>
              <td>{{ r.reviewDurationMinutes }}min</td>
              <td><span class="pill" :class="r.reviewResult === '通过' ? 'pill-pass' : 'pill-reject'">{{ r.reviewResult }}</span></td>
              <td>{{ r.reviewedBy }}</td>
            </tr>
            <tr v-if="recentReviewRecords.length === 0">
              <td colspan="6" style="text-align:center;color:#9aa8b4;padding:24px">暂无审核记录</td>
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
import { useKbAdminInsightStore } from '@/shared/stores/kbAdminInsightStore'
import * as echarts from 'echarts'

const authStore = useAuthStore()
const store = useKbAdminInsightStore()

const d = new Date()
const weekStart = new Date(d)
weekStart.setDate(weekStart.getDate() - 6)
const dateRange = ref([weekStart.toISOString().slice(0, 10), d.toISOString().slice(0, 10)])

const today = new Date().toISOString().slice(0, 10)

const monthStart = computed(() => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
})
const monthEnd = computed(() => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().slice(0, 10)
})

const chartReviewRef = ref(null)
const chartKbHealthRef = ref(null)
const chartDocStatusRef = ref(null)
const chartKbEffectRef = ref(null)
let charts = []

const reviewedBy = computed(() => authStore.username || 'KB_ADMIN')

const statCards = computed(() => {
  const r = store.reviewStats
  const m = store.monthlyReview
  return [
    { label: '今日审核文档', value: r?.reviewedCount ?? '-' },
    { label: '本月审核总量', value: m?.totalReviewed ?? '-' },
    { label: '待审核积压', value: r?.pendingBacklog ?? '-' },
    { label: '平均审核耗时', value: r?.avgReviewMinutes != null ? r.avgReviewMinutes.toFixed(1) : '-', unit: 'min' },
    { label: '审核通过率', value: r?.passRate != null ? r.passRate.toFixed(1) : '-', unit: '%' }
  ]
})

const miniMetrics = computed(() => {
  const h = store.kbHealth
  return [
    { label: '文档总数', value: h?.totalDocuments ?? '-' },
    { label: '知识命中率', value: h?.hitRate != null ? h.hitRate.toFixed(1) + '%' : '-' },
    { label: '已发布', value: h?.publishedCount ?? '-' },
    { label: '近7天搜索量', value: h?.recentSearchCount ?? '-' }
  ]
})

const hotWords = computed(() => store.hotSearchWords?.words || [])

const zeroWords = computed(() => store.zeroResultWords?.words || [])

const recentReviewRecords = computed(() => store.recentReviews?.records || [])

function renderReviewChart() {
  if (!chartReviewRef.value) return
  let chart = echarts.getInstanceByDom(chartReviewRef.value)
  if (!chart) {
    chart = echarts.init(chartReviewRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const t = store.reviewTrend
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0, textStyle: { fontSize: 11, color: '#7b8c9a' }, itemWidth: 12, itemHeight: 12 },
      grid: { left: 10, right: 20, top: 10, bottom: 30, containLabel: true },
      xAxis: { type: 'category', data: t?.dates || [], axisLine: { lineStyle: { color: '#dde4e8' } }, axisLabel: { color: '#7b8c9a', fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 5, splitLine: { lineStyle: { color: '#edf1f4' } }, axisLabel: { color: '#7b8c9a', fontSize: 11 } },
      series: [
        { name: '通过', type: 'bar', barWidth: 14, stack: 'total', itemStyle: { color: 'oklch(0.50 0.150 150)' }, data: t?.approvedCounts || [] },
        { name: '驳回', type: 'bar', barWidth: 14, stack: 'total', itemStyle: { color: 'oklch(0.50 0.170 20)', borderRadius: [6, 6, 0, 0] }, data: t?.rejectedCounts || [] }
      ]
    })
  }, 0)
}

function renderKbHealthChart() {
  if (!chartKbHealthRef.value) return
  let chart = echarts.getInstanceByDom(chartKbHealthRef.value)
  if (!chart) {
    chart = echarts.init(chartKbHealthRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const t = store.kbHealthTrend
    if (!t || !t.dates || t.dates.length === 0) {
      chart.setOption({
        title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } },
        series: []
      })
    } else {
      chart.setOption({
        tooltip: { trigger: 'axis', formatter: '{b}<br/>命中率: {c}%' },
        grid: { left: 10, right: 20, top: 10, bottom: 10, containLabel: true },
        xAxis: { type: 'category', data: t.dates, axisLabel: { color: '#7b8c9a', fontSize: 10 } },
        yAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#edf1f4' } }, axisLabel: { color: '#7b8c9a', fontSize: 10, formatter: '{value}%' } },
        series: [{
          type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
          lineStyle: { color: 'oklch(0.50 0.155 240)', width: 2.5 },
          itemStyle: { color: 'oklch(0.50 0.155 240)' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'oklch(0.50 0.155 240 / 0.25)' },
            { offset: 1, color: 'oklch(0.50 0.155 240 / 0.02)' }
          ]) },
          data: t.hitRates || []
        }]
      })
    }
  }, 0)
}

function renderDocStatusChart() {
  if (!chartDocStatusRef.value) return
  let chart = echarts.getInstanceByDom(chartDocStatusRef.value)
  if (!chart) {
    chart = echarts.init(chartDocStatusRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const dist = store.docStatusDist?.distribution || {}
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie', radius: ['45%', '72%'], center: ['50%', '52%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, formatter: '{b}\n{d}%', fontSize: 11, color: '#3a4f5f' },
        emphasis: {
          scaleSize: 6,
          itemStyle: { shadowBlur: 12, shadowColor: 'oklch(0 0 0 / 0.18)' }
        },
        data: [
          { value: dist.PUBLISHED || 0, name: '已发布', itemStyle: { color: 'oklch(0.50 0.155 240)' } },
          { value: dist.PENDING_REVIEW || 0, name: '待审核', itemStyle: { color: 'oklch(0.62 0.140 75)' } },
          { value: dist.ARCHIVED || 0, name: '已归档', itemStyle: { color: 'oklch(0.55 0.04 210)' } },
          { value: dist.PENDING_OCR || 0, name: 'OCR待处理', itemStyle: { color: 'oklch(0.68 0.08 175)' } },
          { value: dist.PUBLISHING || 0, name: '发布中', itemStyle: { color: 'oklch(0.50 0.150 150)' } }
        ]
      }]
    })
  }, 0)
}

function renderKbEffectChart() {
  if (!chartKbEffectRef.value) return
  let chart = echarts.getInstanceByDom(chartKbEffectRef.value)
  if (!chart) {
    chart = echarts.init(chartKbEffectRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const t = store.kbEffectTrend
    if (!t || !t.dates || t.dates.length === 0) {
      chart.setOption({
        title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } },
        series: []
      })
    } else {
      chart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { bottom: 0, textStyle: { fontSize: 11, color: '#7b8c9a' }, itemWidth: 12, itemHeight: 12 },
        grid: { left: 10, right: 20, top: 10, bottom: 30, containLabel: true },
        xAxis: { type: 'category', data: t.dates, axisLine: { lineStyle: { color: '#dde4e8' } }, axisLabel: { color: '#7b8c9a', fontSize: 11 } },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1f4' } }, axisLabel: { color: '#7b8c9a', fontSize: 11 } },
        series: [
          { name: '搜索次数', type: 'bar', barWidth: 16, itemStyle: { borderRadius: [6, 6, 0, 0], color: 'oklch(0.52 0.135 175)' }, data: t.searchCounts || [] },
          { name: '命中次数', type: 'bar', barWidth: 16, itemStyle: { borderRadius: [6, 6, 0, 0], color: 'oklch(0.50 0.155 240)' }, data: t.hitCounts || [] }
        ]
      })
    }
  }, 0)
}

function disposeCharts() {
  charts.forEach(c => c.dispose())
  charts = []
}

function renderAll() {
  setTimeout(() => {
    renderReviewChart()
    renderKbHealthChart()
    renderDocStatusChart()
    renderKbEffectChart()
  }, 50)
}

let refreshTimer = null

function onDateChange() {
  refresh()
}

async function refresh() {
  await store.fetchAll(reviewedBy.value, today, monthStart.value, monthEnd.value, dateRange.value[0], dateRange.value[1])
  renderAll()
}

onMounted(async () => {
  await store.fetchAll(reviewedBy.value, today, monthStart.value, monthEnd.value, dateRange.value[0], dateRange.value[1])
  renderAll()
  refreshTimer = setInterval(refresh, 30000)
})

onUnmounted(() => {
  clearInterval(refreshTimer)
  disposeCharts()
})
</script>

<style scoped>
.kb-insight { display: flex; flex-direction: column; height: 100%; }
.insight-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 28px; border-bottom: 1px solid var(--border-light);
  background: var(--surface); min-height: 60px;
}
.topbar-left { display: flex; align-items: center; gap: 16px; }
.insight-topbar h1 { font-family: var(--font-heading); font-size: 22px; font-weight: 700; color: var(--ink); }
.badge { font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 9999px; }
.badge-kb { background: oklch(0.88 0.040 240); color: oklch(0.50 0.155 240); }
.topbar-right { display: flex; align-items: center; gap: 16px; }
.dp { width: 260px; }
.refresh-btn { width: 36px; height: 36px; border-radius: 10px; border: 1px solid var(--border); background: var(--base); display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 16px; color: var(--ink-soft); transition: all 150ms; }
.refresh-btn:hover { border-color: var(--brand); color: var(--brand); }
.insight-stage { flex: 1; overflow-y: auto; padding: 28px; }
.stat-row { display: grid; gap: 16px; margin-bottom: 24px; }
.stat-row-5 { grid-template-columns: repeat(5, 1fr); }
.stat {
  background: var(--surface); border-radius: 16px; padding: 20px;
  box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04);
  display: flex; flex-direction: column; gap: 8px;
  transition: transform 150ms cubic-bezier(0.34,1.56,0.64,1), box-shadow 150ms cubic-bezier(0.34,1.56,0.64,1);
  position: relative; overflow: hidden;
}
.stat::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; }
.stat:nth-child(1)::before { background: oklch(0.50 0.155 240); }
.stat:nth-child(2)::before { background: var(--success); }
.stat:nth-child(3)::before { background: oklch(0.58 0.170 45); }
.stat:nth-child(4)::before { background: var(--brand); }
.stat:nth-child(5)::before { background: oklch(0.52 0.140 265); }
.stat:hover { transform: translateY(-2px); box-shadow: 0 8px 24px oklch(0.25 0.01 250 / 0.08); }
.stat-num { font-family: var(--font-heading); font-size: clamp(24px,3.5vw,32px); font-weight: 700; color: var(--ink); line-height: 1.1; }
.stat-num .unit { font-size: 0.45em; font-weight: 400; color: var(--ink-muted); margin-left: 2px; }
.stat-lab { font-size: 12px; color: var(--ink-muted); font-weight: 500; }
.chart-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; margin-bottom: 16px; }
.chart-grid-2 { grid-template-columns: 1fr 1fr; }
.ch-card { background: var(--surface); border-radius: 16px; padding: 20px; box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04); }
.ch-card h3 { font-family: var(--font-heading); font-size: clamp(17px,2.2vw,19px); font-weight: 600; color: var(--ink); margin-bottom: 12px; }
.chart-box { width: 100%; height: 300px; }
.mini-metrics { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 12px; margin-bottom: 16px; }
.mini-metric { background: var(--base); border-radius: 10px; padding: 12px 16px; display: flex; flex-direction: column; gap: 2px; }
.mm-val { font-family: var(--font-heading); font-size: 22px; font-weight: 700; color: var(--ink); line-height: 1.1; }
.mm-lab { font-size: 11px; color: var(--ink-muted); font-weight: 500; }
.no-data { color: #9aa8b4; font-size: 13px; text-align: center; padding: 24px 0; }

.hot-tags { display: flex; flex-wrap: wrap; gap: 8px; min-height: 40px; }
.hot-tag { display: flex; align-items: center; gap: 4px; padding: 4px 12px; border-radius: 9999px; font-size: 12px; font-weight: 500; background: oklch(0.85 0.040 175); color: oklch(0.38 0.105 175); }
.hot-tag .cnt { font-size: 10px; color: oklch(0.60 0.015 210); font-weight: 400; }

.zero-list { display: flex; flex-direction: column; gap: 8px; }
.zero-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 6px; background: oklch(0.93 0.035 20); font-size: 12px; font-weight: 500; color: oklch(0.50 0.170 20); }
.zero-item .z-count { font-family: monospace; font-size: 11px; opacity: 0.7; }

.tbl-wrap { background: var(--surface); border-radius: 16px; box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04); overflow: hidden; }
.tbl-head { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid var(--border-light); }
.tbl-head h3 { font-family: var(--font-heading); font-size: clamp(17px,2.2vw,19px); font-weight: 600; color: var(--ink); }
.tbl { width: 100%; border-collapse: collapse; }
.tbl th { text-align: left; padding: 12px 20px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-muted); background: oklch(0.965 0.008 230); border-bottom: 1px solid var(--border-light); }
.tbl td { padding: 12px 20px; font-size: 13px; color: var(--ink-soft); border-bottom: 1px solid var(--border-light); }
.tbl tr:last-child td { border-bottom: none; }
.tbl tr:hover td { background: oklch(0.94 0.018 175); }
.pill { display: inline-block; padding: 2px 10px; border-radius: 9999px; font-size: 11px; font-weight: 600; }
.pill-pass { background: oklch(0.90 0.040 150); color: oklch(0.50 0.150 150); }
.pill-reject { background: oklch(0.93 0.035 20); color: oklch(0.50 0.170 20); }
</style>
