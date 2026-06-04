<template>
  <div class="da-page">
    <div class="da-tabs">
      <button v-for="t in tabs" :key="t.key" class="da-tab" :class="{ on: menu === t.key }" @click="switchMenu(t.key)">
        {{ t.label }}
      </button>
    </div>

    <div class="da-bar" v-if="menu !== 'user-profile'">
      <el-date-picker v-model="range" type="daterange" range-separator="→" start-placeholder="起" end-placeholder="止" value-format="YYYY-MM-DD" class="dp" @change="fetchAll" />
      <button class="btn-brand btn-sm" :loading="store.loading" @click="fetchAll">刷新</button>
      <button v-if="menu === 'consultation'" class="btn-ghost btn-sm" @click="doExport">导出 CSV</button>
    </div>

    <div class="da-body">
      <!-- Consultation -->
      <template v-if="menu === 'consultation'">
        <div class="stat-row">
          <div class="stat"><span class="stat-num">{{ store.dailyStats?.total_chats ?? 0 }}</span><span class="stat-lab">今日咨询</span></div>
          <div class="stat"><span class="stat-num">{{ store.dailyStats?.avg_satisfaction ?? '-' }}</span><span class="stat-lab">平均满意度</span></div>
          <div class="stat"><span class="stat-num">{{ trendTotal }}</span><span class="stat-lab">时段咨询</span></div>
        </div>
        <div class="ch-row">
          <div class="ch-card"><h3>咨询量趋势</h3><div ref="c0" class="ch"></div></div>
          <div class="ch-card"><h3>满意度分布</h3><div ref="c1" class="ch"></div></div>
        </div>
        <div class="stat-row stat-row-4">
          <div class="stat"><span class="stat-num">{{ store.aiResolutionStats?.overallResolutionRate ?? '0.00' }}%</span><span class="stat-lab">AI 解决率</span></div>
          <div class="stat"><span class="stat-num">{{ store.aiResolutionStats?.totalSessions ?? 0 }}</span><span class="stat-lab">总会话</span></div>
          <div class="stat"><span class="stat-num">{{ store.aiResolutionStats?.resolvedSessions ?? 0 }}</span><span class="stat-lab">AI 已解决</span></div>
          <div class="stat"><span class="stat-num">{{ store.aiResolutionStats?.manualTransferSessions ?? 0 }}</span><span class="stat-lab">转人工</span></div>
        </div>
        <div class="ch-card"><h3>AI 解决率趋势</h3><div ref="c2" class="ch"></div></div>
        <div class="tbl"><table><thead><tr><th>日期</th><th>咨询量</th><th>满意度</th><th>会话</th><th>解决</th><th>转人工</th><th>解决率</th></tr></thead><tbody><tr v-for="(r,i) in td" :key="i"><td>{{ r.date }}</td><td>{{ r.count }}</td><td>{{ r.sat }}</td><td>{{ r.ts }}</td><td>{{ r.rs }}</td><td>{{ r.mt }}</td><td>{{ r.rr }}</td></tr></tbody></table></div>
      </template>

      <!-- Work Order -->
      <template v-else-if="menu === 'workorder'">
        <div class="stat-row stat-row-4">
          <div class="stat"><span class="stat-num">{{ store.workOrderStats?.byStatus?.pending ?? 0 }}</span><span class="stat-lab">待处理</span></div>
          <div class="stat"><span class="stat-num">{{ store.workOrderStats?.byStatus?.processing ?? 0 }}</span><span class="stat-lab">处理中</span></div>
          <div class="stat"><span class="stat-num">{{ store.workOrderStats?.byStatus?.completed ?? 0 }}</span><span class="stat-lab">已完成</span></div>
          <div class="stat"><span class="stat-num">{{ store.workOrderStats?.byStatus?.cancelled ?? 0 }}</span><span class="stat-lab">已取消</span></div>
        </div>
        <div class="ch-row"><div class="ch-card"><h3>状态分布</h3><div ref="c3" class="ch"></div></div><div class="ch-card"><h3>趋势</h3><div ref="c4" class="ch"></div></div></div>
        <div class="tbl"><table><thead><tr><th>日期</th><th>工单量</th></tr></thead><tbody><tr v-for="(r,i) in wtd" :key="i"><td>{{ r.date }}</td><td>{{ r.count }}</td></tr></tbody></table></div>
      </template>

      <!-- User Profile -->
      <template v-else-if="menu === 'user-profile'"><UserData /></template>

      <!-- Conversion -->
      <template v-else-if="menu === 'conversion'">
        <div class="stat-row">
          <div class="stat"><span class="stat-num">{{ store.conversionStats?.overallRegistrationRate ?? '0.00' }}%</span><span class="stat-lab">注册转化率</span></div>
          <div class="stat"><span class="stat-num">{{ store.conversionStats?.overallPurchaseRate ?? '0.00' }}%</span><span class="stat-lab">购买转化率</span></div>
          <div class="stat"><span class="stat-num">{{ store.conversionStats?.totalConsultUsers ?? 0 }}</span><span class="stat-lab">咨询用户</span></div>
          <div class="stat"><span class="stat-num">{{ store.conversionStats?.totalRegisteredUsers ?? 0 }}</span><span class="stat-lab">注册转化</span></div>
          <div class="stat"><span class="stat-num">{{ store.conversionStats?.totalConvertedUsers ?? 0 }}</span><span class="stat-lab">购买转化</span></div>
        </div>
        <div class="ch-card"><h3>转化率趋势</h3><div ref="c5" class="ch"></div></div>
        <div class="tbl"><table><thead><tr><th>日期</th><th>咨询</th><th>注册</th><th>购买</th><th>注册率</th><th>购买率</th></tr></thead><tbody><tr v-for="(r,i) in cvd" :key="i"><td>{{ r.date }}</td><td>{{ r.cc }}</td><td>{{ r.rc }}</td><td>{{ r.pc }}</td><td>{{ r.rr }}</td><td>{{ r.pr }}</td></tr></tbody></table></div>
      </template>

      <!-- KB Effect -->
      <template v-else-if="menu === 'knowledge-effect'">
        <div class="stat-row stat-row-3">
          <div class="stat"><span class="stat-num">{{ store.kbEffectStats?.totalDocuments ?? 0 }}</span><span class="stat-lab">文档总数</span></div>
          <div class="stat"><span class="stat-num">{{ store.kbEffectStats?.enabledDocuments ?? 0 }}</span><span class="stat-lab">可用</span></div>
          <div class="stat"><span class="stat-num">{{ store.kbEffectStats?.totalHitCount ?? 0 }}</span><span class="stat-lab">命中</span></div>
          <div class="stat"><span class="stat-num">{{ store.kbEffectStats?.hitDocumentRate ?? '0.00' }}%</span><span class="stat-lab">命中率</span></div>
          <div class="stat"><span class="stat-num">{{ store.kbEffectStats?.totalWordCount ?? 0 }}</span><span class="stat-lab">词数</span></div>
          <div class="stat"><span class="stat-num">{{ store.kbEffectStats?.selectedDocumentCount ?? 0 }}</span><span class="stat-lab">时段新增</span></div>
        </div>
        <div class="ch-row">
          <div class="ch-card"><h3>类型分布</h3><div ref="c6" class="ch"></div></div>
          <div class="ch-card"><h3>状态分布</h3><div ref="c7" class="ch"></div></div>
          <div class="ch-card"><h3>新增趋势</h3><div ref="c8" class="ch"></div></div>
        </div>
        <div class="tbl"><table><thead><tr><th>文档</th><th>命中</th><th>词数</th><th>Token</th><th>类型</th><th>状态</th><th>创建</th></tr></thead><tbody><tr v-for="(d,i) in (store.kbEffectStats?.topDocuments || [])" :key="i"><td>{{ d.name }}</td><td>{{ d.hitCount }}</td><td>{{ d.wordCount }}</td><td>{{ d.tokens }}</td><td>{{ d.type }}</td><td>{{ d.status }}</td><td>{{ d.createdAt }}</td></tr></tbody></table></div>
      </template>

      <!-- Knowledge Dashboard -->
      <template v-else-if="menu === 'knowledge-dashboard'">
        <KnowledgeStatsDashboard />
      </template>

      <!-- SLA Performance -->
      <template v-else-if="menu === 'sla'">
        <div class="stat-row stat-row-4">
          <div class="stat"><span class="stat-num">{{ store.slaOverview?.totalWorkOrders ?? 0 }}</span><span class="stat-lab">总工单数</span></div>
          <div class="stat"><span class="stat-num">{{ slaFmt(store.slaOverview?.responseComplianceRate) }}%</span><span class="stat-lab">响应SLA达成率</span></div>
          <div class="stat"><span class="stat-num">{{ slaFmt(store.slaOverview?.resolutionComplianceRate) }}%</span><span class="stat-lab">解决SLA达成率</span></div>
          <div class="stat"><span class="stat-num">{{ slaFmt(store.slaOverview?.breachedRatio) }}%</span><span class="stat-lab">超时工单占比</span></div>
        </div>
        <div class="ch-card"><h3>SLA达成率趋势（按业务标签）</h3><div ref="c9" class="ch"></div></div>
        <div class="tbl"><table><thead><tr><th>排名</th><th>客服ID</th><th>工单数</th><th>SLA达标数</th><th>SLA达成率</th></tr></thead><tbody><tr v-for="(r,i) in slaRankingRows" :key="i"><td>{{ i + 1 }}</td><td>{{ r.agentId }}</td><td>{{ r.totalWorkOrders }}</td><td>{{ r.slaMetCount }}</td><td>{{ r.slaRate }}%</td></tr></tbody></table></div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useAnalyticsStore } from '@/shared/stores/analyticsStore'
