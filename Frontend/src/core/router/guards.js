import { useAuthStore } from '@/shared/stores/authStore'

const PERMISSION_MATRIX = {
  '/chat':                       ['*'],
  '/login':                      ['*'],
  '/register':                   ['*'],
  '/admin/knowledge-review':     ['KB_ADMIN'],
  '/admin/knowledge-base':       ['KB_ADMIN', 'AGENT'],
  '/admin/agent-management':     ['ADMIN'],
  '/admin/user-management':      ['ADMIN'],
  '/admin/agent-desk':           ['AGENT'],
  '/admin/work-orders':          ['AGENT'],
  '/admin/agent-insight':        ['AGENT', 'ADMIN'],
  '/admin/data-analysis':        ['ADMIN', 'KB_ADMIN'],
  '/admin/kb-insight':           ['KB_ADMIN', 'ADMIN'],
  '/admin':                      ['ADMIN', 'KB_ADMIN', 'AGENT']
}

function matchAllowedPath(path) {
  for (const [prefix, allowedRoles] of Object.entries(PERMISSION_MATRIX)) {
    if (path.startsWith(prefix)) {
      return allowedRoles
    }
  }
  return null
}

function hasAccess(allowedRoles, roles) {
  if (allowedRoles.includes('*')) return true
  return allowedRoles.some(r => roles.includes(r))
}

export function createRbacGuard(router) {
  router.beforeEach((to, from, next) => {
    const authStore = useAuthStore()
    authStore.checkLoginStatus()

    const reqGuest = import.meta.env.DEV && import.meta.env.VITE_ALLOW_GUEST_ADMIN === 'true'

    if (to.path.startsWith('/admin') && !authStore.isLoggedIn) {
      if (reqGuest) {
        sessionStorage.setItem('token', 'mock-agent-token')
        sessionStorage.setItem('roles', JSON.stringify(['AGENT']))
        sessionStorage.setItem('userId', '4')
        sessionStorage.setItem('username', '客服')
        authStore.checkLoginStatus()
      } else {
        next('/login')
        return
      }
    }

    const roles = authStore.roles
    const allowedRoles = matchAllowedPath(to.path)

    if (allowedRoles === null) {
      next('/chat')
      return
    }

    if (!hasAccess(allowedRoles, roles)) {
      if (to.meta.requiresAuth && !authStore.isLoggedIn) {
        next('/login')
        return
      }
      if (roles.includes('ADMIN')) {
        next('/admin/agent-management')
        return
      }
      if (roles.includes('KB_ADMIN')) {
        next('/admin/knowledge-review')
        return
      }
      if (roles.includes('AGENT')) {
        next('/admin/agent-desk')
        return
      }
      next('/chat')
      return
    }

    next()
  })

  return router
}

export { PERMISSION_MATRIX }
