<template>
  <div class="user-data-container" id="user-report-content">
    <div class="header">
      <h2>用户数据分析</h2>
      <el-button type="primary" @click="fetchData">刷新数据</el-button>
    </div>

    <!-- Filter Bar -->
    <div class="filter-bar">
      <!-- User Type Dropdown -->
      <el-select v-model="filters.userType" placeholder="用户类型" clearable class="filter-item">
        <el-option label="全部" value="All" />
        <el-option label="Unregistered" value="UNREGISTERED" />
        <el-option label="Registered" value="REGISTERED" />
        <el-option label="High-Potential" value="HIGH_POTENTIAL" />
        <el-option label="Member" value="MEMBER" />
      </el-select>

      <!-- User ID Input -->
      <el-input 
        v-model="filters.userId" 
        placeholder="用户 ID" 
        clearable 
        class="filter-item"
        @clear="filters.userId = ''"
      />

      <!-- Date Range -->
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        class="filter-item"
        value-format="YYYY-MM-DD"
      />

      <!-- Buttons -->
      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button @click="clearFilters">清空</el-button>
    </div>

    <!-- Chart Section -->
    <div class="chart-container">
      <div id="userTypeChart" style="width: 100%; height: 300px;"></div>
      <el-button class="export-btn" @click="exportPDF">导出报表</el-button>
    </div>

    <!-- Data List -->
    <el-table :data="tableData" style="width: 100%" v-loading="loading">
      <el-table-column prop="userId" label="User ID" width="120">
        <template #default="scope">
          {{ scope.row.userId || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="sessionId" label="Session ID" width="180" show-overflow-tooltip />
      <el-table-column prop="userType" label="类型" width="120">
        <template #default="scope">
          <el-tag :type="getTypeTag(scope.row.userType || (scope.row.userId ? 'REGISTERED' : 'UNREGISTERED'))">
            {{ scope.row.userType || (scope.row.userId ? 'REGISTERED' : 'UNREGISTERED') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tags" label="用户标签" min-width="150">
        <template #default="scope">
          <el-tag 
            v-for="tag in (scope.row.tags ? scope.row.tags.split(',') : [])" 
            :key="tag" 
            size="small" 
            style="margin-right: 5px"
            effect="plain"
          >
            {{ tag }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalSpending" label="总消费" width="120">
        <template #default="scope">
          ¥{{ scope.row.totalSpending || 0 }}
        </template>
      </el-table-column>
      <el-table-column prop="serviceTimes" label="互动频次" width="100" />
      <el-table-column prop="satisfactionScore" label="满意度" width="100">
        <template #default="scope">
          {{ scope.row.satisfactionScore ? scope.row.satisfactionScore.toFixed(1) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="lastServiceTime" label="最近互动" width="180">
        <template #default="scope">
          {{ formatDate(scope.row.lastServiceTime) }}
        </template>
      </el-table-column>
    </el-table>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { searchProfiles } from '../../api/analysis'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const chartInstance = ref(null)

const filters = reactive({
  userType: 'All',
  userId: '',
  dateRange: []
})

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      userType: filters.userType === 'All' ? '' : filters.userType,
      userId: filters.userId,
      startTime: filters.dateRange && filters.dateRange[0] ? filters.dateRange[0] + 'T00:00:00' : null,
      endTime: filters.dateRange && filters.dateRange[1] ? filters.dateRange[1] + 'T23:59:59' : null
    }
    const res = await searchProfiles(params)
    if (res.data.code === 200) {
      tableData.value = res.data.data
      updateChart(res.data.data)
    } else {
      ElMessage.error(res.data.message || '查询失败')
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
  }
}

const clearFilters = () => {
  filters.userType = 'All'
  filters.userId = ''
  filters.dateRange = []
  fetchData()
}

const updateChart = (data) => {
  if (!chartInstance.value) {
    const chartDom = document.getElementById('userTypeChart')
    if (chartDom) {
        chartInstance.value = echarts.init(chartDom)
    }
  }
  
  if (!chartInstance.value) return;

  const counts = {
    '未注册访客': 0,
    '注册用户': 0,
    '高潜用户': 0,
    '会员': 0
  }
  
  const typeMap = {
    'UNREGISTERED': '未注册访客',
    'REGISTERED': '注册用户',
    'HIGH_POTENTIAL': '高潜用户',
    'MEMBER': '会员'
  }
  
  data.forEach(item => {
    let type = item.userType || 'UNREGISTERED'
    
    if (type === 'UNREGISTERED' && item.userId) {
        // If user has spending > 0, they are MEMBER, else REGISTERED
        if (item.totalSpending && item.totalSpending > 0) {
            type = 'MEMBER'
        } else {
            type = 'REGISTERED'
        }
    }

    // Fallback for empty type
    if (!typeMap[type]) type = 'UNREGISTERED'
    
    const label = typeMap[type]
    counts[label]++
  })
  
  const option = {
    title: { text: '用户类型占比柱状图', left: 'center' },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'category', data: Object.keys(counts) },
    yAxis: { type: 'value' },
    series: [{
      data: Object.values(counts),
      type: 'bar',
      itemStyle: {
        color: (params) => {
          const colors = ['#95a5a6', '#3498db', '#f1c40f', '#e74c3c'] // Grey, Blue, Yellow, Red
          // Order: Unregistered, Registered, High-Potential, Member
          return colors[params.dataIndex] || '#3498db'
        }
      }
    }]
  }
  
  chartInstance.value.setOption(option)
}

const exportPDF = async () => {
  const element = document.getElementById('user-report-content')
  if (!window.html2canvas || !window.jspdf) {
    ElMessage.error('Export library not loaded. Please check network.')
    return
  }
  
  // Temporarily hide buttons for clean export
  const buttons = element.querySelectorAll('button')
  buttons.forEach(btn => btn.style.display = 'none')

  try {
    const canvas = await window.html2canvas(element, { scale: 2 })
    const imgData = canvas.toDataURL('image/png')
    const { jsPDF } = window.jspdf
    const pdf = new jsPDF('p', 'mm', 'a4')
    const imgProps = pdf.getImageProperties(imgData)
    const pdfWidth = pdf.internal.pageSize.getWidth()
    const pdfHeight = (imgProps.height * pdfWidth) / imgProps.width
    
    pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight)
    pdf.save('user-report.pdf')
  } catch (err) {
    console.error(err)
    ElMessage.error('Export failed')
  } finally {
    // Restore buttons
    buttons.forEach(btn => btn.style.display = '')
  }
}

const getTypeTag = (type) => {
  switch (type) {
    case 'MEMBER': return 'danger'
    case 'HIGH_POTENTIAL': return 'warning'
    case 'REGISTERED': return 'success'
    default: return 'info'
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.user-data-container {
  padding: 0;
  background-color: transparent;
  min-height: auto;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.filter-bar {
  display: flex;
  gap: 15px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
  background: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
.filter-item {
  width: 200px;
}
.chart-container {
  position: relative;
  margin-bottom: 20px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
.export-btn {
  position: absolute;
  bottom: 20px;
  right: 20px;
}
</style>