import * as echarts from 'echarts'
import UserData from '@/domains/userprofile/UserData.vue'
import KnowledgeStatsDashboard from '@/domains/analytics/KnowledgeStatsDashboard.vue'

const store = useAnalyticsStore()
const menu = ref('consultation')
const range = ref([])

const tabs = [
  { key: 'consultation', label: '咨询' },
  { key: 'workorder', label: '工单' },
  { key: 'user-profile', label: '画像' },
  { key: 'conversion', label: '转化' },
  { key: 'knowledge-effect', label: '知识库' },
  { key: 'knowledge-dashboard', label: '知识统计' },
  { key: 'sla', label: 'SLA绩效' }
]

const tdRef = ref({ dates: [], counts: [], avgSatisfactions: [] })
const woRef = ref({ byStatus: {}, dates: [], counts: [] })
const cvRef = ref({ dates: [], consultCounts: [], registeredCounts: [], convertedCounts: [], registrationRates: [], purchaseRates: [] })
const aiRef = ref({ dates: [], totalSessionCounts: [], resolvedSessionCounts: [], manualTransferCounts: [], resolutionRates: [] })
const kbRef = ref({ dates: [], usageCounts: [], docsByCategory: {}, statusDistribution: {}, topDocuments: [] })
const slaBizTagRef = ref({ dates: [], presalesRates: [], aftersalesRates: [] })

