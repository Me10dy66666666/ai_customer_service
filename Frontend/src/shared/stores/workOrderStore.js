import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'
import {
  createWorkOrder,
  getWorkOrders,
  getUnassignedWorkOrders,
  updateWorkOrderStatus,
  claimWorkOrder,
  replyWorkOrder,
  transferWorkOrder,
  pauseSla,
  resumeSla
} from '@/domains/workorder/workOrderService'
import { useAuthStore } from '@/shared/stores/authStore'

export const useWorkOrderStore = defineStore('workorder', () => {
  const workOrders = ref([])
  const loading = ref(false)
  const submitting = ref(false)
  const showDialog = ref(false)
  const totalCount = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(50)
  const hasUnreadWoUpdate = ref(false)

  const workOrderCount = computed(() => workOrders.value.length)

  const newWorkOrder = reactive({
    title: '',
    type: '售后',
    description: '',
    priority: 'medium',
    sessionId: null
  })

  const statusLabels = {
    pending: '待处理',
    processing: '处理中',
    completed: '已完成',
    cancelled: '已取消',
    closed: '已取消'
  }

  const statusClasses = {
    pending: '',
    processing: 'status-processing',
    completed: 'status-done',
    cancelled: 'status-cancelled',
    closed: 'status-cancelled'
  }

  const priorityLabels = {
    high: '高',
    medium: '中',
    low: '低'
  }

  const getStatusLabel = (status) => statusLabels[status] || status
  const getStatusClass = (status) => statusClasses[status] || ''
  const getPriorityLabel = (priority) => priorityLabels[priority] || priority

  const fetchWorkOrders = async () => {
    const auth = useAuthStore()
    if (!auth.userId) return

    loading.value = true
    try {
      const res = await getWorkOrders(auth.userId)
      if (res.data.code === 200) {
        const payload = res.data.data
        workOrders.value = payload.list !== undefined ? (payload.list || []) : (payload || [])
      }
    } catch (err) {
      console.error('Failed to fetch work orders:', err)
    } finally {
      loading.value = false
    }
  }

  const fetchAllWorkOrders = async (page = 1) => {
    loading.value = true
    try {
      const auth = useAuthStore()
      const res = await getWorkOrders(null, page, pageSize.value, auth.userId)
      if (res.data.code === 200) {
        const payload = res.data.data
        if (payload.list !== undefined) {
          workOrders.value = payload.list || []
          totalCount.value = payload.total || 0
          currentPage.value = payload.page || page
        } else {
          workOrders.value = payload || []
          totalCount.value = workOrders.value.length
        }
      }
    } catch (err) {
      console.error('Failed to fetch all work orders:', err)
    } finally {
      loading.value = false
    }
  }

  const fetchUnassignedWorkOrders = async () => {
    loading.value = true
    try {
      const res = await getUnassignedWorkOrders()
      if (res.data.code === 200) {
        workOrders.value = res.data.data || []
      }
    } catch (err) {
      console.error('Failed to fetch unassigned work orders:', err)
    } finally {
      loading.value = false
    }
  }

  const openDialog = (sessionId) => {
    newWorkOrder.title = ''
    newWorkOrder.type = '售后'
    newWorkOrder.description = ''
    newWorkOrder.sessionId = sessionId || null
    showDialog.value = true
  }

  const closeDialog = () => {
    showDialog.value = false
  }

  const submit = async (overrides = {}) => {
    const auth = useAuthStore()
    if (!auth.userId) return { success: false, message: '请先登录' }
    if (!newWorkOrder.title || !newWorkOrder.description) {
      return { success: false, message: '请填写完整信息' }
    }

    submitting.value = true
    try {
      const payload = {
        userId: overrides.userId || auth.userId,
        title: newWorkOrder.title,
        type: newWorkOrder.type,
        description: newWorkOrder.description,
        priority: newWorkOrder.priority,
        sessionId: newWorkOrder.sessionId,
        creatorAgentId: overrides.creatorAgentId || null
      }
      const res = await createWorkOrder(payload)
      if (res.data.code === 200) {
        closeDialog()
        await fetchWorkOrders()
        return { success: true }
      }
      return { success: false, message: res.data.msg }
    } catch (err) {
      console.error('Submit work order failed:', err)
      return { success: false, message: '提交失败' }
    } finally {
      submitting.value = false
    }
  }

  const claimWorkOrderById = async (id) => {
    const auth = useAuthStore()
    if (!auth.userId) return { success: false, message: '请先登录' }
    try {
      const res = await claimWorkOrder(id, auth.userId)
      if (res.data.code === 200 && res.data.data.claimed) {
        return { success: true }
      }
      return { success: false, message: res.data.data.message || '认领失败' }
    } catch (err) {
      console.error('Claim work order failed:', err)
      return { success: false, message: '认领失败' }
    }
  }

  const updateStatus = async (id, status, handlerId, result) => {
    try {
      const res = await updateWorkOrderStatus(id, status, handlerId, result)
      if (res.data.code === 200) {
        const wo = workOrders.value.find(w => w.id === id)
        if (wo) {
          wo.status = status
          wo.result = result
        }
        return { success: true }
      }
      return { success: false, message: res.data.msg }
    } catch (err) {
      console.error('Update work order status failed:', err)
      return { success: false, message: '更新状态失败' }
    }
  }

  const replyToWorkOrder = async (workOrderId, content, agentId) => {
    try {
      const res = await replyWorkOrder(workOrderId, content, agentId)
      if (res.data.code === 200) {
        return { success: true, data: res.data.data }
      }
      return { success: false, message: res.data.message || '回复失败' }
    } catch (err) {
      console.error('Reply to work order failed:', err)
      return { success: false, message: err.response?.data?.message || '回复失败，工单可能未关联会话' }
    }
  }

  const transferToAgent = async (workOrderId, targetHandlerId, reason) => {
    try {
      const res = await transferWorkOrder(workOrderId, targetHandlerId, reason)
      if (res.data.code === 200) {
        const wo = workOrders.value.find(w => w.id === workOrderId)
        if (wo) {
          wo.handlerId = targetHandlerId
          wo.status = 'processing'
        }
        return { success: true }
      }
      return { success: false, message: res.data.message || '转移失败' }
    } catch (err) {
      console.error('Transfer work order failed:', err)
      return { success: false, message: '转移失败' }
    }
  }

  const pauseSlaAction = async (workOrderId, reason, agentId) => {
    try {
      const res = await pauseSla(workOrderId, reason, agentId)
      if (res.data.code === 200) {
        await fetchAllWorkOrders(currentPage.value)
        return { success: true, data: res.data.data }
      }
      return { success: false, message: res.data.message || '暂停SLA失败' }
    } catch (err) {
      console.error('Pause SLA failed:', err)
      return { success: false, message: err.response?.data?.message || '暂停SLA失败' }
    }
  }

  const resumeSlaAction = async (workOrderId, agentId) => {
    try {
      const res = await resumeSla(workOrderId, agentId)
      if (res.data.code === 200) {
        await fetchAllWorkOrders(currentPage.value)
        return { success: true, data: res.data.data }
      }
      return { success: false, message: res.data.message || '恢复SLA失败' }
    } catch (err) {
      console.error('Resume SLA failed:', err)
      return { success: false, message: err.response?.data?.message || '恢复SLA失败' }
    }
  }

  return {
    workOrders,
    loading,
    submitting,
    showDialog,
    newWorkOrder,
    workOrderCount,
    totalCount,
    currentPage,
    pageSize,
    hasUnreadWoUpdate,
    statusLabels,
    statusClasses,
    priorityLabels,
    getStatusLabel,
    getStatusClass,
    getPriorityLabel,
    fetchWorkOrders,
    fetchAllWorkOrders,
    fetchUnassignedWorkOrders,
    openDialog,
    closeDialog,
    submit,
    claimWorkOrderById,
    updateStatus,
    replyToWorkOrder,
    transferToAgent,
    pauseSlaAction,
    resumeSlaAction
  }
})
