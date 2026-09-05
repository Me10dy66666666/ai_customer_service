<template>
  <div class="ksd-page">
    <div v-if="loading" class="ksd-loading">加载中...</div>
    <template v-else>
      <div class="stat-row stat-row-5">
        <div class="stat"><span class="stat-num">{{ dashboard.published_count || 0 }}</span><span class="stat-lab">已发布文档</span></div>
        <div class="stat"><span class="stat-num">{{ dashboard.archived_count || 0 }}</span><span class="stat-lab">已归档</span></div>
        <div class="stat"><span class="stat-num">{{ dashboard.total_views || 0 }}</span><span class="stat-lab">时段浏览量</span></div>
        <div class="stat"><span class="stat-num">{{ (dashboard.top_documents || []).length }}</span><span class="stat-lab">活跃文档</span></div>
        <div class="stat"><span class="stat-num">{{ dashboard.synced_dify_count || 0 }}</span><span class="stat-lab">已同步至 Dify</span></div>
      </div>

      <div class="ksd-section">
        <h3>📊 文档浏览量 TOP10</h3>
        <div v-if="(dashboard.top_documents||[]).length === 0" class="ksd-empty">时段内暂无浏览记录</div>
        <div v-else class="ksd-bar-list">
          <div v-for="(doc, idx) in dashboard.top_documents" :key="idx" class="ksd-bar-item">
            <span class="ksd-bar-label">{{ idx + 1 }}. {{ doc.title }}</span>
            <span class="ksd-bar-count">{{ doc.view_count }} 次</span>
          </div>
        </div>
      </div>

      <div class="ksd-grid">
        <div class="ksd-section">
          <h3>🔍 搜索热词 TOP10</h3>
          <div v-if="(dashboard.hot_keywords||[]).length === 0" class="ksd-empty">暂无搜索记录</div>
          <div v-else class="ksd-tag-list">
            <span v-for="(kw, idx) in dashboard.hot_keywords" :key="idx" class="ksd-tag">
              {{ kw.keyword }} <small>({{ kw.search_count }})</small>
            </span>
          </div>
        </div>

        <div class="ksd-section">
          <h3>⚠ 搜索无结果词</h3>
          <div v-if="(dashboard.zero_result_keywords||[]).length === 0" class="ksd-empty ksd-empty-ok">暂无零结果搜索</div>
          <div v-else class="ksd-tag-list">
            <span v-for="(kw, idx) in dashboard.zero_result_keywords" :key="idx" class="ksd-tag ksd-tag-danger">
              {{ kw.keyword }} <small>({{ kw.search_count }})</small>
            </span>
          </div>
        </div>
      </div>

      <div class="ksd-section">
        <h3>💤 30天内从未被查看的文档</h3>
        <div v-if="(dashboard.unviewed_documents||[]).length === 0" class="ksd-empty ksd-empty-ok">所有文档均有人查看</div>
        <div v-else class="ksd-list">
          <div v-for="doc in dashboard.unviewed_documents" :key="doc.id" class="ksd-list-item">{{ doc.title }}</div>
        </div>
      </div>

      <div class="ksd-section">
        <h3>📈 月度浏览趋势</h3>
        <div ref="trendChart" class="ksd-chart"></div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import echarts from '@/shared/charts/echarts'
import http from '@/core/axios'

const loading = ref(true)
const dashboard = ref({})
const trendChart = ref(null)
let chartInstance = null

onMounted(async () => {
  await fetchData()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  chartInstance?.dispose()
  window.removeEventListener('resize', onResize)
})

async function fetchData() {
  loading.value = true
  try {
    const res = await http.get('/api/knowledge/stats/dashboard')
    if (res.data.code === 200) {
      dashboard.value = res.data.data
      await nextTick()
      renderTrendChart()
    }
  } catch { dashboard.value = {} }
  finally { loading.value = false }
}

function renderTrendChart() {
  if (!trendChart.value) return
  chartInstance?.dispose()
  chartInstance = echarts.init(trendChart.value)

  const trend = dashboard.value.monthly_trend || []
  const months = trend.map(t => t.month)
  const counts = trend.map(t => t.view_count)

  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 16, top: 16, bottom: 28 },
    xAxis: { type: 'category', data: months.length ? months : ['暂无'] },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: counts.length ? counts : [0],
      itemStyle: { color: 'oklch(0.52 0.135 175)', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

function onResize() { chartInstance?.resize() }
</script>

<style scoped>
.ksd-page { display: flex; flex-direction: column; gap: var(--s-5); }
.ksd-loading { text-align: center; padding: var(--s-10); color: var(--ink-soft); font-size: var(--text-sm); }

.stat-row { display: grid; gap: var(--s-4); }
.stat-row-4 { grid-template-columns: repeat(4, 1fr); }
.stat-row-5 { grid-template-columns: repeat(5, 1fr); }
.stat { background: var(--surface); border-radius: var(--radius-lg); padding: var(--s-5); box-shadow: var(--shadow-xs); display: flex; flex-direction: column; gap: var(--s-2); }
.stat-num { font-family: var(--font-heading); font-size: var(--text-2xl); font-weight: var(--weight-bold); color: var(--ink); }
.stat-lab { font-size: var(--text-2xs); color: var(--ink-muted); font-weight: var(--weight-medium); }

.ksd-section { background: var(--surface); border-radius: var(--radius-lg); padding: var(--s-5); box-shadow: var(--shadow-xs); }
.ksd-section h3 { font-family: var(--font-heading); font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--ink); margin: 0 0 var(--s-4) 0; }
.ksd-empty { color: var(--ink-soft); font-size: var(--text-sm); padding: var(--s-4) 0; }
.ksd-empty-ok { color: #389e0d; }

.ksd-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s-4); }

.ksd-bar-list { display: flex; flex-direction: column; gap: var(--s-2); }
.ksd-bar-item { display: flex; justify-content: space-between; align-items: center; padding: var(--s-2) var(--s-3); border-radius: var(--radius-sm); background: var(--base); }
.ksd-bar-label { font-size: var(--text-sm); color: var(--ink); flex: 1; }
.ksd-bar-count { font-size: var(--text-sm); color: var(--brand); font-weight: var(--weight-semibold); white-space: nowrap; margin-left: var(--s-4); }

.ksd-tag-list { display: flex; flex-wrap: wrap; gap: var(--s-2); }
.ksd-tag { font-size: var(--text-xs); padding: var(--s-1) var(--s-3); border-radius: var(--radius-full); background: var(--brand-soft); color: var(--brand); }
.ksd-tag small { opacity: 0.7; }
.ksd-tag-danger { background: #fff1f0; color: #cf1322; }

.ksd-list { display: flex; flex-direction: column; gap: var(--s-1); }
.ksd-list-item { font-size: var(--text-sm); color: var(--ink-soft); padding: var(--s-2) 0; border-bottom: 1px solid var(--border-light); }
.ksd-list-item:last-child { border-bottom: none; }

.ksd-chart { width: 100%; height: 260px; }
</style>