const slaFmt = v => v == null ? '0.0' : Number(v).toFixed(1)

const slaRankingRows = computed(() => {
  const ranking = store.slaAgentRanking?.ranking || []
  return ranking.map(r => ({
    ...r,
    slaRate: r.slaRate == null ? '0.0' : Number(r.slaRate).toFixed(1)
  }))
})

const cRefs = { c0: ref(null), c1: ref(null), c2: ref(null), c3: ref(null), c4: ref(null), c5: ref(null), c6: ref(null), c7: ref(null), c8: ref(null), c9: ref(null) }
const charts = reactive(Object.fromEntries(Object.keys(cRefs).map(k => [k, null])))

const trendTotal = computed(() => { const c = tdRef.value.counts || []; return c.reduce((a, b) => a + Number(b || 0), 0) })

const td = computed(() => {
  const { dates = [], counts = [], avgSatisfactions = [] } = tdRef.value || {}
  const { totalSessionCounts = [], resolvedSessionCounts = [], manualTransferCounts = [], resolutionRates = [] } = aiRef.value || {}
  return dates.map((d, i) => ({ date: d, count: counts[i] ?? 0, sat: avgSatisfactions[i] ?? '-', ts: totalSessionCounts[i] ?? 0, rs: resolvedSessionCounts[i] ?? 0, mt: manualTransferCounts[i] ?? 0, rr: resolutionRates[i] ? `${resolutionRates[i]}%` : '0.00%' }))
})

