import { computed } from 'vue'
import { useAuthStore } from '@/shared/stores/authStore'

export function useAuth() {
  const authStore = useAuthStore()

  const isLoggedIn = computed(() => authStore.isLoggedIn)
  const roles = computed(() => authStore.roles)
  const userId = computed(() => authStore.userId)

  const isAdmin = computed(() => authStore.isAdmin)
  const isAgent = computed(() => authStore.isAgent)
  const isKBAdmin = computed(() => authStore.isKBAdmin)
  const isCustomerService = computed(() => authStore.isCustomerService)
  const isGuest = computed(() => !authStore.isLoggedIn)

  const hasRole = (roleName) => authStore.hasRole(roleName)

  const hasPermission = (requiredRole) => {
    if (!authStore.isLoggedIn) return false
    if (requiredRole === '*') return true
    if (requiredRole === 'admin') return authStore.isAdmin
    if (requiredRole === 'agent') return authStore.isAgent
    if (requiredRole === 'kb_admin') return authStore.isKBAdmin
    if (requiredRole === 'customer_service') return authStore.isCustomerService
    if (requiredRole === 'authenticated') return authStore.isLoggedIn
    return authStore.hasRole(requiredRole)
  }

  return {
    isLoggedIn,
    roles,
    userId,
    isAdmin,
    isAgent,
    isKBAdmin,
    isCustomerService,
    isGuest,
    hasRole,
    hasPermission,
    login: authStore.login,
    register: authStore.register,
    logout: authStore.logout,
    checkLoginStatus: authStore.checkLoginStatus
  }
}
