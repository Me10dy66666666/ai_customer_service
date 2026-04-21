import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import AdminLayout from '../layout/AdminLayout.vue'
import KnowledgeBase from '../views/admin/KnowledgeBase.vue'

import Chat from '../views/Chat.vue'

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: Chat
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'knowledge-base',
        name: 'KnowledgeBase',
        component: KnowledgeBase
      },
      {
        path: 'work-orders',
        name: 'WorkOrders',
        component: () => import('../views/admin/WorkOrder.vue')
      },
      {
        path: 'data-analysis',
        name: 'DataAnalysis',
        component: () => import('../views/admin/DataAnalysis.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation Guard
router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('token')
  const userType = parseInt(sessionStorage.getItem('userType'))

  // 管理员账号：只允许访问 /admin/**，避免进入用户侧聊天/会员页面
  if (token && userType === 3 && !to.path.startsWith('/admin')) {
    next('/admin/knowledge-base')
    return
  }

  if (to.meta.requiresAdmin) {
    if (!token) {
      next('/login')
    } else if (userType !== 3) { // Assuming 3 is Admin userType
      alert('无权访问：仅管理员可进入')
      next(false) // Abort navigation or redirect to user home
      // next('/') 
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
