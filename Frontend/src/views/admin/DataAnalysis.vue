<template>
  <div class="data-insight-container">
    <h2 class="page-title">数据洞察</h2>

    <!-- 左侧菜单 + 右侧内容 -->
    <div class="insight-layout">
      <aside class="insight-sidebar">
        <el-menu
          :default-active="activeMenu"
          class="insight-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="consultation">
            <span>咨询统计</span>
          </el-menu-item>
          <el-menu-item index="workorder">
            <span>工单分析</span>
          </el-menu-item>
          <el-menu-item index="user-profile">
            <span>用户画像</span>
          </el-menu-item>
          <el-menu-item index="conversion">
            <span>转化率分析</span>
          </el-menu-item>
          <el-menu-item index="knowledge-effect">
            <span>知识库效果</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <main class="insight-content">
        <!-- 时间范围与操作 -->
        <div class="control-panel" v-if="activeMenu !== 'user-profile'">
          <div class="date-picker">
            <label>时间范围：</label>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              @change="onDateRangeChange"
            />
          </div>
          <div class="actions">
            <el-button type="primary" @click="fetchAllData" :loading="loading">刷新数据</el-button>
            <el-button v-if="activeMenu === 'consultation'" @click="doExportReport">导出报告</el-button>
          </div>
        </div>

        <!-- 咨询统计 -->
        <template v-if="activeMenu === 'consultation'">
          <div class="overview-cards">
            <div class="card">
              <div class="card-title">今日咨询量</div>
              <div class="card-value">{{ dailyStats.total_chats ?? 0 }}</div>
            </div>
            <div class="card">
              <div class="card-title">今日平均满意度</div>
              <div class="card-value">{{ dailyStats.avg_satisfaction ?? '-' }}</div>
            </div>
            <div class="card">
              <div class="card-title">时间范围内咨询总量</div>
              <div class="card-value">{{ trendTotalChats }}</div>
            </div>
          </div>
          <div class="charts-section">
            <div class="chart-box">
              <h3>咨询量趋势</h3>
              <div ref="trendChartRef" class="chart"></div>
            </div>
            <div class="chart-box">
              <h3>满意度分布</h3>
              <div ref="satisfactionChartRef" class="chart"></div>
            </div>
          </div>
          <div class="table-section">
            <h3>详细数据</h3>
            <el-table :data="tableData" border style="width: 100%">
              <el-table-column prop="date" label="日期" width="180" />
              <el-table-column prop="count" label="咨询量" />
              <el-table-column prop="satisfaction" label="平均满意度" />
            </el-table>
          </div>
        </template>

        <!-- 工单分析 -->
        <template v-if="activeMenu === 'workorder'">
          <div class="overview-cards">
            <div class="card">
              <div class="card-title">待处理</div>
              <div class="card-value">{{ workOrderStats.byStatus?.pending ?? 0 }}</div>
            </div>
            <div class="card">
              <div class="card-title">处理中</div>
              <div class="card-value">{{ workOrderStats.byStatus?.processing ?? 0 }}</div>
            </div>
            <div class="card">
              <div class="card-title">已完成</div>
              <div class="card-value">{{ workOrderStats.byStatus?.completed ?? 0 }}</div>
            </div>
            <div class="card">
              <div class="card-title">已取消</div>
              <div class="card-value">{{ workOrderStats.byStatus?.cancelled ?? 0 }}</div>
            </div>
          </div>
          <div class="charts-section">
            <div class="chart-box">
              <h3>工单状态分布</h3>
              <div ref="workOrderPieRef" class="chart"></div>
            </div>
            <div class="chart-box chart-box-full">
              <h3>工单量趋势</h3>
              <div ref="workOrderTrendRef" class="chart"></div>
            </div>
          </div>
          <div class="table-section">
            <h3>工单每日统计</h3>
            <el-table :data="workOrderTableData" border style="width: 100%">
              <el-table-column prop="date" label="日期" width="180" />
              <el-table-column prop="count" label="工单量" />
            </el-table>
          </div>
        </template>

        <!-- 用户画像 -->
        <template v-if="activeMenu === 'user-profile'">
          <UserData />
        </template>

        <!-- 转化率分析 -->
        <template v-if="activeMenu === 'conversion'">
          <div class="overview-cards">
            <div class="card">
              <div class="card-title">整体转化率</div>
              <div class="card-value">{{ conversionStats.overallConversionRate ?? '0.00' }}%</div>
            </div>
            <div class="card">
              <div class="card-title">咨询用户数</div>
              <div class="card-value">{{ conversionStats.totalConsultUsers ?? 0 }}</div>
            </div>
            <div class="card">
              <div class="card-title">转化用户数</div>
              <div class="card-value">{{ conversionStats.totalConvertedUsers ?? 0 }}</div>
            </div>
          </div>
          <div class="charts-section">
            <div class="chart-box chart-box-full">
              <h3>转化率趋势</h3>
              <div ref="conversionChartRef" class="chart"></div>
            </div>
          </div>
          <div class="table-section">
            <h3>每日转化数据</h3>
            <el-table :data="conversionTableData" border style="width: 100%">
              <el-table-column prop="date" label="日期" width="180" />
              <el-table-column prop="consultCount" label="咨询用户数" />
              <el-table-column prop="convertedCount" label="转化用户数" />
              <el-table-column prop="rate" label="转化率" />
            </el-table>
          </div>
        </template>

        <!-- 知识库效果 -->
        <template v-if="activeMenu === 'knowledge-effect'">
          <div class="overview-cards">
            <div class="card">
              <div class="card-title">知识库文档总数</div>
              <div class="card-value">{{ kbEffectStats.totalDocuments ?? 0 }}</div>
            </div>
            <div class="card">
              <div class="card-title">已启用文档数</div>
              <div class="card-value">{{ kbEffectStats.enabledDocuments ?? 0 }}</div>
            </div>
            <div class="card">
              <div class="card-title">知识库使用率</div>
              <div class="card-value">{{ kbEffectStats.kbUsageRate ?? '0.00' }}%</div>
            </div>
            <div class="card">
              <div class="card-title">咨询总数</div>
              <div class="card-value">{{ kbEffectStats.totalConsultations ?? 0 }}</div>
            </div>
          </div>
          <div class="charts-section">
            <div class="chart-box">
              <h3>文档分类分布</h3>
              <div ref="kbCategoryChartRef" class="chart"></div>
            </div>
            <div class="chart-box">
              <h3>知识库使用趋势</h3>
              <div ref="kbUsageChartRef" class="chart"></div>
            </div>
          </div>
        </template>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDailyStats, getTrendStats, getWorkOrderStats, exportReport, getConversionStats, getKnowledgeBaseEffectStats } from '../../api/analysis'
