<template>
  <div class="agent-insight">
    <div class="insight-topbar">
      <div class="topbar-left">
        <h1>我的数据洞察</h1>
        <span class="badge badge-agent">客服 · 个人绩效</span>
      </div>
      <div class="topbar-right">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="→" start-placeholder="起" end-placeholder="止" value-format="YYYY-MM-DD" class="dp" @change="onDateChange" />
        <button class="reset-btn" title="重置为默认范围" @click="resetDateRange">↺</button>
      </div>
    </div>

    <div class="insight-stage" v-loading="store.loading">
      <div class="stat-row stat-row-4x2">
        <div class="stat" v-for="card in statCards" :key="card.label">
          <div class="stat-icon">{{ card.icon }}</div>
          <div class="stat-num">{{ card.value }}<span class="unit" v-if="card.unit">{{ card.unit }}</span></div>
          <div class="stat-lab">{{ card.label }}</div>
        </div>
      </div>

      <div class="chart-grid">
        <div class="ch-card">
          <h3>接待量趋势</h3>
          <div ref="chartSessionsRef" class="chart-box"></div>
        </div>
        <div class="ch-card">
          <h3>今日满意度分布</h3>
          <div ref="chartSatisfactionRef" class="chart-box"></div>
        </div>
      </div>

      <div class="chart-grid chart-grid-2">
        <div class="ch-card">
          <h3>工单状态分布</h3>
          <div ref="chartWoStatusRef" class="chart-box"></div>
        </div>
        <div class="ch-card">
          <div class="ch-card-head">
            <h3>工单满意度评价</h3>
            <select v-model="woSatisFilter" class="wo-satis-select" @change="onWoSatisFilterChange">
              <option value="all">全部</option>
              <option value="售前">售前</option>
              <option value="售后">售后</option>
            </select>
          </div>
          <div ref="chartWoSatisRef" class="chart-box"></div>
        </div>
      </div>

      <div class="chart-grid chart-grid-2">
        <div class="ch-card">
          <h3>与团队平均对比</h3>
          <div class="compare-legend">
            <span class="legend-item me"><i></i>我的数据</span>
            <span class="legend-item avg"><i></i>团队平均</span>
          </div>
          <div class="compare-list">
            <div class="compare-item" v-for="cmp in compareItems" :key="cmp.label">
              <div class="compare-label">
                <span class="cmp-name">{{ cmp.label }}</span>
                <span class="cmp-val">{{ cmp.me }} / {{ cmp.avg }} avg</span>
              </div>
              <div class="compare-bar"><div class="compare-fill me" :style="{ width: cmp.mePct }"></div></div>
              <div class="compare-bar"><div class="compare-fill avg" :style="{ width: cmp.avgPct }"></div></div>
            </div>
          </div>
        </div>
        <div class="ch-card">
          <h3>团队排名</h3>
          <div ref="chartRankRef" class="chart-box"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/shared/stores/authStore'
import { useAgentInsightStore } from '@/shared/stores/agentInsightStore'
import * as echarts from 'echarts'

const authStore = useAuthStore()
const store = useAgentInsightStore()

const fmtD = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const todayDate = new Date()
const sixDaysAgo = new Date()
sixDaysAgo.setDate(sixDaysAgo.getDate() - 6)
const dateRange = ref([fmtD(sixDaysAgo), fmtD(todayDate)])

const startDate = computed(() => dateRange.value[0])
const endDate = computed(() => dateRange.value[1])

// 实际今日日期（日统计始终用今天）
const todayStr = computed(() => fmtD(new Date()))

function onDateChange() {
  refresh()
}

function resetDateRange() {
  const d = new Date()
  const sixDaysAgo = new Date(d)
  sixDaysAgo.setDate(sixDaysAgo.getDate() - 6)
  dateRange.value = [fmtD(sixDaysAgo), fmtD(d)]
  refresh()
}

const chartSessionsRef = ref(null)
const chartRankRef = ref(null)
const chartSatisfactionRef = ref(null)
const chartWoStatusRef = ref(null)
const chartWoSatisRef = ref(null)
const woSatisFilter = ref('all')
let charts = []

