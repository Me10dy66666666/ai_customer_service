<template>
  <div class="app-layout">
    <!-- Sidebar -->
    <div class="sidebar" :class="{ 'open': isSidebarOpen }">
      <div class="sidebar-header">
        <div class="menu-btn" @click="toggleSidebar">
          <span></span><span></span><span></span>
        </div>
      </div>
      <div class="sidebar-menu">
        <div 
          class="menu-item" 
          :class="{ active: currentView === 'chat' }"
          @click="switchView('chat')"
        >
          <span class="icon">💬</span> AI 客服
        </div>
        <div 
          class="menu-item" 
          :class="{ active: currentView === 'orders' }"
          @click="switchView('orders')"
        >
          <span class="icon">📦</span> 历史订单
        </div>
        <div 
          class="menu-item" 
          :class="{ active: currentView === 'work-orders' }"
          @click="switchView('work-orders')"
        >
          <span class="icon">📋</span> 我的工单
        </div>
      </div>
    </div>
    
    <!-- Overlay for mobile/when sidebar is open -->
    <div class="sidebar-overlay" v-if="isSidebarOpen" @click="toggleSidebar"></div>

    <!-- Main Content -->
    <div class="main-content">
      <!-- Header -->
      <header class="chat-header">
        <div class="header-left">
          <div class="hamburger-btn" @click="toggleSidebar">
            <span></span><span></span><span></span>
          </div>
          <div class="logo">AI 智能客服</div>
        </div>
        <div class="auth-actions">
          <template v-if="!isLoggedIn">
            <router-link to="/login" class="auth-btn">登录</router-link>
          </template>
          <template v-else>
            <span class="user-info">欢迎, {{ username }}</span>
            <button @click="logout" class="auth-btn logout">退出</button>
          </template>
        </div>
      </header>

      <!-- Chat View -->
      <div v-if="currentView === 'chat'" class="view-container chat-view">
        <div class="chat-messages" ref="messagesContainer">
          <div 
            v-for="(msg, index) in messages" 
            :key="index" 
            class="message-wrapper"
            :class="{ 'user-message': msg.isUser, 'ai-message': !msg.isUser }"
          >
            <div class="message-content">
              <div class="avatar">{{ msg.isUser ? '我' : 'AI' }}</div>
              <div class="bubble" v-if="msg.isUser">{{ msg.content }}</div>
              <div class="bubble" v-else v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>
          <div v-if="loading" class="message-wrapper ai-message">
            <div class="message-content">
              <div class="avatar">AI</div>
              <div class="bubble loading">...</div>
            </div>
          </div>
        </div>

        <div class="input-area">
          <input 
            v-model="newMessage" 
            @keyup.enter="handleSend"
            placeholder="请输入您的问题..." 
            :disabled="loading"
          />
          <button @click="handleSend" :disabled="loading || !newMessage.trim()">发送</button>
        </div>
      </div>

      <!-- Orders View -->
      <div v-if="currentView === 'orders'" class="view-container orders-view">
        <div class="orders-header">
          <h3>历史订单</h3>
          <button @click="handleSyncOrders" class="sync-btn" :disabled="syncing">
            {{ syncing ? '同步中...' : '同步订单' }}
          </button>
        </div>
        
        <div v-if="loadingOrders" class="loading-state">加载中...</div>
        <div v-else-if="orders.length === 0" class="empty-state">暂无订单数据</div>
        <div v-else class="order-list">
          <div v-for="order in orders" :key="order.id" class="order-card">
            <div class="order-header">
              <span class="order-no">{{ order.orderNo }}</span>
              <span class="order-status" :class="order.orderStatus === '已完成' ? 'status-done' : ''">
                {{ order.orderStatus }}
              </span>
            </div>
            <div class="order-body">
              <div class="product-info">
                <h4>{{ order.productName }}</h4>
                <p class="model">{{ order.productModel }}</p>
              </div>
              <div class="order-meta">
                <span class="price">¥{{ order.totalAmount }}</span>
                <span class="date">{{ new Date(order.createTime).toLocaleDateString() }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Work Orders View -->
      <div v-if="currentView === 'work-orders'" class="view-container orders-view">
        <div class="orders-header">
          <h3>我的工单</h3>
          <button @click="openWorkOrderDialog" class="sync-btn">提交工单</button>
        </div>
        
        <div v-if="loadingWorkOrders" class="loading-state">加载中...</div>
        <div v-else-if="workOrders.length === 0" class="empty-state">暂无工单数据</div>
        <div v-else class="order-list">
          <div v-for="wo in workOrders" :key="wo.id" class="order-card">
            <div class="order-header">
              <span class="order-no">#{{ wo.id }}</span>
              <span class="order-status" :class="getWorkOrderStatusClass(wo.status)">
                {{ getWorkOrderStatusLabel(wo.status) }}
              </span>
            </div>
            <div class="order-body">
              <div class="product-info">
                <h4>{{ wo.title }}</h4>
                <p class="model">{{ wo.description }}</p>
              </div>
              <div class="order-meta">
                <span class="type-tag">{{ wo.type }}</span>
                <span class="date">{{ new Date(wo.createTime).toLocaleDateString() }}</span>
              </div>
              <div v-if="wo.result" class="wo-result">
                <strong>处理结果:</strong> {{ wo.result }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 提交工单弹窗 (简易版) -->
      <div v-if="showWorkOrderDialog" class="modal-overlay">
        <div class="modal-content">
          <h3>提交新工单</h3>
          <div class="form-group">
            <label>标题</label>
            <input v-model="newWorkOrder.title" placeholder="请输入标题" />
          </div>
          <div class="form-group">
            <label>类型</label>
            <select v-model="newWorkOrder.type">
              <option value="售前">售前咨询</option>
              <option value="售后">售后服务</option>
            </select>
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="newWorkOrder.description" rows="4" placeholder="请详细描述问题"></textarea>
          </div>
          <div class="modal-actions">
            <button @click="showWorkOrderDialog = false" class="cancel-btn">取消</button>
            <button @click="submitWorkOrder" class="submit-btn" :disabled="submittingWO">提交</button>
          </div>
        </div>
      </div>
      
      <!-- Guest Register Prompt Modal -->
      <div v-if="showRegisterPrompt" class="modal-overlay">
        <div class="modal-content prompt-modal">
          <h3>温馨提示</h3>
          <p>您需要注册或登录后才能查看历史订单和我的工单。</p>
          <div class="modal-actions">
            <button @click="showRegisterPrompt = false" class="cancel-btn">暂不</button>
            <router-link to="/login" class="submit-btn" style="text-decoration: none; padding: 10px 20px; display: inline-block;">去登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked' // 引入 marked 库
import { getHistory } from '../api/chat'
import { syncOrders, getUserOrders } from '../api/orders'
import { createWorkOrder, getWorkOrders } from '../api/workOrder'

const WS_URL = 'ws://localhost:8081/ws/chat'

const router = useRouter()
const messages = ref([])
const newMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
const wsClient = ref(null)
const currentAiMessage = ref(null)
const awaitingResponse = ref(false)

// Markdown 渲染函数
const renderMarkdown = (content) => {
  if (!content) return ''
  
  // 1. 隐藏 JSON 数据块 (匹配 ```json ... ``` 且包含 action: create_work_order)
  // 使用非贪婪匹配，尝试移除特定的工单 JSON
  let processedContent = content.replace(/```(?:json|JSON)?\s*\{[\s\S]*?"action"\s*:\s*"create_work_order"[\s\S]*?\}\s*```/g, '')

  // 2. 处理思考过程标签 <think>...</think>
  // 将其转换为 <details> 标签
  processedContent = processedContent.replace(
    /<think>([\s\S]*?)<\/think>/g, 
    '<details class="thought-process"><summary>思考过程</summary><div class="thought-content">$1</div></details>'
  )

  // marked.parse 返回的是 HTML 字符串
  return marked.parse(processedContent)
}

const isLoggedIn = ref(false)
const username = ref('')

// Sidebar & View State
const isSidebarOpen = ref(false)
const currentView = ref('chat') // 'chat' or 'orders' or 'work-orders'
const orders = ref([])
const loadingOrders = ref(false)
const syncing = ref(false)

// Work Order State
const workOrders = ref([])
const loadingWorkOrders = ref(false)
const showWorkOrderDialog = ref(false)
const showRegisterPrompt = ref(false)
const submittingWO = ref(false)
const newWorkOrder = ref({
  title: '',
  type: '售后',
  description: ''
})

const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
}

const switchView = async (view) => {
  if (view !== 'chat' && !isLoggedIn.value) {
    // Show register prompt for guest users
    showRegisterPrompt.value = true
    return
  }
  
  currentView.value = view
  isSidebarOpen.value = false // Close sidebar on mobile after selection
  
   if (view === 'orders' && isLoggedIn.value) {
    await fetchOrders()
  } else if (view === 'work-orders' && isLoggedIn.value) {
    await fetchWorkOrders()
  }
}

// Initialize Session Logic
const initSession = () => {
  let sessionId = sessionStorage.getItem('chat_session_id')
  if (!sessionId) {
    sessionId = 'guest-' + Math.random().toString(36).substr(2, 9) + '-' + Date.now()
    sessionStorage.setItem('chat_session_id', sessionId)
  }
  return sessionId
}

const sessionId = initSession()

// Check Login Status
const checkLoginStatus = () => {
  const token = sessionStorage.getItem('token')
  if (token) {
    isLoggedIn.value = true
    username.value = '用户' 
  } else {
    isLoggedIn.value = false
    username.value = ''
  }
}

const logout = () => {
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('userType')
  sessionStorage.removeItem('userId')
  isLoggedIn.value = false
  currentView.value = 'chat'
  router.push('/login')
}

// Load History
const loadHistory = async () => {
  try {
    const res = await getHistory(sessionId)
    if (res.data.code === 200) {
      const logs = res.data.data
      messages.value = logs.flatMap(log => [
        { content: log.userInput, isUser: true },
        { content: log.aiResponse, isUser: false }
      ])
      scrollToBottom()
    }
  } catch (err) {
    console.error('Failed to load history:', err)
  }
}

const handleWsMessage = (raw) => {
  let msg = null
  try {
    msg = JSON.parse(raw)
  } catch {
    return
  }

  if (!msg || !msg.type) return

  if (msg.type === 'chunk') {
    if (currentAiMessage.value) {
      currentAiMessage.value.content += msg.content || ''
      scrollToBottom()
    }
    return
  }

  if (msg.type === 'done') {
    awaitingResponse.value = false
    loading.value = false
    currentAiMessage.value = null
    scrollToBottom()
    return
  }

  if (msg.type === 'error') {
    if (currentAiMessage.value && !currentAiMessage.value.content) {
      currentAiMessage.value.content = `请求失败：${msg.content || '未知错误'}`
    } else if (currentAiMessage.value) {
      currentAiMessage.value.content += `\n[请求失败：${msg.content || '未知错误'}]`
    }
    awaitingResponse.value = false
    loading.value = false
    currentAiMessage.value = null
    scrollToBottom()
  }
}

const connectWebSocket = async () => {
  if (wsClient.value && wsClient.value.readyState === WebSocket.OPEN) {
    return
  }

  await new Promise((resolve, reject) => {
    const socket = new WebSocket(WS_URL)

    socket.onopen = () => {
      wsClient.value = socket
      resolve()
    }

    socket.onmessage = (event) => {
      handleWsMessage(event.data)
    }

    socket.onerror = () => {
      reject(new Error('WebSocket 连接失败'))
    }

    socket.onclose = () => {
      wsClient.value = null
      if (awaitingResponse.value && currentAiMessage.value) {
        currentAiMessage.value.content += '\n[连接已断开]'
        awaitingResponse.value = false
        loading.value = false
        currentAiMessage.value = null
      }
    }
  })
}

const handleSend = async () => {
  const content = newMessage.value.trim()
  if (!content) return

  // Add user message immediately
  messages.value.push({ content, isUser: true })
  newMessage.value = ''
  scrollToBottom()
  loading.value = true

  const aiMessage = reactive({ content: '', isUser: false })
  messages.value.push(aiMessage)
  currentAiMessage.value = aiMessage
  awaitingResponse.value = true

  try {
    const userType = sessionStorage.getItem('userType') ? parseInt(sessionStorage.getItem('userType')) : 0
    const userId = sessionStorage.getItem('userId') ? parseInt(sessionStorage.getItem('userId')) : null
                        
    const payload = {
      sessionId,
      content,
      userType,
      userId
    }
    await connectWebSocket()
    if (!wsClient.value || wsClient.value.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket 未连接')
    }
    wsClient.value.send(JSON.stringify(payload))

  } catch (err) {
    console.error(err)
    if (!aiMessage.content) {
      aiMessage.content = '网络错误，请稍后重试'
    } else {
      aiMessage.content += '\n[连接中断]'
    }
    awaitingResponse.value = false
    loading.value = false
    currentAiMessage.value = null
    scrollToBottom()
  } finally {
    if (!awaitingResponse.value) {
      loading.value = false
      currentAiMessage.value = null
      scrollToBottom()
    }
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// Orders Logic
const fetchOrders = async () => {
  const userId = sessionStorage.getItem('userId')
  if (!userId) return
  
  loadingOrders.value = true
  try {
    const res = await getUserOrders(userId)
    if (res.data.code === 200) {
      orders.value = res.data.data
    }
  } catch (err) {
    console.error(err)
  } finally {
    loadingOrders.value = false
  }
}

const handleSyncOrders = async () => {
  const userId = sessionStorage.getItem('userId')
  if (!userId) return
  
  syncing.value = true
  try {
    const res = await syncOrders(userId)
    if (res.data.code === 200) {
      orders.value = res.data.data
    } else {
      alert('同步失败: ' + res.data.message)
    }
  } catch (err) {
    console.error(err)
    alert('同步失败')
  } finally {
    syncing.value = false
  }
}

// Work Orders Logic
const fetchWorkOrders = async () => {
  const userId = sessionStorage.getItem('userId')
  if (!userId) return

  loadingWorkOrders.value = true
  try {
    const res = await getWorkOrders(userId)
    if (res.data.code === 200) {
      workOrders.value = res.data.data
    }
  } catch (err) {
    console.error(err)
  } finally {
    loadingWorkOrders.value = false
  }
}

const openWorkOrderDialog = () => {
  newWorkOrder.value = { title: '', type: '售后', description: '' }
  showWorkOrderDialog.value = true
}

const submitWorkOrder = async () => {
  const userId = sessionStorage.getItem('userId')
  if (!userId) {
    alert('请先登录')
    return
  }
  if (!newWorkOrder.value.title || !newWorkOrder.value.description) {
    alert('请填写完整信息')
    return
  }

  submittingWO.value = true
  try {
    const payload = {
      userId: parseInt(userId),
      ...newWorkOrder.value
    }
    const res = await createWorkOrder(payload)
    if (res.data.code === 200) {
      alert('工单提交成功')
      showWorkOrderDialog.value = false
      fetchWorkOrders()
    } else {
      alert('提交失败: ' + res.data.msg)
    }
  } catch (err) {
    console.error(err)
    alert('提交失败')
  } finally {
    submittingWO.value = false
  }
}

const getWorkOrderStatusClass = (status) => {
  const map = { pending: '', processing: 'status-processing', completed: 'status-done', cancelled: 'status-cancelled' }
  return map[status] || ''
}

const getWorkOrderStatusLabel = (status) => {
  const map = { pending: '待处理', processing: '处理中', completed: '已完成', cancelled: '已取消' }
  return map[status] || status
}

onMounted(() => {
  checkLoginStatus()
  loadHistory()
  connectWebSocket().catch(() => {})
})

onBeforeUnmount(() => {
  if (wsClient.value) {
    wsClient.value.close()
    wsClient.value = null
  }
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100%;
  width: 100%;
  min-height: 0;
  overflow: hidden;
  background-color: #f5f7fa;
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
}

/* Sidebar Styles */
.sidebar {
  position: fixed;
  left: -280px;
  top: 0;
  bottom: 0;
  width: 280px;
  background-color: #fff;
  box-shadow: 2px 0 8px rgba(0,0,0,0.1);
  z-index: 100;
  transition: left 0.3s ease;
  display: flex;
  flex-direction: column;
}

.sidebar.open {
  left: 0;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f0;
}

.sidebar-menu {
  flex: 1;
  padding: 20px 0;
}

.menu-item {
  padding: 12px 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #333;
  transition: background 0.2s;
  font-size: 1rem;
}

.menu-item:hover {
  background-color: #f5f7fa;
}

.menu-item.active {
  background-color: #e6f7ff;
  color: #1890ff;
  font-weight: 500;
}

.menu-item .icon {
  font-size: 1.2rem;
}

.sidebar-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0,0,0,0.3);
  z-index: 90;
}

/* Main Content Styles */
.main-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 60px;
  background-color: #fff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  z-index: 10;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
}

.hamburger-btn, .menu-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  margin-right: 15px;
  transition: background-color 0.2s;
}