const wtd = computed(() => { const { dates = [], counts = [] } = woRef.value || {}; return dates.map((d, i) => ({ date: d, count: counts[i] ?? 0 })) })

const cvd = computed(() => {
  const { dates = [], consultCounts = [], registeredCounts = [], convertedCounts = [], registrationRates = [], purchaseRates = [] } = cvRef.value || {}
  return dates.map((d, i) => ({ date: d, cc: consultCounts[i] ?? 0, rc: registeredCounts[i] ?? 0, pc: convertedCounts[i] ?? 0, rr: `${registrationRates[i] ?? '0.00'}%`, pr: `${purchaseRates[i] ?? '0.00'}%` }))
})

const fmtD = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const clr = { pri: 'oklch(0.52 0.135 175)', suc: 'oklch(0.50 0.150 150)', war: 'oklch(0.62 0.160 75)', dan: 'oklch(0.50 0.170 20)' }

const emptyGraphic = (show, text = '暂无数据') => show ? [{ type: 'text', left: 'center', top: 'middle', style: { text, fill: 'oklch(0.60 0.015 210)', fontSize: 14 } }] : []

const initChart = (k, dom) => { if (!dom.value) { return null } charts[k]?.dispose(); return echarts.init(dom.value) }
const getChart = (k, dom) => charts[k] && charts[k].getDom() === dom.value ? charts[k] : (charts[k] = initChart(k, dom))

const switchMenu = (k) => { menu.value = k; setTimeout(render, 50) }

const render = () => {
  if (menu.value === 'consultation') {
    lineChart(getChart('c0', cRefs.c0), tdRef.value.dates, tdRef.value.counts, clr.pri)
    pieChart(getChart('c1', cRefs.c1), store.dailyStats?.satisfaction_dist || {})
    aiBarLineChart(getChart('c2', cRefs.c2), aiRef.value)
  } else if (menu.value === 'workorder') {
    pieChart(getChart('c3', cRefs.c3), store.workOrderStats?.byStatus || {}, { pending: '待处理', processing: '处理中', completed: '已完成', cancelled: '已取消' })
    lineChart(getChart('c4', cRefs.c4), woRef.value.dates, woRef.value.counts, clr.war)
  } else if (menu.value === 'conversion') {
    multiLineChart(getChart('c5', cRefs.c5), cvRef.value)
  } else if (menu.value === 'knowledge-effect') {
    pieChart(getChart('c6', cRefs.c6), store.kbEffectStats?.docsByCategory || {})
    pieChart(getChart('c7', cRefs.c7), store.kbEffectStats?.statusDistribution || {})
    lineChart(getChart('c8', cRefs.c8), kbRef.value.dates, kbRef.value.creationCounts || kbRef.value.usageCounts, clr.pri)
  } else if (menu.value === 'sla') {
    slaBizTagLineChart(getChart('c9', cRefs.c9), slaBizTagRef.value)
  }
}

const lineChart = (c, dates, counts, color) => {
  if (!c) return
  setTimeout(() => { c.setOption({ tooltip: { trigger: 'axis' }, grid: { left: 40, right: 12, top: 8, bottom: 20 }, xAxis: { type: 'category', data: dates?.length ? dates : ['暂无'] }, yAxis: { type: 'value' }, graphic: emptyGraphic(!counts?.length), series: [{ type: 'line', data: counts?.length ? counts : [0], smooth: true, itemStyle: { color }, lineStyle: { color, width: 2 } }] }) }, 0)
}