const statCards = computed(() => {
  const d = store.dailyStats
  const m = store.monthlyStats
  const wo = store.workOrderStats
  return [
    { label: '今日接待会话', value: d?.sessionsHandled ?? '-', unit: '次', icon: '💬' },
    { label: '累计接待', value: m?.totalSessions ?? '-', unit: '次', icon: '📊' },
    { label: '客户满意度', value: m?.avgSatisfaction?.toFixed(1) ?? '-', unit: '分', icon: '⭐' },
    { label: 'SLA 达成率', value: d?.responseSlaComplianceRate?.toFixed(1) ?? '-', unit: '%', icon: '⏱️' },
    { label: '平均首次响应', value: d?.avgResponseSeconds != null ? (Math.max(0, d.avgResponseSeconds) / 60).toFixed(1) : '-', unit: 'min', icon: '⚡' },
    { label: '工单总数', value: wo?.totalCount ?? '-', unit: '个', icon: '📋' },
    { label: '已解决工单', value: wo?.completedCount ?? '-', unit: '个', icon: '✅' },
    { label: '工单解决率', value: wo?.resolutionRate?.toFixed(1) ?? '-', unit: '%', icon: '🎯' }
  ]
})

const woSatisfaction = computed(() => store.woSatisfaction || {})

const compareItems = computed(() => {
  const m = store.monthlyStats
  const ta = store.teamAverage
  if (!m && !ta) return []
  const mySessions = m?.totalSessions ?? null
  const teamSessions = ta?.teamTotalSessions ?? null
  const mySatisfaction = m?.avgSatisfaction ?? null
  const teamSatisfaction = ta?.teamAvgSatisfaction ?? null
  const mySla = store.monthlyStats?.responseSlaComplianceRate ?? null
  const teamSla = ta?.teamSlaRate ?? null
  const myResponse = store.dailyStats?.avgResponseSeconds ?? null
  const teamResponse = ta?.teamAvgFirstResponseSeconds ?? null

  const items = [
    { label: '接待量', meVal: mySessions, avgVal: teamSessions, meFmt: v => v != null ? v : '-', avgFmt: v => v != null ? v : '-', max: Math.max(mySessions ?? 0, teamSessions ?? 0, 1) },
    { label: '满意度', meVal: mySatisfaction, avgVal: teamSatisfaction, meFmt: v => v != null ? v.toFixed(1) : '-', avgFmt: v => v != null ? v.toFixed(1) : '-', max: 5 },
    { label: 'SLA达成率', meVal: mySla, avgVal: teamSla, meFmt: v => v != null ? v.toFixed(1) + '%' : '-', avgFmt: v => v != null ? v.toFixed(1) + '%' : '-', max: 100 },
    { label: '平均响应', meVal: myResponse != null ? Math.max(0, myResponse) : null, avgVal: teamResponse != null ? Math.max(0, teamResponse) : null, meFmt: v => v != null ? (v / 60).toFixed(1) + 'min' : '-', avgFmt: v => v != null ? (v / 60).toFixed(1) + 'min' : '-', max: Math.max(myResponse ?? 0, teamResponse ?? 0, 1) }
  ]
  return items.map(item => {
    const pct = (v, max, inverse) => {
      if (v == null) return '0%'
      const rawPct = max > 0 ? Math.min(v / max * 100, 100) : 0
      // 响应时间越短越好，所以反向
      if (inverse) return Math.max(0, Math.round(100 - rawPct)) + '%'
      return Math.round(rawPct) + '%'
    }
    const isInverse = item.label === '平均响应'
    return {
      label: item.label,
      me: item.meVal != null ? item.meFmt(item.meVal) : '-',
      avg: item.avgVal != null ? item.avgFmt(item.avgVal) : '-',
      mePct: pct(item.meVal, item.max, isInverse) || '0%',
      avgPct: pct(item.avgVal, item.max, isInverse) || '0%'
    }
  })
})

function renderSessionChart() {
  if (!chartSessionsRef.value) return
  let chart = echarts.getInstanceByDom(chartSessionsRef.value)
  if (!chart) {
    chart = echarts.init(chartSessionsRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const trend = store.trendStats
    if (!trend?.dates || trend.dates.length === 0 || !trend?.counts) {
      chart.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } }, series: [] })
      return
    }
    chart.setOption({
      tooltip: { trigger: 'item' },
      grid: { left: 10, right: 20, top: 10, bottom: 10, containLabel: true },
      xAxis: {
        type: 'category', data: trend?.dates || [],
        axisLine: { lineStyle: { color: '#dde4e8' } },
        axisLabel: { color: '#7b8c9a', fontSize: 11 }
      },
      yAxis: {
        type: 'value', minInterval: 5,
        splitLine: { lineStyle: { color: '#edf1f4' } },
        axisLabel: { color: '#7b8c9a', fontSize: 11 }
      },
      series: [{
        data: trend?.counts || [], type: 'bar', barWidth: 20,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'oklch(0.58 0.120 175)' },
            { offset: 1, color: 'oklch(0.45 0.100 175)' }
          ])
        },
        emphasis: {
          barWidth: 32,
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            shadowBlur: 12,
            shadowColor: 'oklch(0.50 0.130 175 / 0.35)',
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'oklch(0.68 0.150 175)' },
              { offset: 1, color: 'oklch(0.55 0.130 175)' }
            ])
          }
        }
      }]
    })
  }, 0)
}