.hamburger-btn:hover, .menu-btn:hover {
  background-color: #f0f0f0;
}

.hamburger-btn span, .menu-btn span {
  display: block;
  width: 18px;
  height: 2px;
  background-color: #333;
  margin: 2px 0;
  border-radius: 2px;
}

.logo {
  font-size: 1.2rem;
  font-weight: bold;
  color: #333;
}

.auth-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.auth-btn {
  text-decoration: none;
  color: #007bff;
  font-size: 0.9rem;
  cursor: pointer;
}

.auth-btn.logout {
  background: none;
  border: none;
  padding: 0;
}

.user-info {
  font-size: 0.9rem;
  color: #666;
}

/* View Container */
.view-container {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Chat View Specifics */
.chat-view .chat-messages {
  flex: 1;
  width: 100%;
  max-width: 920px;
  margin: 0 auto;
  padding: 16px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-wrapper {
  display: flex;
  width: 100%;
}

.user-message {
  justify-content: flex-end;
}

.ai-message {
  justify-content: flex-start;
}

.message-content {
  display: flex;
  max-width: min(860px, 88%);
  align-items: flex-start;
  gap: 8px;
}

.user-message .message-content {
  flex-direction: row-reverse;
}

.avatar {
  width: 30px;
  height: 30px;
  background-color: #d8dee8;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.72rem;
  color: #3f4c63;
  flex-shrink: 0;
}

.ai-message .avatar {
  background-color: #8ba4ff;
  color: white;
}

.bubble {
  padding: 8px 12px;
  border-radius: 14px;
  font-size: 0.92rem;
  line-height: 1.55;
  word-wrap: break-word;
}

.user-message .bubble {
  background-color: #e9f0ff;
  color: #273142;
  border-top-right-radius: 6px;
}

.ai-message .bubble {
  background-color: #ffffff;
  color: #30384a;
  border: 1px solid #e6ebf4;
  border-top-left-radius: 6px;
}

.loading {
  letter-spacing: 2px;
}

.input-area {
  width: calc(100% - 32px);
  max-width: 920px;
  margin: 0 auto 14px;
  padding: 10px;
  background-color: #fff;
  border: 1px solid #e8edf7;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(31, 56, 114, 0.08);
  display: flex;
  gap: 8px;
}

input {
  flex: 1;
  padding: 11px 12px;
  border: 1px solid #dde4f1;
  border-radius: 12px;
  font-size: 0.95rem;
  outline: none;
}

input:focus {
  border-color: #007bff;
}

button {
  padding: 0 18px;
  background-color: #5f7cff;
  color: white;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-size: 0.92rem;
}

button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

/* Orders View Specifics */
.orders-view {
  padding: 20px;
  overflow-y: auto;
  background-color: #f5f7fa;
}

.orders-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.sync-btn {
  background-color: #1890ff;
  font-size: 0.9rem;
  padding: 8px 16px;
}

.order-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.order-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  transition: transform 0.2s;
}

.order-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.order-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 0.9rem;
  color: #888;
}