import { ElMessage } from 'element-plus'
import UserData from './UserData.vue'

const loading = ref(false)
const dateRange = ref([])
const dailyStats = ref({})
const trendData = ref({ dates: [], counts: [], avgSatisfactions: [] })
const workOrderStats = ref({ byStatus: {}, dates: [], counts: [] })
const conversionStats = ref({})
const kbEffectStats = ref({})
const activeMenu = ref('consultation')

const trendChartRef = ref(null)
const satisfactionChartRef = ref(null)
const workOrderPieRef = ref(null)
const workOrderTrendRef = ref(null)
const conversionChartRef = ref(null)
const kbCategoryChartRef = ref(null)
const kbUsageChartRef = ref(null)
let trendChart = null
let satisfactionChart = null
let workOrderPieChart = null
let workOrderTrendChart = null
let conversionChart = null
let kbCategoryChart = null
let kbUsageChart = null

const trendTotalChats = computed(() => {
  const counts = trendData.value.counts || []
  return counts.reduce((a, b) => a + Number(b), 0)
})

const tableData = computed(() => {
  const d = trendData.value
  if (!d.dates || !d.dates.length) return []
  return d.dates.map((date, i) => ({
    date,
    count: d.counts?.[i] ?? 0,
    satisfaction: d.avgSatisfactions?.[i] ?? '-'
  }))
})

const workOrderTableData = computed(() => {
  const d = workOrderStats.value
  if (!d.dates || !d.dates.length) return []
  return d.dates.map((date, i) => ({
    date,
    count: d.counts?.[i] ?? 0
  }))
})

const conversionTableData = computed(() => {
  const d = conversionStats.value
  if (!d.dates || !d.dates.length) return []
  return d.dates.map((date, i) => ({
    date,
    consultCount: d.consultCounts?.[i] ?? 0,
    convertedCount: d.convertedCounts?.[i] ?? 0,
    rate: (d.conversionRates?.[i] ?? '0.00') + '%'
  }))
})

function initDateRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 7)
  dateRange.value = [
    start.toISOString().split('T')[0],
    end.toISOString().split('T')[0]
  ]
}

