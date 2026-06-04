import http from '@/core/axios'

export const getPendingQueue = () => http.get('/api/agent/queue/pending')

export const getAgentActiveSessions = (agentId) =>
  http.get('/api/agent/sessions/active', { params: { agentId } })
