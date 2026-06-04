<template>
  <div class="user-page">
    <div class="toolbar">
      <div class="toolbar-row">
        <div class="search-box">
          <input v-model="filters.keyword" @input="debouncedSearch" @keyup.enter="doSearch" placeholder="搜索用户名/昵称/ID/手机号" class="search-input" />
          <button class="search-btn" @click="doSearch">搜索</button>
        </div>
        <select v-model="filters.status" @change="doSearch" class="filter-select">
          <option value="">全部状态</option>
          <option value="1">启用</option>
          <option value="0">禁用</option>
        </select>
      </div>
    </div>

    <div class="table-wrap">
      <table class="user-table" v-loading="loading">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>昵称</th>
            <th>手机号</th>
            <th>类型</th>
            <th>标签</th>
            <th>状态</th>
            <th>注册时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="users.length === 0">
            <td colspan="9" class="empty">暂无数据</td>
          </tr>
          <tr v-for="u in users" :key="u.id">
            <td class="mono">{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>{{ u.nickname || '-' }}</td>
            <td>{{ u.phone || '-' }}</td>
            <td><span class="user-type-badge">{{ userTypeLabel(u.roleName) }}</span></td>
            <td><span v-if="u.tags" class="user-tags">{{ u.tags }}</span><span v-else class="hint-text">-</span></td>
            <td><span :class="['status-dot', u.status === 1 ? 'on' : 'off']">{{ u.status === 1 ? '启用' : '禁用' }}</span></td>
            <td class="date">{{ fmt(u.createTime) }}</td>
            <td>
              <button class="btn-link" :class="{ 'btn-link-off': u.status === 1 }" @click="toggleStatus(u)">
                {{ u.status === 1 ? '禁用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager" v-if="total > 0">
      <span class="pager-info">共 {{ total }} 条</span>
      <div class="pager-btns">
        <button :disabled="page <= 1" @click="goPage(page - 1)">‹</button>
        <span class="pager-current">{{ page }}</span>
        <button :disabled="page * pageSize >= total" @click="goPage(page + 1)">›</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { searchUsers, toggleUserStatus } from '@/domains/agentmgmt/agentManagementService'

const users = ref([])
const total = ref(0)
const loading = ref(false)
const filters = reactive({ keyword: '', status: '' })
const page = ref(1)
const pageSize = ref(20)
let searchTimer = null

const userTypeLabel = (role) => {
  if (role === 'VIP') return 'VIP会员'
  return '注册用户'
}
const fmt = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

const doSearch = () => {
  page.value = 1
  const params = { page: page.value, size: pageSize.value }
  if (filters.keyword) params.keyword = filters.keyword
  if (filters.status) params.status = Number(filters.status)
  fetchData(params)
}

const debouncedSearch = () => { clearTimeout(searchTimer); searchTimer = setTimeout(doSearch, 300) }
const goPage = (p) => { page.value = p; fetchData({ page: page.value, size: pageSize.value, keyword: filters.keyword || undefined, status: filters.status ? Number(filters.status) : undefined }) }

const fetchData = async (params) => {
  loading.value = true
  try {
    const res = await searchUsers(params)
    if (res.data.code === 200) {
      users.value = res.data.data.list || []
      total.value = res.data.data.total || 0
    }
  } catch (err) { console.error(err) }
  finally { loading.value = false }
}

const toggleStatus = async (u) => {
  const newStatus = u.status === 1 ? 0 : 1
  try {
    const res = await toggleUserStatus(u.id, newStatus)
    if (res.data.code === 200) u.status = newStatus
  } catch { alert('操作失败') }
}

onMounted(() => fetchData({ page: 1, size: pageSize.value }))
</script>

<style scoped>
.user-page { display: flex; flex-direction: column; gap: var(--s-4); padding: var(--page-pad-y) var(--page-pad-x); height: 100%; }

.toolbar { display: flex; flex-direction: column; gap: var(--s-2); flex-shrink: 0; padding-bottom: var(--s-1); }
.toolbar-row { display: flex; align-items: center; gap: var(--s-2); }

.search-box { display: flex; align-items: stretch; flex: 0 1 320px; }
.search-input {
  flex: 1; min-width: 0; padding: 8px 12px; border: 1.5px solid oklch(0.88 0.005 210);
  border-right: none; border-radius: var(--radius-md) 0 0 var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body);
  color: var(--ink); background: var(--surface); transition: border-color var(--dur-fast) var(--ease-soft);
}
.search-input::placeholder { color: var(--ink-muted); }
.search-input:focus { border-color: var(--brand); outline: none; box-shadow: 0 0 0 3px var(--brand-soft); }
.search-btn {
  padding: 8px 16px; border: 1.5px solid var(--brand); border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--brand); color: #fff; font-size: var(--text-sm); font-weight: var(--weight-medium);
  font-family: var(--font-body); cursor: pointer; white-space: nowrap;
  transition: background var(--dur-fast) var(--ease-soft);
}
.search-btn:hover { background: var(--brand-deep); }

.filter-select {
  padding: 8px 12px; border: 1.5px solid oklch(0.88 0.005 210); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--surface);
  cursor: pointer; min-width: 120px;
}

