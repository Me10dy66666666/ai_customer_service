import http from '@/core/axios'

export const getPendingQueue = (agentId) => {
  const params = {}
  if (agentId) params.agentId = agentId
  return http.get('/api/agent/queue/pending', { params })
}

export const getAgentActiveSessions = (agentId) =>
  http.get('/api/agent/sessions/active', { params: { agentId } })