const pieChart = (c, map, labels = {}) => {
  if (!c) return
  const data = Object.entries(map || {}).filter(([, v]) => Number(v) > 0).map(([k, v]) => ({ name: labels[k] || k, value: Number(v) }))
  setTimeout(() => { c.setOption({ tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['44%', '68%'], data: data.length ? data : [{ name: '暂无数据', value: 1 }], emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.1)' } } }] }) }, 0)
}

const multiLineChart = (c, data) => {
  if (!c) return
  const { dates = [], registrationRates = [], purchaseRates = [] } = data || {}
  setTimeout(() => { c.setOption({ tooltip: { trigger: 'axis' }, legend: { data: ['注册转化率', '购买转化率'] }, grid: { left: 40, right: 12, top: 30, bottom: 20 }, xAxis: { type: 'category', data: dates.length ? dates : ['暂无'] }, yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } }, graphic: emptyGraphic(!registrationRates.length), series: [{ name: '注册转化率', type: 'line', data: registrationRates.length ? registrationRates : [0], smooth: true, itemStyle: { color: clr.pri }, lineStyle: { width: 2 } }, { name: '购买转化率', type: 'line', data: purchaseRates.length ? purchaseRates : [0], smooth: true, itemStyle: { color: clr.suc }, lineStyle: { width: 2 } }] }) }, 0)
}

const aiBarLineChart = (c, data) => {
  if (!c) return
  const { dates = [], resolutionRates = [], totalSessionCounts = [], resolvedSessionCounts = [], manualTransferCounts = [] } = data || {}
  setTimeout(() => { c.setOption({ tooltip: { trigger: 'axis' }, legend: { data: ['解决率', '总会话', '已解决', '转人工'] }, grid: { left: 44, right: 12, top: 30, bottom: 20 }, xAxis: { type: 'category', data: dates.length ? dates : ['暂无'] }, yAxis: [{ type: 'value', axisLabel: { formatter: '{value}%' } }, { type: 'value' }], graphic: emptyGraphic(!resolutionRates.length), series: [{ name: '解决率', type: 'line', yAxisIndex: 0, data: resolutionRates.length ? resolutionRates : [0], smooth: true, itemStyle: { color: clr.suc }, lineStyle: { width: 2 } }, { name: '总会话', type: 'bar', yAxisIndex: 1, data: totalSessionCounts.length ? totalSessionCounts : [0], itemStyle: { color: clr.pri } }, { name: '已解决', type: 'bar', yAxisIndex: 1, data: resolvedSessionCounts.length ? resolvedSessionCounts : [0], itemStyle: { color: clr.suc } }, { name: '转人工', type: 'bar', yAxisIndex: 1, data: manualTransferCounts.length ? manualTransferCounts : [0], itemStyle: { color: clr.dan } }] }) }, 0)
}

const slaBizTagLineChart = (c, data) => {
  if (!c) return
  const { dates = [], presalesRates = [], aftersalesRates = [] } = data || {}
  setTimeout(() => { c.setOption({ tooltip: { trigger: 'axis' }, legend: { data: ['售前SLA', '售后SLA'] }, grid: { left: 40, right: 12, top: 30, bottom: 20 }, xAxis: { type: 'category', data: dates.length ? dates : ['暂无'] }, yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } }, graphic: emptyGraphic(!presalesRates.length), series: [{ name: '售前SLA', type: 'line', data: presalesRates.length ? presalesRates : [0], smooth: true, itemStyle: { color: clr.pri }, lineStyle: { width: 2 } }, { name: '售后SLA', type: 'line', data: aftersalesRates.length ? aftersalesRates : [0], smooth: true, itemStyle: { color: clr.suc }, lineStyle: { width: 2 } }] }) }, 0)
}

const fetchAll = async () => {
  if (!range.value || range.value.length < 2) return
  const [s, e] = range.value
  await store.fetchAll(s, e)
  await store.fetchSlaAll(s, e)
  if (store.trendStats) tdRef.value = store.trendStats
  if (store.workOrderStats) woRef.value = store.workOrderStats
  if (store.conversionStats) cvRef.value = store.conversionStats
  if (store.aiResolutionStats) aiRef.value = store.aiResolutionStats
  if (store.kbEffectStats) kbRef.value = store.kbEffectStats
  if (store.slaBizTagTrend) slaBizTagRef.value = store.slaBizTagTrend
  setTimeout(render, 50)
}

