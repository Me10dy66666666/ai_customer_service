import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register } from '@/domains/auth/authService'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem('token') || null)
  const roles = ref(JSON.parse(sessionStorage.getItem('roles') || '[]'))
  const userIdValue = ref(sessionStorage.getItem('userId') ? Number.parseInt(sessionStorage.getItem('userId')) : null)
  const username = ref(sessionStorage.getItem('username') || '')

  const isLoggedIn = computed(() => !!token.value)
  const userId = computed(() => userIdValue.value)
  const isAdmin = computed(() => roles.value.includes('ADMIN'))
  const isAgent = computed(() => roles.value.includes('AGENT'))
  const isKBAdmin = computed(() => roles.value.includes('KB_ADMIN'))
  const isCustomerService = computed(() => roles.value.includes('USER') || roles.value.includes('VIP'))

  const hasRole = (roleName) => roles.value.includes(roleName)
  const hasAnyRole = (...roleNames) => roleNames.some(r => roles.value.includes(r))

  const checkLoginStatus = () => {
    const storedToken = sessionStorage.getItem('token')
    if (storedToken) {
      token.value = storedToken
      roles.value = JSON.parse(sessionStorage.getItem('roles') || '[]')
      userIdValue.value = sessionStorage.getItem('userId') ? Number.parseInt(sessionStorage.getItem('userId')) : null
      username.value = sessionStorage.getItem('username') || ''
    }
  }

  const doLogin = async (credentials) => {
    const sessionId = sessionStorage.getItem('chat_session_id')
    const loginData = {
      username: credentials.username,
      password: credentials.password,
      sessionId
    }

    const response = await login(loginData)
    if (response.data.code === 200) {
      if (sessionId) {
        sessionStorage.removeItem('chat_session_id')
        sessionStorage.removeItem('chat_session_token')
      }
      const data = response.data.data
      sessionStorage.setItem('token', data.token)
      sessionStorage.setItem('roles', JSON.stringify(data.roles || []))
      sessionStorage.setItem('userId', data.userId)
      sessionStorage.setItem('username', credentials.username)
      token.value = data.token
      roles.value = data.roles || []
      userIdValue.value = data.userId
      username.value = credentials.username
      return { success: true, roles: data.roles || [] }
    }
    return { success: false, message: response.data.message }
  }

  const doRegister = async (registerData) => {
    const sessionId = sessionStorage.getItem('chat_session_id')
    const payload = { ...registerData, sessionId }

    const response = await register(payload)
    if (response.data.code === 200) {
      if (sessionId) {
        sessionStorage.removeItem('chat_session_id')
        sessionStorage.removeItem('chat_session_token')
      }
      return { success: true }
    }
    return { success: false, message: response.data.message }
  }

  const logout = () => {
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('roles')
    sessionStorage.removeItem('userId')
    sessionStorage.removeItem('username')
    token.value = null
    roles.value = []
    userIdValue.value = null
    username.value = ''
  }

  return {
    token,
    roles,
    userId,
    username,
    isLoggedIn,
    isAdmin,
    isAgent,
    isKBAdmin,
    isCustomerService,
    hasRole,
    hasAnyRole,
    checkLoginStatus,
    login: doLogin,
    register: doRegister,
    logout
  }
})
