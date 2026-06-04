import http from '@/core/axios'

export const searchAgents = (params) => http.get('/api/admin/agents', { params })
export const getAgent = (id) => http.get(`/api/admin/agents/${id}`)
export const createAgent = (data) => http.post('/api/admin/agents', data)
export const updateAgent = (id, data) => http.put(`/api/admin/agents/${id}`, data)
export const deleteAgent = (id) => http.delete(`/api/admin/agents/${id}`)
export const batchDeleteAgents = (ids) => http.post('/api/admin/agents/batch-delete', { ids })
export const batchUpdateAgentStatus = (ids, status) => http.post('/api/admin/agents/batch-status', { ids, status })

export const searchUsers = (params) => http.get('/api/admin/users', { params })
export const toggleUserStatus = (id, status) => http.put(`/api/admin/users/${id}/status`, { status })
