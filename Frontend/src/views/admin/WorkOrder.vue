<template>
  <div class="work-order-container">
    <h2>工单管理</h2>

    <!-- 筛选区域 -->
    <div class="filter-container">
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width: 200px; margin-right: 10px">
        <el-option label="待处理" value="pending" />
        <el-option label="处理中" value="processing" />
        <el-option label="已完成" value="completed" />
        <el-option label="已取消" value="cancelled" />
      </el-select>
      <el-button type="primary" @click="fetchWorkOrders">查询</el-button>
    </div>

    <!-- 工单列表 -->
    <el-table :data="filteredWorkOrders" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="工单ID" width="100" />
      <el-table-column prop="title" label="标题" show-overflow-tooltip />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === '售前' ? 'success' : 'warning'">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="100">
        <template #default="{ row }">
          <el-tag :type="getPriorityType(row.priority)">{{ row.priority }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleEdit(row)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 处理工单弹窗 -->
    <el-dialog v-model="dialogVisible" title="处理工单" width="500px">
      <el-form :model="currentOrder" label-width="100px">
        <el-form-item label="工单标题">
          <el-input v-model="currentOrder.title" disabled />
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input v-model="currentOrder.description" type="textarea" disabled :rows="3" />
        </el-form-item>
        <el-form-item label="当前状态">
          <el-select v-model="form.status" placeholder="请选择状态">
            <el-option label="待处理" value="pending" />
            <el-option label="处理中" value="processing" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input v-model="form.result" type="textarea" :rows="3" placeholder="请输入处理结果或备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitUpdate" :loading="submitting">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getWorkOrders, updateWorkOrderStatus } from '../../api/workOrder'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const workOrders = ref([])
const filterStatus = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const currentOrder = ref({})
const form = ref({
  status: '',
  result: ''
})

// 获取工单列表
const fetchWorkOrders = async () => {
  loading.value = true
  try {
    const res = await getWorkOrders()
    if (res.data.code === 200) {
      workOrders.value = res.data.data
    } else {
      ElMessage.error(res.data.msg || '获取工单列表失败')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('获取工单列表失败')
  } finally {
    loading.value = false
  }
}

// 过滤后的列表
const filteredWorkOrders = computed(() => {
  if (!filterStatus.value) return workOrders.value
  return workOrders.value.filter(order => order.status === filterStatus.value)
})

// 打开处理弹窗
const handleEdit = (row) => {
  currentOrder.value = { ...row }
  form.value = {
    status: row.status,
    result: row.result || ''
  }
  dialogVisible.value = true
}

// 提交更新
const submitUpdate = async () => {
  submitting.value = true
  try {
    // 假设当前管理员ID为 1 (实际应从 store 获取)
    const handlerId = 1 
    const res = await updateWorkOrderStatus(currentOrder.value.id, form.value.status, handlerId, form.value.result)
    
    if (res.data.code === 200) {
      ElMessage.success('工单更新成功')
      dialogVisible.value = false
      fetchWorkOrders() // 刷新列表
    } else {
      ElMessage.error(res.data.msg || '更新失败')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('更新失败')
  } finally {
    submitting.value = false
  }
}

// 辅助函数
const getPriorityType = (priority) => {
  const map = { high: 'danger', medium: 'warning', low: 'info' }
  return map[priority] || 'info'
}

const getStatusType = (status) => {
  const map = { pending: 'info', processing: 'primary', completed: 'success', cancelled: 'danger' }
  return map[status] || 'info'
}

const getStatusLabel = (status) => {
  const map = { pending: '待处理', processing: '处理中', completed: '已完成', cancelled: '已取消' }
  return map[status] || status
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}

onMounted(() => {
  fetchWorkOrders()
})
</script>

<style scoped>
.work-order-container {
  padding: 20px;
}
.filter-container {
  margin-bottom: 20px;
}
</style>