.status-done {
  color: #52c41a;
  font-weight: 500;
}

.product-info h4 {
  margin: 0 0 4px 0;
  color: #333;
  font-size: 1.1rem;
}

.product-info .model {
  color: #666;
  font-size: 0.9rem;
  margin: 0 0 12px 0;
}

.order-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.order-meta .price {
  color: #f5222d;
  font-weight: bold;
  font-size: 1.1rem;
}

.order-meta .date {
  color: #999;
  font-size: 0.85rem;
}

/* Markdown Styles */
  .bubble :deep(p) { margin: 0 0 8px 0; }
  .bubble :deep(p:last-child) { margin-bottom: 0; }
  .bubble :deep(ul), .bubble :deep(ol) { margin: 0 0 8px 20px; padding: 0; }
  .bubble :deep(li) { margin-bottom: 4px; }
  .bubble :deep(code) { background-color: rgba(0,0,0,0.05); padding: 2px 4px; border-radius: 4px; font-family: monospace; }
  .bubble :deep(pre) { background-color: #f6f8fa; padding: 10px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }
  .bubble :deep(pre code) { background-color: transparent; padding: 0; }
  .bubble :deep(a) { color: #1890ff; text-decoration: none; }
  .bubble :deep(a:hover) { text-decoration: underline; }
  .bubble :deep(blockquote) { border-left: 3px solid #dfe2e5; color: #6a737d; padding-left: 10px; margin: 8px 0; }
  
  /* Thought Process Styles */
  .bubble :deep(details.thought-process) {
    background-color: #f6f8fa;
    border: 1px solid #e1e4e8;
    border-radius: 6px;
    margin-bottom: 12px;
    overflow: hidden;
  }

  .bubble :deep(details.thought-process > summary) {
    padding: 8px 12px;
    cursor: pointer;
    background-color: #f0f3f6;
    font-size: 0.9rem;
    font-weight: 500;
    color: #586069;
    user-select: none;
    outline: none;
    list-style: none; /* Hide default triangle in some browsers */
  }
  
  /* Custom marker */
  .bubble :deep(details.thought-process > summary)::before {
    content: '💡';
    margin-right: 6px;
  }

  .bubble :deep(details.thought-process[open] > summary) {
    border-bottom: 1px solid #e1e4e8;
  }

  .bubble :deep(.thought-content) {
    padding: 12px;
    font-size: 0.9rem;
    color: #444;
    white-space: pre-wrap; /* Preserve formatting */
    line-height: 1.5;
  }

  .loading-state, .empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

/* Work Order Specifics */
.type-tag {
  background-color: #e6f7ff;
  color: #1890ff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.8rem;
}

.wo-result {
  margin-top: 10px;
  padding: 8px;
  background-color: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
  font-size: 0.9rem;
  color: #52c41a;
}

.status-processing {
  color: #1890ff;
}
.status-cancelled {
  color: #999;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  padding: 24px;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.form-group input, .form-group select, .form-group textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.cancel-btn {
  background: white;
  color: #666;
  border: 1px solid #ddd;
}

.submit-btn {
  background: #1890ff;
  color: white;
}
</style>