.table-wrap { flex: 1; min-height: 0; overflow-y: auto; }
.user-table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
.user-table th {
  text-align: left; padding: 10px 12px; background: oklch(0.96 0.005 210);
  color: var(--ink-soft); font-weight: var(--weight-semibold); font-size: var(--text-2xs);
  text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1px solid oklch(0.90 0.005 210);
  position: sticky; top: 0; z-index: 1;
}
.user-table td { padding: 10px 12px; color: var(--ink); border-bottom: 1px solid oklch(0.94 0.005 210); }
.user-table tbody tr:hover { background: oklch(0.97 0.01 255); }
.empty { text-align: center; color: var(--ink-muted); padding: 48px 0 !important; }
.mono { font-family: var(--font-mono); font-size: var(--text-2xs); color: var(--ink-muted); }
.date { font-size: var(--text-2xs); color: var(--ink-muted); white-space: nowrap; }

.btn-link { padding: 0; border: none; background: none; font-size: var(--text-sm); font-family: var(--font-body); color: var(--brand); cursor: pointer; font-weight: var(--weight-medium); }
.btn-link:hover { text-decoration: underline; }
.btn-link-off { color: var(--danger); }

.user-type-badge { display: inline-block; font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 8px; border-radius: var(--radius-full); background: oklch(0.92 0.04 260 / 0.25); color: oklch(0.35 0.06 260); }
.user-tags { font-size: var(--text-2xs); color: oklch(0.55 0.08 255); background: oklch(0.97 0.02 255); padding: 2px 6px; border-radius: var(--radius-sm); }

.status-dot { font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 10px; border-radius: var(--radius-full); }
.status-dot.on { background: oklch(0.93 0.05 155 / 0.3); color: oklch(0.38 0.06 155); }
.status-dot.off { background: oklch(0.93 0.02 15 / 0.2); color: oklch(0.45 0.02 15); }

.pager { display: flex; align-items: center; justify-content: space-between; padding: var(--s-2) 0; flex-shrink: 0; }
.pager-info { font-size: var(--text-sm); color: var(--ink-soft); }
.pager-btns { display: flex; align-items: center; gap: var(--s-2); }
.pager-btns button {
  padding: 6px 12px; border: 1.5px solid oklch(0.88 0.005 210); border-radius: var(--radius-md);
  font-size: var(--text-sm); background: var(--surface); cursor: pointer; color: var(--ink);
}
.pager-btns button:disabled { opacity: 0.4; cursor: not-allowed; }
.pager-btns button:hover:not(:disabled) { border-color: var(--brand); }
.pager-current { font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--ink); min-width: 24px; text-align: center; }
</style>
