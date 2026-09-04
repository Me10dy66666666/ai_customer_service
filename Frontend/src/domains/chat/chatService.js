import http from '@/core/axios'

export const getHistory = (sessionId) =>
  http.get('/api/chat/history', {
    params: { sessionId },
    headers: { 'X-Chat-Session-Token': sessionStorage.getItem('chat_session_token') || '' }
  })

export const getMessages = (sessionId) =>
  http.get('/api/chat/messages', {
    params: { sessionId },
    headers: { 'X-Chat-Session-Token': sessionStorage.getItem('chat_session_token') || '' }
  })

export const submitSatisfaction = (data) =>
  http.post('/api/chat/satisfaction', data)
