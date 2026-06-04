<template>
  <div class="admin-shell">
    <aside class="admin-rail">
      <div class="rail-brand">Serene</div>
      <nav class="rail-nav">
        <template v-if="isAdmin">
          <div class="rail-section">管理</div>
          <router-link to="/admin/agent-management" class="rail-link" active-class="rail-on">
            <span>● 员工管理</span>
          </router-link>
          <router-link to="/admin/user-management" class="rail-link" active-class="rail-on">
            <span>● 用户管理</span>
          </router-link>
          <router-link to="/admin/sla-settings" class="rail-link" active-class="rail-on">
            <span>◉ 超时设置</span>
          </router-link>
          <div class="rail-divider"></div>
          <div class="rail-section">分析</div>
          <router-link to="/admin/data-analysis" class="rail-link" active-class="rail-on">
            <span>◆ 洞察</span>
          </router-link>
        </template>
        <template v-else-if="isKBAdmin">
          <div class="rail-section">知识管理</div>
          <router-link to="/admin/knowledge-review" class="rail-link" active-class="rail-on">
            <span>◈ 知识审核</span>
          </router-link>
          <router-link to="/admin/knowledge-base" class="rail-link" active-class="rail-on">
            <span>▣ 知识库</span>
          </router-link>
          <div class="rail-divider"></div>
          <div class="rail-section">分析</div>
          <router-link to="/admin/kb-insight" class="rail-link" active-class="rail-on">
            <span>◆ 数据洞察</span>
          </router-link>
        </template>
        <template v-else>
          <div class="rail-section">工作台</div>
          <router-link to="/admin/agent-desk" class="rail-link" active-class="rail-on">
            <span>● 在线接待</span>
          </router-link>
          <router-link to="/admin/work-orders" class="rail-link" active-class="rail-on">
            <span>◉ 工单</span>
          </router-link>
          <router-link to="/admin/agent-insight" class="rail-link" active-class="rail-on">
            <span>◆ 数据洞察</span>
          </router-link>
          <div class="rail-divider"></div>
          <div class="rail-section">知识</div>
          <router-link to="/admin/knowledge-base" class="rail-link" active-class="rail-on">
            <span>◈ 知识库</span>
          </router-link>
        </template>
      </nav>
      <div class="rail-foot">
        <span class="role-tag" :class="roleTagClass">
          {{ roleTagLabel }}
        </span>
        <button class="rail-logout" @click="handleLogout" title="退出登录">⏻</button>
      </div>
    </aside>

    <div class="admin-main">
      <div class="admin-stage">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/shared/composables/useAuth'
import { useAuthStore } from '@/shared/stores/authStore'

const router = useRouter()
const { isAdmin, isKBAdmin } = useAuth()

const roleTagClass = computed(() => {
  if (isAdmin.value) return 'tag-admin'
  if (isKBAdmin.value) return 'tag-kb-admin'
  return 'tag-agent'
})

const roleTagLabel = computed(() => {
  if (isAdmin.value) return '超级管理员'
  if (isKBAdmin.value) return '知识库管理员'
  return '客服'
})

const handleLogout = () => {
  useAuthStore().logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-shell { height: 100%; display: flex; overflow: hidden; }

.admin-rail {
  width: var(--sidebar-w); flex-shrink: 0;
  background: var(--nav-bg); display: flex; flex-direction: column;
}

.rail-brand {
  font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-bold);
  color: #fff; padding: var(--s-5) var(--s-6); border-bottom: 1px solid oklch(0.28 0.015 210);
}

.rail-nav { flex: 1; padding: var(--s-4) var(--s-3); display: flex; flex-direction: column; gap: var(--s-1); }

.rail-section {
  font-size: 10px; font-weight: 600; text-transform: uppercase;
  letter-spacing: 0.08em; color: oklch(0.45 0.012 210);
  padding: var(--s-4) var(--s-4) var(--s-1);
}

.rail-divider { height: 1px; background: oklch(0.28 0.015 210); margin: var(--s-2) 0; }

.rail-link {
  display: block; padding: var(--s-3) var(--s-4); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); color: var(--nav-text);
  text-decoration: none; transition: all var(--dur-fast) var(--ease-soft);
}
.rail-link:hover { background: oklch(0.28 0.018 210); color: #fff; }
.rail-on { background: var(--nav-active); color: #fff; font-weight: var(--weight-medium); }

.rail-foot {
  padding: var(--s-4) var(--s-5); border-top: 1px solid oklch(0.28 0.015 210);
  display: flex; align-items: center; justify-content: space-between;
}
.role-tag { font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 10px; border-radius: var(--radius-full); }
.tag-admin { background: oklch(0.38 0.06 75); color: oklch(0.85 0.06 75); }
.tag-kb-admin { background: oklch(0.35 0.06 260); color: oklch(0.85 0.04 260); }
.tag-agent { background: oklch(0.30 0.05 310); color: oklch(0.85 0.04 310); }
.rail-logout {
  background: none; border: 1px solid oklch(0.35 0.015 210);
  color: oklch(0.55 0.015 210); font-size: 14px; cursor: pointer;
  width: 32px; height: 32px; border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  transition: all var(--dur-fast) var(--ease-soft);
}
.rail-logout:hover { border-color: var(--danger); color: var(--danger); }

.admin-main { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; background: var(--base); }

.admin-stage { flex: 1; overflow-y: auto; }

.page-fade-enter-active { transition: opacity 200ms var(--ease-soft), transform 200ms var(--ease-soft); }
.page-fade-leave-active { transition: opacity 120ms var(--ease-soft); }
.page-fade-enter-from { opacity: 0; transform: translateY(6px); }
.page-fade-leave-to { opacity: 0; }
</style>