const doExport = () => { if (range.value?.length >= 2) store.doExport(range.value[0], range.value[1]) }

const onResize = () => Object.values(charts).forEach(c => c?.resize())

onMounted(() => { const e = new Date(); const s = new Date(); s.setDate(s.getDate() - 7); range.value = [fmtD(s), fmtD(e)]; fetchAll(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { Object.values(charts).forEach(c => c?.dispose()); window.removeEventListener('resize', onResize) })
</script>

<style scoped>
.da-page { display: flex; flex-direction: column; gap: var(--s-5); height: 100%; min-height: 0; padding: var(--page-pad-y) var(--page-pad-x); }
.da-tabs {
  display: flex; gap: var(--s-1); position: sticky; top: 0; z-index: 10;
  background: var(--base); margin: calc(-1 * var(--page-pad-y)) calc(-1 * var(--page-pad-x)) 0;
  padding: var(--s-3) var(--page-pad-x); flex-shrink: 0;
}
.da-tab { padding: var(--s-2) var(--s-5); border: none; border-radius: var(--radius-md); background: transparent; font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink-soft); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); }
.da-tab:hover { background: var(--base); color: var(--ink); }
.da-tab.on { background: var(--brand-soft); color: var(--brand); font-weight: var(--weight-semibold); }
.da-bar { display: flex; gap: var(--s-3); align-items: center; flex-shrink: 0; }
.dp { width: 250px; }
.da-body { flex: 1; display: flex; flex-direction: column; gap: var(--section-gap); overflow-y: auto; min-height: 0; }

.btn-brand { padding: var(--s-2) var(--s-5); border: none; border-radius: var(--radius-md); font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body); color: #fff; background: var(--brand); cursor: pointer; display: inline-flex; align-items: center; gap: var(--s-2); transition: background var(--dur-fast) var(--ease-soft); }
.btn-brand:hover { background: var(--brand-deep); }
.btn-ghost { padding: var(--s-2) var(--s-5); border: 1.5px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink-soft); background: var(--surface); cursor: pointer; transition: all var(--dur-fast) var(--ease-soft); }
.btn-ghost:hover { border-color: var(--ink-muted); color: var(--ink); }
.btn-sm { padding: var(--s-2) var(--s-4); font-size: var(--text-sm); }

.stat-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--s-4); }
.stat-row-4 { grid-template-columns: repeat(4, 1fr); }
.stat-row-3 { grid-template-columns: repeat(3, 1fr); }
.stat {
  background: var(--surface); border-radius: var(--radius-lg); padding: var(--s-5);
  box-shadow: var(--shadow-xs); display: flex; flex-direction: column; gap: var(--s-2);
  transition: transform var(--dur-fast) var(--ease-soft), box-shadow var(--dur-fast) var(--ease-soft);
}
.stat:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.stat-num { font-family: var(--font-heading); font-size: var(--text-2xl); font-weight: var(--weight-bold); color: var(--ink); }
.stat-lab { font-size: var(--text-2xs); color: var(--ink-muted); font-weight: var(--weight-medium); }

.ch-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s-4); }
.ch-card { background: var(--surface); border-radius: var(--radius-lg); padding: var(--s-5); box-shadow: var(--shadow-xs); }
.ch-card h3 { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); margin-bottom: var(--s-4); }
.ch { width: 100%; height: 280px; }

.da-section-divider { border-bottom: 1px dashed var(--border-light); }

.tbl { background: var(--surface); border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-xs); }
.tbl table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
.tbl th { text-align: left; padding: var(--s-3) var(--s-4); background: var(--base-alt); color: var(--ink-soft); font-weight: var(--weight-semibold); font-size: var(--text-2xs); text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1px solid var(--border-light); }
.tbl td { padding: var(--s-3) var(--s-4); color: var(--ink); border-bottom: 1px solid var(--border-light); }
.tbl tbody tr:last-child td { border-bottom: none; }
.tbl tbody tr:hover { background: var(--brand-pale); }

@media (prefers-reduced-motion: reduce) {
  .stat:hover { transform: none; }
}
</style>
