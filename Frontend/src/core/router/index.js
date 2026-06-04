import { createRouter, createWebHistory } from 'vue-router'
import { createRbacGuard } from './guards'

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/domains/chat/ChatView.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/domains/auth/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/domains/auth/Register.vue')
  },
  {
    path: '/admin',
    component: () => import('@/layout/AdminLayout.vue'),
    meta: { requiresAuth: true, roles: ['admin'] },
    children: [
      {
        path: 'knowledge-review',
        name: 'KnowledgeReview',
        component: () => import('@/domains/knowledge/KnowledgeReview.vue')
      },
      {
        path: 'knowledge-base',
        name: 'KnowledgeBase',
        component: () => import('@/domains/knowledge/CustomerKnowledge.vue')
      },
      {
        path: 'agent-management',
        name: 'AgentManagement',
        component: () => import('@/domains/agentmgmt/AgentManagement.vue')
      },
      {
        path: 'user-management',
        name: 'UserManagement',
        component: () => import('@/domains/agentmgmt/UserManagement.vue')
      },
      {
        path: 'work-orders',
        name: 'WorkOrders',
        component: () => import('@/domains/workorder/WorkOrder.vue')
      },
      {
        path: 'agent-desk',
        name: 'AgentDesk',
        component: () => import('@/domains/agent/AgentDesk.vue')
      },
      {
        path: 'agent-insight',
        name: 'AgentInsight',
        component: () => import('@/domains/analytics/AgentInsight.vue')
      },
      {
        path: 'sla-settings',
        name: 'SlaSettings',
        component: () => import('@/domains/admin/SlaSettings.vue')
      },
      {
        path: 'data-analysis',
        name: 'DataAnalysis',
        component: () => import('@/domains/analytics/DataAnalysis.vue')
      },
      {
        path: 'kb-insight',
        name: 'KbAdminInsight',
        component: () => import('@/domains/analytics/KbAdminInsight.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

createRbacGuard(router)

export default router
