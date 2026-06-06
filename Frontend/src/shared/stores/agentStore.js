import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useAuthStore } from '@/shared/stores/authStore'
import { useWorkOrderStore } from '@/shared/stores/workOrderStore'
import { getMessages, getHistory } from '@/domains/chat/chatService'
import { getPendingQueue, getAgentActiveSessions } from '@/domains/agent/agentService'

const WS_URL = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/chat`
const HEARTBEAT_INTERVAL_MS = 30_000
const MAX_RECONNECT = 5
const RECONNECT_BASE_MS = 1000

export const useAgentStore = defineStore('agent', () => {
  const socket = ref(null)
  const connected = ref(false)
  const online = ref(false)
  const queue = ref([])
  const activeSessionId = ref(null)
  const activeMessages = ref([])
  const viewOnly = ref(false)
  const queueSize = ref(0)
  const reconnectAttempts = ref(0)
  const sessionHistory = ref([])
  const sessionSummary = ref(null)
  const sessionAiMessages = ref([])
  const searchKeyword = ref('')
  const statusFilter = ref('all')
  let heartbeatTimer = null
  let reconnectTimer = null
  let connectResolve = null
  let connectReject = null

  const filteredQueue = computed(() => {
    let list = queue.value
    if (searchKeyword.value) {
      const kw = searchKeyword.value.toLowerCase()
      list = list.filter(q => {
        const uid = q.userId ? String(q.userId) : ''
        const intent = q.intent ? q.intent.toLowerCase() : ''
        const sid = q.sessionId ? q.sessionId.toLowerCase() : ''
        return uid.includes(kw) || intent.includes(kw) || sid.includes(kw)
      })
    }
    if (statusFilter.value === 'waiting') {
      list = list.filter(q => !q.completed && q.status === 'WAITING')
    } else if (statusFilter.value === 'active') {
      list = list.filter(q => !q.completed && q.status === 'HUMAN')
    } else if (statusFilter.value === 'completed') {
      list = list.filter(q => q.completed || q.status === 'CLOSED')
    }
    return list
  })

  const connect = () => {
    return new Promise((resolve, reject) => {
      connectResolve = resolve
      connectReject = reject

      if (socket.value && socket.value.readyState === WebSocket.OPEN) {
        resolve()
        return
      }

      const auth = useAuthStore()
      if (!auth.userId) {
        reject(new Error('未登录或用户ID无效，请重新登录'))
        return
      }

      attemptConnect()
    })
  }

  const attemptConnect = () => {
    if (socket.value) {
      try { socket.value.close() } catch (e) { /* ignore */ }
      socket.value = null
    }

    const ws = new WebSocket(WS_URL)

    ws.onopen = () => {
      socket.value = ws
      connected.value = true
      const auth = useAuthStore()
      ws.send(JSON.stringify({ action: 'register', agentId: auth.userId }))
      startHeartbeat()
    }

    ws.onmessage = (event) => {
      let msg = null
      try { msg = JSON.parse(event.data) } catch { return }
      if (!msg || !msg.type) return

      switch (msg.type) {
        case 'connected':
          break

        case 'registered':
          online.value = true
          reconnectAttempts.value = 0
          queueSize.value = msg.queueSize || 0
          fetchPendingQueue()
          if (connectResolve) {
            connectResolve()
            connectResolve = null
            connectReject = null
          }
          break

        case 'register_failed':
          if (connectReject) {
            connectReject(new Error(msg.content || '客服注册失败'))
            connectReject = null
            connectResolve = null
          }
          socket.value?.close()
          break

        case 'agent_queue_notify':
          const existing = queue.value.find(q => q.sessionId === msg.sessionId)
          if (existing) {
            existing.completed = false
            existing.status = 'WAITING'
            existing.timestamp = Date.now()
            existing.position = msg.position || 0
            existing.estimatedWait = msg.estimatedWait || 0
          } else {
            queue.value.push({
              sessionId: msg.sessionId,
              userId: msg.userId,
              intent: msg.intent,
              lastMessage: '',
              timestamp: Date.now(),
              position: msg.position || 0,
              estimatedWait: msg.estimatedWait || 0,
              messages: [],
              completed: false,
              priority: null,
              tags: null,
              aiMessages: [],
              status: 'WAITING'
            })
          }
          queueSize.value = queue.value.filter(q => !q.completed).length
          break

        case 'user_msg':
          if (msg.sessionId === activeSessionId.value && !viewOnly.value) {
            activeMessages.value.push({
              from: 'user',
              content: msg.content,
              time: nowTime()
            })
          }
          break

        case 'echo':
          if (!msg.rejected && !viewOnly.value) {
            activeMessages.value.push({
              from: 'agent',
              content: msg.content,
              time: nowTime()
            })
          }
          break

        case 'claimed':
          break

        case 'claim_failed':
          alert(msg.content || '该会话已被其他客服认领')
          break

        case 'service_ended':
          {
            const sid = msg.sessionId || activeSessionId.value
            if (sid) {
              const entry = queue.value.find(q => q.sessionId === sid)
              if (entry) {
                entry.completed = true
                entry.status = 'CLOSED'
                if (msg.content) {
                  entry.messages.push({
                    from: 'system',
                    content: msg.content,
                    time: nowTime()
                  })
                }
              }
            }
          }
          if (activeSessionId.value === msg.sessionId || !msg.sessionId) {
            viewOnly.value = false
            activeSessionId.value = null
            activeMessages.value = []
          }
          break

        case 'transferred':
          queue.value = queue.value.filter(q => q.sessionId !== activeSessionId.value)
          viewOnly.value = false
          activeSessionId.value = null
          activeMessages.value = []
          queueSize.value = queue.value.filter(q => !q.completed).length
          break

        case 'sessions_transferred_in':
          queueSize.value = queue.value.filter(q => !q.completed).length
          break

        case 'heartbeat_ack':
          online.value = true
          if (msg.queueSize !== undefined) queueSize.value = msg.queueSize
          break

        case 'back_to_ai':
        case 'session_closed':
        case 'session_timeout':
          if (msg.sessionId) {
            const entry = queue.value.find(q => q.sessionId === msg.sessionId)
            if (entry) {
              entry.completed = true
              entry.status = 'CLOSED'
            }
          }
          if (activeSessionId.value === (msg.sessionId || activeSessionId.value)) {
            viewOnly.value = false
            activeSessionId.value = null
            activeMessages.value = []
          }
          break

        case 'summary_ready':
          if (msg.sessionId === activeSessionId.value) {
            sessionSummary.value = { priority: msg.priority, content: msg.content, tags: msg.tags }
            getMessages(msg.sessionId).then(res => {
              if (res.data.code === 200) {
                sessionHistory.value = res.data.data
              }
            }).catch(err => {
              console.error('Failed to refresh session history after summary:', err)
            })
          }
          {
            const entry = queue.value.find(q => q.sessionId === msg.sessionId)
            if (entry) {
              if (msg.priority) entry.priority = msg.priority
              if (msg.tags) entry.tags = msg.tags
            }
          }
          break

        case 'SUMMARY_READY':
          try {
            const woStore = useWorkOrderStore()
            const targetWo = woStore.workOrders.find(w => w.id === msg.workOrderId)
            if (targetWo) {
              if (msg.priority) targetWo.priority = msg.priority
              if (msg.tags) targetWo.tags = msg.tags
              if (msg.summary) targetWo.summary = msg.summary
              if (msg.bizTag) targetWo.bizTag = msg.bizTag
              if (msg.emotionLevel) targetWo.emotionLevel = msg.emotionLevel
              if (msg.dispatchConfidence) targetWo.dispatchConfidence = msg.dispatchConfidence
            }
          } catch (e) {
            console.error('SUMMARY_READY handler error:', e)
          }
          break

        case 'session_dispatched':
          {
            const sessionId = msg.payload?.sessionId || msg.sessionId
            const agentId = msg.payload?.agentId || msg.agentId
            const auth = useAuthStore()
            if (sessionId && agentId === auth.userId) {
              fetchPendingQueue()
            }
          }
          break

        default:
          break
      }
    }

    ws.onerror = () => {
      if (connectReject && reconnectAttempts.value === 0) {
        connectReject(new Error('Agent WS 连接失败'))
        connectReject = null
        connectResolve = null
      }
    }

    ws.onclose = () => {
      socket.value = null
      connected.value = false
      online.value = false
      stopHeartbeat()

      if (reconnectAttempts.value < MAX_RECONNECT) {
        reconnectAttempts.value++
        const delay = RECONNECT_BASE_MS * Math.pow(2, reconnectAttempts.value - 1)
        reconnectTimer = setTimeout(() => {
          attemptConnect()
        }, delay)
      }
    }
  }

  const startHeartbeat = () => {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (socket.value && socket.value.readyState === WebSocket.OPEN) {
        socket.value.send(JSON.stringify({ action: 'heartbeat' }))
      }
    }, HEARTBEAT_INTERVAL_MS)
  }

  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  const fetchPendingQueue = async () => {
    const auth = useAuthStore()
    if (!auth.userId) {
      console.warn('[AgentStore] fetchPendingQueue skipped: no userId')
      return
    }

    let sessions = null
    try {
      const res = await getAgentActiveSessions(auth.userId)
      if (res.data.code === 200) {
        sessions = res.data.data
      }
    } catch (err) {
      console.warn('[AgentStore] getAgentActiveSessions failed, falling back:', err.message)
    }

    if (!sessions || !sessions.length) {
      try {
        const fallbackRes = await getPendingQueue(auth.userId)
        if (fallbackRes.data.code === 200) {
          const fallbackList = fallbackRes.data.data || []
          sessions = fallbackList.map(item => ({
            sessionId: item.sessionId,
            userId: item.userId,
            intent: item.intent || '',
            status: 'WAITING',
            position: item.position || 0,
            estimatedWait: item.estimatedWait || 0,
            priority: null,
            tags: null,
            summary: null,
            aiMessages: []
          }))
        }
      } catch (err2) {
        console.error('[AgentStore] fetchPendingQueue completely failed:', err2)
        return
      }
    }

    if (!sessions || !sessions.length) {
      queueSize.value = queue.value.filter(q => !q.completed).length
      return
    }

    const storedSessionIds = new Set()
    const allCompleted = []

    for (const ctx of sessions) {
      storedSessionIds.add(ctx.sessionId)
      const existing = queue.value.find(q => q.sessionId === ctx.sessionId)
      if (existing) {
        existing.position = ctx.position || 0
        existing.estimatedWait = ctx.estimatedWait || 0
        if (ctx.priority) existing.priority = ctx.priority
        if (ctx.tags) existing.tags = ctx.tags
        if (ctx.aiMessages) existing.aiMessages = ctx.aiMessages
        if (ctx.status) existing.status = ctx.status
      } else {
        queue.value.push({
          sessionId: ctx.sessionId,
          userId: ctx.userId,
          intent: ctx.intent || '',
          lastMessage: '',
          timestamp: Date.now(),
          position: ctx.position || 0,
          estimatedWait: ctx.estimatedWait || 0,
          messages: [],
          completed: false,
          priority: ctx.priority || null,
          tags: ctx.tags || null,
          aiMessages: ctx.aiMessages || [],
          status: ctx.status || 'WAITING'
        })
      }
    }

    for (const entry of queue.value) {
      if (!storedSessionIds.has(entry.sessionId) && entry.completed) {
        allCompleted.push(entry)
      }
    }

    queue.value = queue.value.filter(q => storedSessionIds.has(q.sessionId) || q.completed)
    queueSize.value = queue.value.filter(q => !q.completed).length
  }

  const stripThinkTags = (content) => {
    if (!content) return ''
    return content.replace(/<think>[\s\S]*?<\/think>/g, '').trim()
  }

  const fetchAiConversation = async (sessionId) => {
    if (!sessionId) return
    try {
      const res = await getHistory(sessionId)
      if (res.data.code === 200 && res.data.data) {
        const messages = []
        for (const log of res.data.data) {
          if (log.userInput) {
            messages.push({ role: 'user', content: log.userInput, time: log.createTime || '' })
          }
          if (log.aiResponse) {
            const cleanedResponse = stripThinkTags(log.aiResponse)
            messages.push({ role: 'ai', content: cleanedResponse, time: log.createTime || '' })
          }
        }
        sessionAiMessages.value = messages
      }
    } catch (err) {
      console.error('Failed to fetch AI conversation for', sessionId, ':', err.message)
    }
  }

  const restoreSessionSummary = (chatMessages) => {
    if (!chatMessages || !chatMessages.length) return
    for (const m of chatMessages) {
      if (m.senderType !== 'SYSTEM' || !m.content) continue
      try {
        const parsed = JSON.parse(m.content)
        if (parsed.priority && parsed.summary) {
          sessionSummary.value = {
            priority: parsed.priority,
            content: parsed.summary,
            tags: parsed.tags || ''
          }
          return
        }
      } catch { /* not valid JSON, skip */ }
    }
  }

  const claimSession = async (sessionId) => {
    const entry = queue.value.find(q => q.sessionId === sessionId)
    if (!entry || entry.completed) return
    viewOnly.value = false
    sessionSummary.value = null
    sessionAiMessages.value = entry.aiMessages || []
    activeSessionId.value = sessionId
    if (entry) {
      activeMessages.value = (entry.messages || []).filter(m => m.from !== 'system')
    }
    socket.value?.send(JSON.stringify({ action: 'claim', sessionId }))
    try {
      const [msgRes] = await Promise.all([
        getMessages(sessionId),
        entry.aiMessages && entry.aiMessages.length ? Promise.resolve() : fetchAiConversation(sessionId)
      ])
      if (msgRes.data.code === 200) {
        sessionHistory.value = msgRes.data.data
        restoreSessionSummary(msgRes.data.data)
        const typeMap = { USER: 'user', AGENT: 'agent', SYSTEM: 'system' }
        activeMessages.value = msgRes.data.data
          .filter(m => m.senderType !== 'SYSTEM')
          .map(m => ({
            from: typeMap[m.senderType] || 'system',
            content: m.content,
            time: m.createTime
              ? new Date(m.createTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
              : ''
          }))
      }
    } catch (err) {
      console.error('Failed to load session history:', err)
    }
  }

  const viewHistory = async (sessionId) => {
    const entry = queue.value.find(q => q.sessionId === sessionId)
    if (!entry) return
    viewOnly.value = true
    activeSessionId.value = sessionId
    sessionAiMessages.value = entry.aiMessages && entry.aiMessages.length
      ? entry.aiMessages
      : []
    try {
      const [msgRes] = await Promise.all([
        getMessages(sessionId),
        entry.aiMessages && entry.aiMessages.length ? Promise.resolve() : fetchAiConversation(sessionId)
      ])
      if (msgRes.data.code === 200) {
        const msgs = msgRes.data.data
        sessionHistory.value = msgs
        restoreSessionSummary(msgs)
        const typeMap = { USER: 'user', AGENT: 'agent', SYSTEM: 'system' }
        activeMessages.value = msgs
          .filter(m => m.senderType !== 'SYSTEM')
          .map(m => ({
            from: typeMap[m.senderType] || 'system',
            content: m.content,
            time: m.createTime
              ? new Date(m.createTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
              : ''
          }))
      }
    } catch (err) {
      console.error('Failed to load session history:', err)
    }
  }

  const clearView = () => {
    viewOnly.value = false
    sessionSummary.value = null
    sessionAiMessages.value = []
    sessionHistory.value = []
    activeSessionId.value = null
    activeMessages.value = []
  }

  const sendMessage = (content) => {
    if (!activeSessionId.value || !socket.value || viewOnly.value) return
    socket.value.send(JSON.stringify({
      action: 'agent_message',
      sessionId: activeSessionId.value,
      content
    }))
  }

  const transferToAgent = (targetAgentId) => {
    if (!activeSessionId.value || !socket.value) return
    socket.value.send(JSON.stringify({
      action: 'transfer_to_agent',
      sessionId: activeSessionId.value,
      targetAgentId
    }))
  }

  const transferToAi = () => {
    if (!activeSessionId.value || !socket.value) return
    const sid = activeSessionId.value
    socket.value.send(JSON.stringify({ action: 'transfer_ai', sessionId: sid }))
    const entry = queue.value.find(q => q.sessionId === sid)
    if (entry) entry.completed = true
    queueSize.value = queue.value.filter(q => !q.completed).length
    viewOnly.value = false
    activeSessionId.value = null
    activeMessages.value = []
  }

  const requestSatisfaction = () => {
    if (!activeSessionId.value || !socket.value) return
    socket.value.send(JSON.stringify({ action: 'request_satisfaction', sessionId: activeSessionId.value }))
  }

  const closeSession = () => {
    if (!activeSessionId.value || !socket.value) return
    const sid = activeSessionId.value
    socket.value.send(JSON.stringify({ action: 'close', sessionId: sid }))
    const entry = queue.value.find(q => q.sessionId === sid)
    if (entry) entry.completed = true
    queueSize.value = queue.value.filter(q => !q.completed).length
    viewOnly.value = false
    activeSessionId.value = null
    activeMessages.value = []
  }

  const disconnect = () => {
    stopHeartbeat()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    reconnectAttempts.value = MAX_RECONNECT
    connectResolve = null
    connectReject = null
    socket.value?.close()
    socket.value = null
    connected.value = false
    online.value = false
  }

  const nowTime = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

  return {
    connected,
    online,
    queue,
    filteredQueue,
    queueSize,
    activeSessionId,
    activeMessages,
    viewOnly,
    sessionHistory,
    sessionSummary,
    sessionAiMessages,
    reconnectAttempts,
    searchKeyword,
    statusFilter,
    connect,
    claimSession,
    viewHistory,
    clearView,
    sendMessage,
    transferToAgent,
    transferToAi,
    requestSatisfaction,
    closeSession,
    disconnect,
    fetchPendingQueue,
    fetchAiConversation
  }
})