function renderRankChart() {
  if (!chartRankRef.value) return
  let chart = echarts.getInstanceByDom(chartRankRef.value)
  if (!chart) {
    chart = echarts.init(chartRankRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const ranking = store.teamRanking?.ranking
    const myId = authStore.userId
    
    // 如果后端返回空ranking，构造仅含当前客服的默认排名
    let effectiveRanking = ranking
    if (!effectiveRanking || effectiveRanking.length === 0) {
      if (myId) {
        effectiveRanking = [{ agentId: myId, sessionCount: store.monthlyStats?.totalSessions || 0, rank: 1 }]
      } else {
        chart.setOption({ animation: false, title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } }, series: [] })
        return
      }
    }
    const names = []
    const data = []
    effectiveRanking.forEach(r => {
      const n = r.agentId === myId ? '我' : ('客服#' + r.agentId)
      names.push(n)
      data.push({
        value: r.sessionCount,
        itemStyle: {
          color: r.agentId === myId ? 'oklch(0.48 0.120 310)' : 'oklch(0.55 0.04 210)',
          borderRadius: [0, 6, 6, 0]
        }
      })
    })
    chart.setOption({
      animation: false,
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: params => params[0] ? `${params[0].name}: ${params[0].value} 次会话` : '' },
      grid: { left: 60, right: 20, top: 10, bottom: 10 },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1f4' } }, axisLabel: { color: '#7b8c9a', fontSize: 11 } },
      yAxis: { type: 'category', data: names, axisLabel: { color: '#3a4f5f', fontSize: 11, fontWeight: 500 }, inverse: true },
      series: [{ type: 'bar', barWidth: 14, data, label: { show: true, position: 'right', fontSize: 11, color: '#3a4f5f' } }]
    })
  }, 0)
}

function renderSatisfactionChart() {
  if (!chartSatisfactionRef.value) return
  let chart = echarts.getInstanceByDom(chartSatisfactionRef.value)
  if (!chart) {
    chart = echarts.init(chartSatisfactionRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const dist = store.satisfactionDist?.distribution
    if (!dist || !store.satisfactionDist?.total) {
      chart.setOption({ animation: false, title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } }, series: [] })
      return
    }
    const pieData = [5, 4, 3, 2, 1].map(n => ({
      value: dist[String(n)] || 0,
      name: n + '星',
      itemStyle: {
        color: n >= 4 ? `oklch(${0.52 + (5 - n) * 0.1} 0.135 175)` :
               n === 3 ? 'oklch(0.72 0.100 175)' :
               n === 2 ? 'oklch(0.62 0.140 75)' : 'oklch(0.50 0.160 20)'
      }
    }))
    chart.setOption({
      animation: false,
      tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
      series: [{
        type: 'pie', radius: ['50%', '78%'], center: ['50%', '52%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, position: 'outside', formatter: '{b}', fontSize: 11, color: '#3a4f5f' },
        emphasis: { scaleSize: 6, itemStyle: { shadowBlur: 12, shadowColor: 'oklch(0 0 0 / 0.18)' } },
        data: pieData
      }]
    })
  }, 0)
}

