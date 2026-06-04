import { ref, onUnmounted } from 'vue'

export function createWebSocketClient(url) {
  let socket = null
  const messageHandlers = new Set()
  const closeHandlers = new Set()

  const processMessage = (event) => {
    let msg = null
    try { msg = JSON.parse(event.data) } catch { return }
    if (!msg?.type) return
    messageHandlers.forEach((handler) => handler(msg))
  }

  const notifyClose = () => {
    socket = null
    closeHandlers.forEach((handler) => handler())
  }

  const connect = () => {
    return new Promise((resolve, reject) => {
      if (socket?.readyState === WebSocket.OPEN) {
        resolve()
        return
      }

      const ws = new WebSocket(url)

      ws.onopen = () => {
        socket = ws
        resolve()
      }

      ws.onmessage = processMessage

      ws.onerror = () => { reject(new Error('WebSocket 连接失败')) }

      ws.onclose = notifyClose
    })
  }

  const send = (payload) => {
    if (socket?.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket 未连接')
    }
    socket.send(JSON.stringify(payload))
  }

  const onMessage = (handler) => {
    messageHandlers.add(handler)
    return () => messageHandlers.delete(handler)
  }

  const onClose = (handler) => {
    closeHandlers.add(handler)
    return () => closeHandlers.delete(handler)
  }

  const disconnect = () => {
    if (socket) { socket.close(); socket = null }
    messageHandlers.clear()
    closeHandlers.clear()
  }

  const isOpen = () => socket?.readyState === WebSocket.OPEN

  return { connect, send, onMessage, onClose, disconnect, isOpen }
}

export function useWebSocket(url) {
  const ws = createWebSocketClient(url)
  const connected = ref(false)

  const connect = async () => {
    await ws.connect()
    connected.value = true
  }

  ws.onMessage(() => { connected.value = ws.isOpen() })

  onUnmounted(() => {
    ws.disconnect()
    connected.value = false
  })

  return {
    ...ws,
    connected,
    connect
  }
}