function handleMenuSelect(index) {
  activeMenu.value = index
  nextTick(() => {
    if (index === 'consultation') {
      updateTrendChart(trendData.value)
      updateSatisfactionChart(dailyStats.value.satisfaction_dist)
    } else if (index === 'workorder') {
      updateWorkOrderPie(workOrderStats.value.byStatus)
      updateWorkOrderTrend(workOrderStats.value.dates, workOrderStats.value.counts)
    } else if (index === 'conversion') {
      fetchConversionData()
    } else if (index === 'knowledge-effect') {
      fetchKnowledgeBaseEffectData()
    }
  })
}

function onDateRangeChange() {
  if (activeMenu.value === 'consultation') fetchTrendData()
  else if (activeMenu.value === 'workorder') fetchWorkOrderData()
  else if (activeMenu.value === 'conversion') fetchConversionData()
  else if (activeMenu.value === 'knowledge-effect') fetchKnowledgeBaseEffectData()
}

async function fetchAllData() {
  loading.value = true
  try {
    await fetchDailyData()
    if (activeMenu.value === 'consultation') {
      await fetchTrendData()
    } else {
      await fetchWorkOrderData()
    }
  } finally {
    loading.value = false
  }
}

async function fetchDailyData() {
  try {
    const res = await getDailyStats()
    if (res.data.code === 200) {
      dailyStats.value = res.data.data
      if (activeMenu.value === 'consultation') {
        updateSatisfactionChart(res.data.data.satisfaction_dist)
      }
    }
  } catch (e) {
    console.error(e)
  }
}

async function fetchTrendData() {
  if (!dateRange.value || dateRange.value.length < 2) return
  try {
    const [start, end] = dateRange.value
    const res = await getTrendStats(start, end)
    if (res.data.code === 200) {
      trendData.value = res.data.data
      updateTrendChart(res.data.data)
      if (activeMenu.value === 'consultation') {
        updateTrendChart(res.data.data)
      }
    }
  } catch (e) {
    console.error(e)
  }
}

async function fetchWorkOrderData() {
  if (!dateRange.value || dateRange.value.length < 2) return
  try {
    const [start, end] = dateRange.value
    const res = await getWorkOrderStats(start, end)
    if (res.data.code === 200) {
      workOrderStats.value = res.data.data
      if (activeMenu.value === 'workorder') {
        updateWorkOrderPie(res.data.data.byStatus)
        updateWorkOrderTrend(res.data.data.dates, res.data.data.counts)
      }
    }
  } catch (e) {
    console.error(e)
  }
}

async function fetchConversionData() {
  if (!dateRange.value || dateRange.value.length < 2) return
  try {
    const [start, end] = dateRange.value
    const res = await getConversionStats(start, end)
    if (res.data.code === 200) {
      conversionStats.value = res.data.data
      updateConversionChart(res.data.data)
    }
  } catch (e) {
    console.error(e)
  }
}

async function fetchKnowledgeBaseEffectData() {
  if (!dateRange.value || dateRange.value.length < 2) return
  try {
    const [start, end] = dateRange.value
    const res = await getKnowledgeBaseEffectStats(start, end)
    if (res.data.code === 200) {
      kbEffectStats.value = res.data.data
      updateKbCategoryChart(res.data.data.docsByCategory)
      updateKbUsageChart(res.data.data.dates, res.data.data.usageCounts)
    }
  } catch (e) {
    console.error(e)
  }
}

function updateTrendChart(data) {
  if (!trendChartRef.value || !data?.dates?.length) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.dates },
    yAxis: { type: 'value' },
    series: [{
      data: data.counts,
      type: 'line',
      smooth: true,
      itemStyle: { color: '#1890ff' }
    }]
  })
}

function updateSatisfactionChart(dist) {
  if (!satisfactionChartRef.value) return
  if (!satisfactionChart) satisfactionChart = echarts.init(satisfactionChartRef.value)
  const map = dist || {}
  const data = Object.entries(map)
    .filter(([, v]) => Number(v) > 0)
    .map(([name, value]) => ({ name, value }))
  if (!data.length) data.push({ name: '暂无数据', value: 1 })
  satisfactionChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '5%' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      label: { show: false, position: 'center' },
      emphasis: { label: { show: true, fontSize: 20, fontWeight: 'bold' } },
      data
    }]
  })
}

const statusLabels = {
  pending: '待处理',
  processing: '处理中',
  completed: '已完成',
  cancelled: '已取消'
}

function updateWorkOrderPie(byStatus) {
  if (!workOrderPieRef.value) return
  if (!workOrderPieChart) workOrderPieChart = echarts.init(workOrderPieRef.value)
  const map = byStatus || {}
  const data = Object.entries(map)
    .filter(([, v]) => Number(v) > 0)
    .map(([key, value]) => ({ name: statusLabels[key] || key, value }))
  if (!data.length) data.push({ name: '暂无数据', value: 1 })
  workOrderPieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '5%' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 }
    }]
  })
}