function renderWoStatusChart() {
  if (!chartWoStatusRef.value) return
  let chart = echarts.getInstanceByDom(chartWoStatusRef.value)
  if (!chart) {
    chart = echarts.init(chartWoStatusRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const wo = store.workOrderStats
    if (!wo || (wo.totalCount ?? 0) === 0) {
      chart.setOption({
        animation: false,
        title: { text: '暂无工单数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } },
        graphic: [{ type: 'text', left: 'center', top: '40%', style: { text: '📋', fontSize: 40, textAlign: 'center', opacity: 0.3 } }],
        series: []
      })
      return
    }
    const pieData = [
      { value: wo.pendingCount || 0, name: '待处理', itemStyle: { color: '#f0ad4e' } },
      { value: wo.processingCount || 0, name: '处理中', itemStyle: { color: '#5bc0de' } },
      { value: wo.completedCount || 0, name: '已完成', itemStyle: { color: '#5cb85c' } },
      { value: wo.cancelledCount || 0, name: '已取消', itemStyle: { color: '#d9534f' } }
    ]
    chart.setOption({
      animation: false,
      tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
      series: [{
        type: 'pie', radius: ['50%', '78%'], center: ['50%', '52%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, position: 'outside', formatter: '{b}', fontSize: 11, color: '#3a4f5f' },
        data: pieData
      }]
    })
  }, 0)
}

function renderWoSatisChart() {
  if (!chartWoSatisRef.value) return
  let chart = echarts.getInstanceByDom(chartWoSatisRef.value)
  if (!chart) {
    chart = echarts.init(chartWoSatisRef.value)
    if (!charts.includes(chart)) charts.push(chart)
  }
  setTimeout(() => {
    const dist = woSatisfaction.value?.distribution
    if (!dist || !woSatisfaction.value?.total) {
      chart.setOption({
        animation: false,
        title: { text: '暂无评价数据', left: 'center', top: 'center', textStyle: { color: '#9aa8b4', fontSize: 13 } },
        graphic: [{ type: 'text', left: 'center', top: '40%', style: { text: '⭐', fontSize: 40, textAlign: 'center', opacity: 0.3 } }],
        series: []
      })
      return
    }
    const pieData = [5, 4, 3, 2, 1].map(n => ({
      value: dist[String(n)] || 0,
      name: n + '星',
      itemStyle: {
        color: n >= 4 ? `oklch(${0.52 + (5 - n) * 0.1} 0.135 175)` :
               n === 3 ? 'oklch(0.72 0.100 175)' :
               n === 2 ? 'oklch(0.62 0.140 75)' : 'oklch(0.50 0.160 20)'
      }
    }))
    chart.setOption({
      animation: false,
      tooltip: { trigger: 'item', formatter: '{b}: {c} 单 ({d}%)' },
      series: [{
        type: 'pie', radius: ['50%', '78%'], center: ['50%', '52%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, position: 'outside', formatter: '{b}', fontSize: 11, color: '#3a4f5f' },
        emphasis: { scaleSize: 6, itemStyle: { shadowBlur: 12, shadowColor: 'oklch(0 0 0 / 0.18)' } },
        data: pieData
      }]
    })
  }, 0)
}

function onWoSatisFilterChange() {
  if (!agentId.value) return
  store.fetchWoSatisfaction(agentId.value, startDate.value, endDate.value, woSatisFilter.value).then(() => {
    setTimeout(() => renderWoSatisChart(), 50)
  })
}

function disposeCharts() {
  charts.forEach(c => c.dispose())
  charts = []
}

function renderAll() {
  setTimeout(() => { try { renderSessionChart() } catch(e) { console.error('[AgentInsight] renderSessionChart 异常:', e) } }, 100)
  setTimeout(() => { try { renderSatisfactionChart() } catch(e) { console.error('[AgentInsight] renderSatisfactionChart 异常:', e) } }, 150)
  setTimeout(() => { try { renderWoStatusChart() } catch(e) { console.error('[AgentInsight] renderWoStatusChart 异常:', e) } }, 200)
  setTimeout(() => { try { renderWoSatisChart() } catch(e) { console.error('[AgentInsight] renderWoSatisChart 异常:', e) } }, 250)
  setTimeout(() => { try { renderRankChart() } catch(e) { console.error('[AgentInsight] renderRankChart 异常:', e) } }, 300)
}

const agentId = computed(() => authStore.userId)

let refreshTimer = null

async function refresh() {
  if (!agentId.value) { console.warn('[AgentInsight] refresh: agentId 为空，跳过'); return }
  console.log('[AgentInsight] refresh: 开始加载, today=', todayStr.value, 'range=', startDate.value, '~', endDate.value)
  await store.fetchAll(agentId.value, endDate.value, startDate.value, endDate.value, startDate.value, endDate.value)
  renderAll()
}

onMounted(async () => {
  if (!agentId.value) return
  await store.fetchAll(agentId.value, endDate.value, startDate.value, endDate.value, startDate.value, endDate.value)
  renderAll()
  refreshTimer = setInterval(refresh, 30000)
})

onUnmounted(() => {
  clearInterval(refreshTimer)
  disposeCharts()
})
</script>

<style scoped>
.agent-insight { display: flex; flex-direction: column; height: 100%; }
.insight-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 28px; border-bottom: 1px solid var(--border-light);
  background: var(--surface); min-height: 60px;
}
.topbar-left { display: flex; align-items: center; gap: 16px; }
.insight-topbar h1 { font-family: var(--font-heading); font-size: 22px; font-weight: 700; color: var(--ink); }
.badge { font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 9999px; }
.badge-agent { background: oklch(0.90 0.030 310); color: oklch(0.48 0.120 310); }
.topbar-right { display: flex; align-items: center; gap: 16px; }
.date-range {
  display: flex; align-items: center; gap: 8px; background: var(--base);
  border: 1px solid var(--border); border-radius: 10px;
  padding: 6px 12px; font-size: 13px; color: var(--ink-soft);
  cursor: pointer; transition: border 150ms;
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
.reset-btn { width: 36px; height: 36px; border-radius: 10px; border: 1px solid var(--border); background: var(--base); display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 16px; color: var(--ink-soft); transition: all 150ms; }
.reset-btn:hover { border-color: var(--brand); color: var(--brand); }
.insight-stage { flex: 1; overflow-y: auto; padding: 28px; }
.stat-row { display: grid; gap: 16px; margin-bottom: 24px; }
.stat-row-4x2 { grid-template-columns: repeat(4, 1fr); }
.stat {
  background: var(--surface); border-radius: 16px; padding: 14px 16px;
  box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04);
  display: flex; flex-direction: column; gap: 8px;
  transition: transform 150ms cubic-bezier(0.34,1.56,0.64,1), box-shadow 150ms cubic-bezier(0.34,1.56,0.64,1);
  position: relative; overflow: hidden;
}
.stat:hover { transform: translateY(-2px); box-shadow: 0 8px 24px oklch(0.25 0.01 250 / 0.08); }
.stat-num { font-family: var(--font-heading); font-size: clamp(20px, 3vw, 26px); font-weight: 700; color: var(--ink); line-height: 1.1; }
.stat-num .unit { font-size: 0.45em; font-weight: 400; color: var(--ink-muted); margin-left: 2px; }
.stat-lab { font-size: 12px; color: var(--ink-muted); font-weight: 500; }
.stat-icon {
  position: absolute; top: 12px; right: 12px; font-size: 20px;
  opacity: 0.25; pointer-events: none;
}
.chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.chart-grid-2 { grid-template-columns: 1fr 1fr; }
.ch-card {
  background: var(--surface); border-radius: 16px; padding: 20px;
  box-shadow: 0 1px 2px oklch(0.25 0.01 250 / 0.04);
}
.ch-card h3 { font-family: var(--font-heading); font-size: clamp(17px,2.2vw,19px); font-weight: 600; color: var(--ink); margin-bottom: 12px; }
.ch-card-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.ch-card-head h3 { margin-bottom: 0; }
.wo-satis-select {
  padding: 4px 10px; border: 1px solid var(--border); border-radius: 8px;
  font-size: 12px; color: var(--ink-soft); background: var(--surface);
  cursor: pointer; outline: none;
}
.wo-satis-select:focus { border-color: var(--brand); }
.chart-box { width: 100%; height: 300px; }
.compare-list { display: flex; flex-direction: column; gap: 12px; }
.compare-legend { display: flex; gap: 20px; margin-bottom: 12px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--ink-muted); font-weight: 500; }
.legend-item i { display: inline-block; width: 12px; height: 12px; border-radius: 3px; }
.legend-item.me i { background: oklch(0.48 0.120 310); }
.legend-item.avg i { background: oklch(0.55 0.04 210); }
.compare-item { display: flex; flex-direction: column; gap: 6px; }
.compare-label { display: flex; justify-content: space-between; font-size: 12px; font-weight: 500; }
.cmp-name { color: var(--ink-soft); }
.cmp-val { color: var(--ink); font-weight: 600; }
.compare-bar { height: 8px; border-radius: 9999px; background: var(--border-light); overflow: hidden; }
.compare-fill { height: 100%; border-radius: 9999px; transition: width 0.8s cubic-bezier(0.34,1.56,0.64,1); }
.compare-fill.me { background: oklch(0.48 0.120 310); }
.compare-fill.avg { background: oklch(0.55 0.04 210); }

</style>
