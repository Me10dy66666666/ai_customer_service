import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'
import { getHistory, submitSatisfaction } from '@/domains/chat/chatService'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { createWebSocketClient } from '@/shared/composables/useWebSocket'
import { useWorkOrderStore } from '@/shared/stores/workOrderStore'
import http from '@/core/axios'

const WS_URL = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/chat`
const MAX_RECONNECT = 5
const RECONNECT_BASE_MS = 1000

export const useChatStore = defineStore('chat', () => {
  const messages = ref([])
  const loading = ref(false)
  const awaitingResponse = ref(false)
  const currentAiMessage = ref(null)
  const wsClient = ref(null)
  const sessionId = ref('')
  const transferring = ref(false)
  const showSatisfaction = ref(false)
  const waitPosition = ref(0)
  const estimatedWait = ref(0)
  const humanSessionActive = ref(false)
  const currentAgentId = ref(null)
  let reconnectTimer = null

  const hasMessages = computed(() => messages.value.length > 0)
  const waitTimeDisplay = computed(() => {
    if (estimatedWait.value <= 0) return ''
    if (estimatedWait.value < 60) return `${estimatedWait.value}秒`
    if (estimatedWait.value < 3600) return `${Math.round(estimatedWait.value / 60)}分钟`
    return `${Math.round(estimatedWait.value / 3600)}小时`
  })

  const initSession = async () => {
    let sid = sessionStorage.getItem('chat_session_id')
    let sessionToken = sessionStorage.getItem('chat_session_token')
    if (!sid || !sessionToken) {
      const response = await http.post('/api/public/chat/session')
      sid = response.data.data.sessionId
      sessionToken = response.data.data.sessionToken
      sessionStorage.setItem('chat_session_id', sid)
      sessionStorage.setItem('chat_session_token', sessionToken)
    }
    sessionId.value = sid
    return sid
  }

  const renderMarkdown = (content) => {
    if (!content) return ''
    let processed = content.replace(
      /```(?:json|JSON)?\s*\{[\s\S]*?"action"\s*:\s*"create_work_order"[\s\S]*?\}\s*```/g,
      ''
    )
    processed = processed.replace(/<think>[\s\S]*?<\/think>/g, '')
    return String(DOMPurify.sanitize(marked.parse(processed)))
  }

  const createOnCloseHandler = (reconnectAttempts, doConnect) => {
    return () => {
      wsClient.value = null
      if (awaitingResponse.value && currentAiMessage.value) {
        currentAiMessage.value.content += '\n[连接已断开]'
        awaitingResponse.value = false
        loading.value = false
        currentAiMessage.value = null
      }
      if (reconnectAttempts.count < MAX_RECONNECT) {
        reconnectAttempts.count++
        reconnectTimer = setTimeout(doConnect, RECONNECT_BASE_MS * Math.pow(2, reconnectAttempts.count - 1))
      }
    }
  }

  const handleInitialConnect = (client, reconnectAttempts, resolve, reject) => {
    client.connect().then(() => {
      wsClient.value = client
      reconnectAttempts.count = 0
      if (sessionId.value) {
        client.send({ action: 'register_session', sessionId: sessionId.value })
      }
      resolve()
    }).catch((err) => {
      if (reconnectAttempts.count === 0) {
        reject(err)
      }
    })
  }

  const connectWebSocket = () => {
    return new Promise((resolve, reject) => {
      if (wsClient.value?.isOpen()) {
        resolve()
        return
      }

      const reconnectAttempts = { count: 0 }

      const doConnect = () => {
        const token = sessionStorage.getItem('token')
        const sessionToken = sessionStorage.getItem('chat_session_token') || ''
        const params = new URLSearchParams({ session_id: sessionId.value, chat_token: sessionToken })
        if (token) params.set('access_token', token)
        const url = `${WS_URL}?${params.toString()}`
        const client = createWebSocketClient(url)

        client.onMessage(handleWsDispatch)

        client.onClose(createOnCloseHandler(reconnectAttempts, doConnect))

        handleInitialConnect(client, reconnectAttempts, resolve, reject)
      }

      doConnect()
    })
  }

  const restoreSessionStatus = async (isGuest = false) => {
    const sid = sessionId.value
    if (!sid) return
    const basePath = isGuest ? '/api/public/chat' : '/api/chat'
    try {
      const headers = { 'X-Chat-Session-Token': sessionStorage.getItem('chat_session_token') || '' }
      const res = await http.get(`${basePath}/session/${sid}/status`, { headers })
      if (res.data.code === 200 && res.data.data) {
        const statusData = res.data.data
        humanSessionActive.value = Boolean(statusData.humanSessionActive)
        if (statusData.isWaiting) {
          transferring.value = true
          waitPosition.value = statusData.waitPosition || 0
          estimatedWait.value = statusData.estimatedWait || 0
        }
      }
    } catch (err) {
      if (!isGuest) {
        console.error('Failed to restore session status:', err)
      }
    }
  }

  const handleChunk = (msg) => {
    if (currentAiMessage.value) {
      currentAiMessage.value.content += msg.content || ''
    }
  }

  const handleDone = () => {
    awaitingResponse.value = false
    loading.value = false
    currentAiMessage.value = null
  }

  const handleError = (msg) => {
    if (currentAiMessage.value && !currentAiMessage.value.content) {
      currentAiMessage.value.content = `请求失败：${msg.content || '未知错误'}`
    } else if (currentAiMessage.value) {
      currentAiMessage.value.content += `\n[请求失败：${msg.content || '未知错误'}]`
    }
    awaitingResponse.value = false
    loading.value = false
    currentAiMessage.value = null
  }

  const handleWaiting = (msg) => {
    messages.value.push({
      content: msg.content || '您已进入排队，客服将尽快接入...',
      isUser: false,
      isSystem: true,
      systemType: 'info'
    })
    if (msg.position) waitPosition.value = msg.position
    if (msg.estimatedWait) estimatedWait.value = msg.estimatedWait
    transferring.value = true
    awaitingResponse.value = false
    loading.value = false
    currentAiMessage.value = null
  }

  const handleAgentJoined = (msg) => {
    const agentLabel = msg.agentId ? `客服 #${msg.agentId}` : '人工客服'        
    currentAgentId.value = msg.agentId || null
    messages.value.push({
      content: `已为您接入${agentLabel}，正在为您服务，AI 已暂时退出`,
      isUser: false,
      isSystem: true,
      systemType: 'session_status'
    })
    waitPosition.value = 0
    estimatedWait.value = 0
    transferring.value = false
    humanSessionActive.value = true
  }

  const handleAgentMsg = (msg) => {
    messages.value.push({ content: msg.content, isUser: false })
  }

  const handleUserMsgSent = () => {
    loading.value = false
  }

  const handleBackToAi = (msg) => {
    messages.value.push({
      content: msg.content || '人工服务已结束，已为您转接AI',
      isUser: false,
      isSystem: true,
      systemType: 'session_status'
    })
    transferring.value = false
    humanSessionActive.value = false
    waitPosition.value = 0
    estimatedWait.value = 0
  }

  const handleCancelledWaiting = (msg) => {
    messages.value.push({
      content: msg.content || '已取消排队，返回AI服务',
      isUser: false,
      isSystem: true,
      systemType: 'info'
    })
    transferring.value = false
    waitPosition.value = 0
    estimatedWait.value = 0
  }

  const handleSessionClosed = (msg) => {
    messages.value.push({
      content: msg.content || '当前服务已结束',
      isUser: false,
      isSystem: true,
      systemType: 'session_status'
    })
    transferring.value = false
    humanSessionActive.value = false
    waitPosition.value = 0
    estimatedWait.value = 0
  }

  const handleAgentTransferred = (msg) => {
    messages.value.push({
      content: msg.content || '已为您转接其他客服',
      isUser: false,
      isSystem: true,
      systemType: 'info'
    })
  }

  const handleAgentOffline = (msg) => {
    messages.value.push({
      content: msg.content || '当前客服已离线，已为您切换回AI服务',
      isUser: false,
      isSystem: true,
      systemType: 'info'
    })
    transferring.value = false
    humanSessionActive.value = false
    waitPosition.value = 0
    estimatedWait.value = 0
  }

  const handleSatisfactionRequired = (msg) => {
    messages.value.push({
      content: msg.content || '请对本次服务进行评价',
      isUser: false,
      isSystem: true,
      systemType: 'info'
    })
    showSatisfaction.value = true
  }

  const handleBlocked = (msg) => {
    messages.value.push({
      content: msg.content || '您正在与客服沟通中，AI 已暂停响应',
      isUser: false,
      isSystem: true,
      systemType: 'info'
    })
    humanSessionActive.value = true
  }

  const handleWorkorderReply = (msg) => {
    try {
      const woStore = useWorkOrderStore()
      const target = woStore.workOrders.find(w => w.id === msg.workOrderId)
      if (target) {
        target.result = msg.result
      }
      woStore.hasUnreadWoUpdate = true
    } catch (e) {
      console.error('Failed to handle workorder_reply:', e)
    }
  }

  const handleWorkorderCreated = (msg) => {
    try {
      const woStore = useWorkOrderStore()
      const exists = woStore.workOrders.find(w => w.id === msg.workOrderId)
      if (!exists) {
        woStore.workOrders.push({
          id: msg.workOrderId,
          title: msg.title,
          description: msg.description,
          type: msg.woType,
          status: msg.status,
          createTime: msg.createTime,
          result: null
        })
      }
      woStore.hasUnreadWoUpdate = true
    } catch (e) {
      console.error('Failed to handle workorder_created:', e)
    }
  }

  const wsMessageHandlers = {
    chunk: handleChunk,
    done: handleDone,
    error: handleError,
    waiting: handleWaiting,
    agent_joined: handleAgentJoined,
    agent_msg: handleAgentMsg,
    user_msg_sent: handleUserMsgSent,
    back_to_ai: handleBackToAi,
    blocked: handleBlocked,
    cancelled_waiting: handleCancelledWaiting,
    session_closed: handleSessionClosed,
    session_timeout: handleSessionClosed,
    agent_transferred: handleAgentTransferred,
    agent_offline: handleAgentOffline,
    satisfaction_required: handleSatisfactionRequired,
    workorder_reply: handleWorkorderReply,
    workorder_created: handleWorkorderCreated
  }

  const handleWsDispatch = (msg) => {
    if (!msg?.type) return
    const handler = wsMessageHandlers[msg.type]
    if (handler) {
      handler(msg)
    }
  }

  const sendMessagePayload = async (payload) => {
    let lastErr = null
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        await connectWebSocket()
        if (wsClient.value?.isOpen()) {
          wsClient.value.send(payload)
          return
        }
        lastErr = new Error('WebSocket 未连接')
      } catch (err) {
        lastErr = err
      }
      if (attempt < 2) {
        await new Promise(r => setTimeout(r, 500 * (attempt + 1)))
      }
    }
    throw lastErr || new Error('WebSocket 发送失败')
  }

  const sendMessage = async (content, { roles, userId } = {}) => {
    if (!content.trim()) return
    if (awaitingResponse.value || loading.value) return

    messages.value.push({ content, isUser: true })
    loading.value = true

    const payload = {
      sessionId: sessionId.value,
      content,
      userType: roles || [],
      userId: userId || null
    }

    if (humanSessionActive.value || transferring.value) {
      try {
        await sendMessagePayload(payload)
      } catch (err) {
        console.error('Human session message send failed:', err)
        messages.value.push({
          content: '[系统] 消息发送失败，请刷新页面后重试',
          isUser: false,
          isSystem: true,
          systemType: 'info'
        })
        loading.value = false
      }
      return
    }

    const aiMessage = reactive({ content: '', isUser: false })
    messages.value.push(aiMessage)
    currentAiMessage.value = aiMessage
    awaitingResponse.value = true

    try {
      await sendMessagePayload(payload)
    } catch (err) {
      console.error(err)
      if (aiMessage.content) {
        aiMessage.content += '\n[连接中断]'
      } else {
        aiMessage.content = '网络错误，请稍后重试'
      }
      awaitingResponse.value = false
      loading.value = false
      currentAiMessage.value = null
    }
  }

  const transferToHuman = async ({ roles, userId } = {}) => {
    if (transferring.value || humanSessionActive.value) {
      messages.value.push({
        content: '[系统] 当前已在排队中或已接入客服，请勿重复操作',
        isUser: false,
        isSystem: true,
        systemType: 'info'
      })
      return
    }
    transferring.value = true
    let attempts = 0
    const maxAttempts = 3

    const tryTransfer = async () => {
      attempts++
      try {
        const payload = {
          action: 'transfer_to_human',
          sessionId: sessionId.value,
          userType: roles || [],
          userId: userId || null,
          intent: ''
        }
        await connectWebSocket()
        if (!wsClient.value?.isOpen()) {
          throw new Error('WebSocket 未连接')
        }
        wsClient.value.send(payload)
      } catch (err) {
        console.error('Transfer to human failed:', err)
        if (attempts < maxAttempts) {
          const delay = 1000 * attempts
          setTimeout(tryTransfer, delay)
        } else {
          messages.value.push({
            content: `[系统] 转接失败(${maxAttempts}次)，请稍后重试。`,
            isUser: false,
            isSystem: true,
            systemType: 'info'
          })
          transferring.value = false
        }
      }
    }

    tryTransfer()
  }

  const endHumanSession = async () => {
    if (!humanSessionActive.value) return
    try {
      const payload = {
        action: 'end_human',
        sessionId: sessionId.value
      }
      await connectWebSocket()
      if (wsClient.value?.isOpen()) {
        wsClient.value.send(payload)
      }
      humanSessionActive.value = false
    } catch (err) {
      console.error('End human session failed:', err)
    }
  }

  const cancelWaiting = async () => {
    if (!transferring.value) return
    try {
      const payload = {
        action: 'cancel_waiting',
        sessionId: sessionId.value
      }
      await connectWebSocket()
      if (wsClient.value?.isOpen()) {
        wsClient.value.send(payload)
      }
      transferring.value = false
      waitPosition.value = 0
      estimatedWait.value = 0
    } catch (err) {
      console.error('Cancel waiting failed:', err)
    }
  }

  const loadHistory = async (isGuest = false) => {
    const basePath = isGuest ? '/api/public/chat' : '/api/chat'
    try {
      const headers = { 'X-Chat-Session-Token': sessionStorage.getItem('chat_session_token') || '' }
      const res = await http.get(`${basePath}/session/${sessionId.value}/full-history`, { headers })
      if (res.data.code === 200) {
        const merged = res.data.data || []
        messages.value = merged.map(item => {
          if (item.role === 'agent') {
            return { content: item.content, isUser: false }
          }
          return {
            content: item.content,
            isUser: item.role === 'user'
          }
        })
      } else {
        const historyRes = await getHistory(sessionId.value)
        if (historyRes.data.code === 200) {
          const logs = historyRes.data.data
          messages.value = logs.flatMap(log => [
            { content: log.userInput, isUser: true },
            { content: log.aiResponse, isUser: false }
          ])
        }
      }
    } catch (err) {
      if (!isGuest) {
        console.error('Failed to load history:', err)
      }
      try {
        const res = await getHistory(sessionId.value)
        if (res.data.code === 200) {
          const logs = res.data.data
          messages.value = logs.flatMap(log => [
            { content: log.userInput, isUser: true },
            { content: log.aiResponse, isUser: false }
          ])
        }
      } catch (fallbackErr) {
        if (!isGuest) {
          console.error('Fallback loadHistory also failed:', fallbackErr)
        }
      }
    }
  }

  const submitRating = async (satisfaction, userId, agentId) => {
    const payload = { sessionId: sessionId.value, satisfaction, userId }
    if (agentId !== undefined) payload.agentId = agentId
    const res = await submitSatisfaction(payload)
    return res.data
  }

  const setMessageFeedback = (index, feedback) => {
    const msg = messages.value[index]
    if (msg && !msg.isUser) {
      msg.feedback = feedback
    }
  }

  const disconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (wsClient.value) {
      wsClient.value.disconnect()
      wsClient.value = null
    }
  }

  const sendWorkOrderContact = async (workOrderId, workOrderTitle) => {
    try {
      const payload = {
        action: 'workorder_contact',
        sessionId: sessionId.value,
        workOrderId: workOrderId,
        workOrderTitle: workOrderTitle,
        content: `[工单 #${workOrderId}] ${workOrderTitle}`
      }
      await sendMessagePayload(payload)
    } catch (err) {
      console.error('Failed to send work order contact:', err)
    }
  }

  const clearMessages = () => {
    messages.value = []
  }

  return {
    messages,
    loading,
    awaitingResponse,
    transferring,
    showSatisfaction,
    sessionId,
    waitPosition,
    estimatedWait,
    waitTimeDisplay,
    hasMessages,
    humanSessionActive,
    currentAgentId,
    initSession,
    renderMarkdown,
    connectWebSocket,
    sendMessage,
    sendMessagePayload,
    sendWorkOrderContact,
    transferToHuman,
    endHumanSession,
    cancelWaiting,
    loadHistory,
    restoreSessionStatus,
    submitRating,
    setMessageFeedback,
    disconnect,
    clearMessages
  }
})