function updateWorkOrderTrend(dates, counts) {
  if (!workOrderTrendRef.value || !dates?.length) return
  if (!workOrderTrendChart) workOrderTrendChart = echarts.init(workOrderTrendRef.value)
  workOrderTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [{ data: counts || [], type: 'bar', itemStyle: { color: '#52c41a' } }]
  })
}

function updateConversionChart(data) {
  if (!conversionChartRef.value || !data?.dates?.length) return
  if (!conversionChart) conversionChart = echarts.init(conversionChartRef.value)
  conversionChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['咨询用户数', '转化用户数', '转化率'] },
    xAxis: { type: 'category', data: data.dates },
    yAxis: [
      { type: 'value', name: '用户数' },
      { type: 'value', name: '转化率(%)', axisLabel: { formatter: '{value}%' } }
    ],
    series: [
      {
        name: '咨询用户数',
        type: 'bar',
        data: data.consultCounts || [],
        itemStyle: { color: '#1890ff' }
      },
      {
        name: '转化用户数',
        type: 'bar',
        data: data.convertedCounts || [],
        itemStyle: { color: '#52c41a' }
      },
      {
        name: '转化率',
        type: 'line',
        yAxisIndex: 1,
        data: data.conversionRates || [],
        smooth: true,
        itemStyle: { color: '#f5a623' }
      }
    ]
  })
}

function updateKbCategoryChart(docsByCategory) {
  if (!kbCategoryChartRef.value) return
  if (!kbCategoryChart) kbCategoryChart = echarts.init(kbCategoryChartRef.value)
  const map = docsByCategory || {}
  const data = Object.entries(map)
    .filter(([, v]) => Number(v) > 0)
    .map(([name, value]) => ({ name, value }))
  if (!data.length) data.push({ name: '暂无数据', value: 1 })
  kbCategoryChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '5%' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 }
    }]
  })
}

function updateKbUsageChart(dates, usageCounts) {
  if (!kbUsageChartRef.value || !dates?.length) return
  if (!kbUsageChart) kbUsageChart = echarts.init(kbUsageChartRef.value)
  kbUsageChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series: [{
      name: '知识库使用次数',
      data: usageCounts || [],
      type: 'line',
      smooth: true,
      itemStyle: { color: '#722ed1' }
    }]
  })
}

async function doExportReport() {
  if (!dateRange.value || dateRange.value.length < 2) {
    ElMessage.warning('请先选择时间范围')
    return
  }
  try {
    const [startDate, endDate] = dateRange.value
    const res = await exportReport(startDate, endDate)
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `consultation_report_${startDate}_${endDate}.csv`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('报告已导出')
  } catch (e) {
    ElMessage.error('导出失败')
    console.error(e)
  }
}

function handleResize() {
  trendChart?.resize()
  satisfactionChart?.resize()
  workOrderPieChart?.resize()
  workOrderTrendChart?.resize()
  conversionChart?.resize()
  kbCategoryChart?.resize()
  kbUsageChart?.resize()
}

onMounted(() => {
  initDateRange()
  nextTick(() => {
    fetchAllData()
    window.addEventListener('resize', handleResize)
  })
})
</script>

<style scoped>
.data-insight-container {
  padding: 0;
}

.page-title {
  margin: 0 0 20px 0;
  font-size: 1.25rem;
  color: #1f2329;
}

.insight-layout {
  display: flex;
  gap: 20px;
  min-height: 500px;
}

.insight-sidebar {
  width: 200px;
  flex-shrink: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.insight-menu {
  border-right: none;
}

.insight-content {
  flex: 1;
  min-width: 0;
}

.control-panel {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.date-picker {
  display: flex;
  align-items: center;
  gap: 10px;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.card {
  background: white;
  padding: 25px 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  text-align: center;
}

.card-title {
  color: #888;
  font-size: 0.9rem;
  margin-bottom: 10px;
}

.card-value {
  font-size: 1.75rem;
  font-weight: bold;
  color: #333;
}

.charts-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.chart-box {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.chart-box-full {
  grid-column: 1 / -1;
}

.chart {
  height: 350px;
  width: 100%;
}

.table-section {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.table-section h3,
.chart-box h3 {
  margin-top: 0;
  margin-bottom: 20px;
  font-size: 1.1rem;
  color: #333;
}
</style>
