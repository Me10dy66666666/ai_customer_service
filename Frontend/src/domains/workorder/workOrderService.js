import http from '@/core/axios'

export const createWorkOrder = (workOrder) => http.post('/api/work-orders', workOrder)
export const getWorkOrders = (userId, page = 1, size = 50) => {
  const params = { page, size }
  if (userId) params.userId = userId
  return http.get('/api/work-orders', { params })
}
export const getWorkOrder = (id) => http.get(`/api/work-orders/${id}`)
export const getUnassignedWorkOrders = () => http.get('/api/work-orders/unassigned')
export const updateWorkOrderStatus = (id, status, handlerId, result) =>
  http.put(`/api/work-orders/${id}/status`, { status, handlerId, result })
export const claimWorkOrder = (id, handlerId) =>
  http.post(`/api/work-orders/${id}/claim`, null, { params: { handlerId } })
export const replyWorkOrder = (id, content, agentId) =>
  http.post(`/api/work-orders/${id}/reply`, { content, agentId })
export const transferWorkOrder = (id, targetHandlerId, reason) =>
  http.post(`/api/work-orders/${id}/transfer`, { targetHandlerId, reason })
export const pauseSla = (id, reason, agentId) =>
  http.post(`/api/work-orders/${id}/pause-sla`, { reason, agentId })
export const resumeSla = (id, agentId) =>
  http.post(`/api/work-orders/${id}/resume-sla`, { agentId })
