import http from '@/core/axios'

export const getHistory = (sessionId) =>
  http.get('/api/chat/history', { params: { sessionId } })

export const getMessages = (sessionId) =>
  http.get('/api/chat/messages', { params: { sessionId } })

export const submitSatisfaction = (data) =>
  http.post('/api/chat/satisfaction', data)
