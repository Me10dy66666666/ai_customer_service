import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  searchAgents,
  createAgent,
  updateAgent,
  deleteAgent,
  batchDeleteAgents,
  batchUpdateAgentStatus
} from '@/domains/agentmgmt/agentManagementService'

export const useAgentManagementStore = defineStore('agentManagement', () => {
  const agents = ref([])
  const total = ref(0)
  const loading = ref(false)
  const saving = ref(false)

  const fetchAgents = async (params = {}) => {
    loading.value = true
    try {
      const res = await searchAgents({ page: 1, size: 20, ...params })
      if (res.data.code === 200) {
        const data = res.data.data
        agents.value = data.list || []
        total.value = data.total || 0
      }
    } catch (err) {
      console.error('Failed to fetch agents:', err)
    } finally {
      loading.value = false
    }
  }

  const addAgent = async (data) => {
    saving.value = true
    try {
      const res = await createAgent(data)
      if (res.data.code === 200) {
        return { success: true, data: res.data.data }
      }
      return { success: false, message: res.data.message }
    } catch (err) {
      return { success: false, message: '创建失败' }
    } finally {
      saving.value = false
    }
  }

  const editAgent = async (id, data) => {
    saving.value = true
    try {
      const res = await updateAgent(id, data)
      if (res.data.code === 200) {
        return { success: true, data: res.data.data }
      }
      return { success: false, message: res.data.message }
    } catch (err) {
      return { success: false, message: '更新失败' }
    } finally {
      saving.value = false
    }
  }

  const removeAgent = async (id) => {
    try {
      const res = await deleteAgent(id)
      if (res.data.code === 200) {
        agents.value = agents.value.filter(a => a.id !== id)
        total.value = Math.max(0, total.value - 1)
        return { success: true }
      }
      return { success: false, message: res.data.message }
    } catch (err) {
      return { success: false, message: '删除失败' }
    }
  }

  const toggleAgentStatus = async (id, currentStatus) => {
    const newStatus = currentStatus === 1 ? 0 : 1
    const res = await updateAgent(id, { status: newStatus })
    if (res.data.code === 200) {
      const agent = agents.value.find(a => a.id === id)
      if (agent) agent.status = newStatus
      return { success: true }
    }
    return { success: false, message: res.data.message }
  }

  const batchDelete = async (ids) => {
    try {
      const res = await batchDeleteAgents(ids)
      if (res.data.code === 200) {
        agents.value = agents.value.filter(a => !ids.includes(a.id))
        total.value = Math.max(0, total.value - ids.length)
        return { success: true }
      }
      return { success: false, message: res.data.message }
    } catch {
      return { success: false, message: '批量删除失败' }
    }
  }

  const batchUpdateStatus = async (ids, status) => {
    try {
      const res = await batchUpdateAgentStatus(ids, status)
      if (res.data.code === 200) {
        for (const a of agents.value) {
          if (ids.includes(a.id)) a.status = status
        }
        return { success: true }
      }
      return { success: false, message: res.data.message }
    } catch {
      return { success: false, message: '批量操作失败' }
    }
  }

  return {
    agents, total, loading, saving,
    fetchAgents, addAgent, editAgent, removeAgent, toggleAgentStatus,
    batchDelete, batchUpdateStatus
  }
})
