<template>
  <div class="staff-page">
    <div class="toolbar">
      <div class="toolbar-row">
        <button class="btn-create" @click="showFormModal = true">+ 创建员工</button>
        <div class="search-box">
          <input v-model="filters.keyword" @input="debouncedSearch" @keyup.enter="doSearch" placeholder="搜索用户名/昵称/ID/手机号" class="search-input" />
          <button class="search-btn" @click="doSearch">搜索</button>
        </div>
        <select v-model="filters.role" @change="doSearch" class="filter-select">
          <option v-for="o in ROLE_FILTER_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <select v-model="filters.status" @change="doSearch" class="filter-select">
          <option v-for="o in ROLE_STATUS_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <div class="batch-dropdown">
          <span class="batch-count" v-if="selectedIds.length > 0">已选 {{ selectedIds.length }} 项</span>
          <span class="batch-count" v-else>勾选后可批量操作</span>
          <button class="batch-btn" @click="batchEnable" :disabled="selectedIds.length === 0">批量启用</button>
          <button class="batch-btn" @click="batchDisable" :disabled="selectedIds.length === 0">批量禁用</button>
          <button class="batch-btn batch-btn-danger" @click="confirmBatchDel = true" :disabled="selectedIds.length === 0">批量删除</button>
        </div>
      </div>
    </div>

    <div class="table-wrap">
      <table class="staff-table" v-loading="store.loading">
        <thead>
          <tr>
            <th class="th-check"><input type="checkbox" :checked="allChecked" @change="toggleAll" /></th>
            <th>ID</th>
            <th>用户名</th>
            <th>昵称</th>
            <th>角色</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.agents.length === 0">
            <td colspan="8" class="empty">暂无数据</td>
          </tr>
          <tr v-for="a in store.agents" :key="a.id">
            <td class="td-check"><input type="checkbox" :value="a.id" v-model="checkedIds" /></td>
            <td class="mono">{{ a.id }}</td>
            <td>{{ a.username }}</td>
            <td>{{ a.nickname || '-' }}</td>
            <td><span class="role-badge" :class="roleBadgeClass(a.roleName)">{{ roleLabel(a.roleName) }}</span></td>
            <td><span :class="['status-dot', a.status === 1 ? 'on' : 'off']">{{ a.status === 1 ? '启用' : '禁用' }}</span></td>
            <td class="date">{{ fmt(a.createTime) }}</td>
            <td class="acts">
              <button class="btn-act" @click="openEdit(a)">编辑</button>
              <button class="btn-act" @click="toggleStatus(a)">{{ a.status === 1 ? '禁用' : '启用' }}</button>
              <button class="btn-act btn-act-del" @click="askDel(a)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager" v-if="store.total > 0">
      <span class="pager-info">共 {{ store.total }} 条</span>
      <div class="pager-btns">
        <button :disabled="page <= 1" @click="goPage(page - 1)">‹</button>
        <span class="pager-current">{{ page }}</span>
        <button :disabled="page * pageSize >= store.total" @click="goPage(page + 1)">›</button>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="showFormModal" class="overlay" @click.self="closeForm">
        <div class="modal modal-lg">
          <h3 class="modal-title">{{ editing ? '编辑员工' : '创建员工' }}</h3>
          <div class="form-two-col">
            <div class="col-left">
              <div class="field">
                <label class="field-label">用户名 <span class="req">*</span></label>
                <input v-model="form.username" type="text" :disabled="!!editing" placeholder="登录用户名" class="field-input" />
              </div>
              <div class="field">
                <label class="field-label">密码 <span class="req" v-if="!editing">*</span></label>
                <input v-model="form.password" type="password" :placeholder="editing ? '留空则不修改' : '设置登录密码'" class="field-input" />
              </div>
              <div class="field">
                <label class="field-label">昵称</label>
                <input v-model="form.nickname" type="text" placeholder="显示名称" class="field-input" />
              </div>
              <div class="field">
                <label class="field-label">手机号</label>
                <input v-model="form.phone" type="text" placeholder="选填" class="field-input" />
              </div>
              <div class="field">
                <label class="field-label">邮箱</label>
                <input v-model="form.email" type="email" placeholder="选填" class="field-input" />
              </div>
              <div class="field">
                <label class="field-label">角色 <span class="req">*</span></label>
                <select v-model="form.roleName" @change="onRoleChange" class="field-input">
                  <option value="" disabled>选择角色</option>
                  <option v-for="o in ROLE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
                </select>
              </div>
              <div class="field" v-if="form.roleName === 'AGENT'">
                <label class="field-label">技能标签</label>
                <div class="skills-grid">
                  <label v-for="opt in skillOptions" :key="opt" class="skill-chip" :class="{ on: form.skills.includes(opt) }">
                    <input type="checkbox" :value="opt" v-model="form.skills" class="skill-check" />{{ opt }}
                  </label>
                </div>
              </div>
              <div class="field" v-else-if="form.roleName">
                <label class="field-label">技能标签</label>
                <span class="hint-text">暂无需设定</span>
              </div>
            </div>
            <div class="col-right">
              <label class="field-label">权限配置 <span class="hint-text">自动填充，可手动调整</span></label>
              <div v-if="!form.roleName" class="perm-empty">请先选择角色</div>
              <div v-else-if="form.roleName === 'AGENT'" class="perm-empty">客服所有功能为自带权限，无需额外配置</div>
              <div v-else class="perm-list">
                <label v-for="p in currentPerms" :key="p.code" class="perm-item" :class="{ checked: form.selectedPerms.includes(p.code) }">
                  <input type="checkbox" :value="p.code" :checked="form.selectedPerms.includes(p.code)" @change="toggleSelectedPerm(p.code)" class="perm-check" />
                  <span class="perm-code">{{ p.code }}</span>
                </label>
              </div>
            </div>
          </div>
          <div v-if="formError" class="banner banner-err">{{ formError }}</div>
          <div class="modal-acts">
            <button class="btn-ghost" @click="closeForm">取消</button>
            <button class="btn-brand" @click="submitForm" :disabled="store.saving">{{ store.saving ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>

      <div v-if="delDlg" class="overlay" @click.self="delDlg = false">
        <div class="modal modal-sm">
          <h3 class="modal-title">确认删除</h3>
          <p class="modal-desc">确定删除「{{ delTarget?.username }}」？该操作不可撤销。</p>
          <div class="modal-acts">
            <button class="btn-ghost" @click="delDlg = false">取消</button>
            <button class="btn-brand" style="background:var(--danger)" @click="doDel">删除</button>
          </div>
        </div>
      </div>

      <div v-if="confirmBatchDel" class="overlay" @click.self="confirmBatchDel = false">
        <div class="modal modal-sm">
          <h3 class="modal-title">批量删除</h3>
          <p class="modal-desc">确定删除已选的 {{ selectedIds.length }} 名员工？</p>
          <div class="modal-acts">
            <button class="btn-ghost" @click="confirmBatchDel = false">取消</button>
            <button class="btn-brand" style="background:var(--danger)" @click="doBatchDel">删除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useAgentManagementStore } from '@/shared/stores/agentManagementStore'
import { ROLE_OPTIONS, ROLE_LABEL_MAP, ROLE_PERMISSION_MAP, ALL_PERMISSIONS, ROLE_FILTER_OPTIONS, ROLE_STATUS_OPTIONS } from './permissionConfig'

const store = useAgentManagementStore()
const filters = reactive({ keyword: '', role: '', status: '' })
const page = ref(1)
const pageSize = ref(20)
const showFormModal = ref(false)
const editing = ref(null)
const form = reactive({ username: '', password: '', nickname: '', phone: '', email: '', roleName: '', skills: [], selectedPerms: [] })
const formError = ref('')
const delDlg = ref(false)
const delTarget = ref(null)
const checkedIds = ref([])
const confirmBatchDel = ref(false)
const skillOptions = ['售前', '售后']
let searchTimer = null

const selectedIds = computed(() => checkedIds.value.map(Number))
const showBatchBar = computed(() => selectedIds.value.length > 0)
const allChecked = computed(() => store.agents.length > 0 && checkedIds.value.length === store.agents.length)

const currentPerms = computed(() => {
  if (!form.roleName) return []
  return ALL_PERMISSIONS.filter(p => ROLE_PERMISSION_MAP[form.roleName]?.includes(p.code))
})

const roleLabel = (role) => ROLE_LABEL_MAP[role] || role || '-'
const roleBadgeClass = (role) => {
  if (role === 'ADMIN') return 'role-admin'
  if (role === 'KB_ADMIN') return 'role-kb'
  if (role === 'AGENT') return 'role-agent'
  return ''
}
const fmt = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

const toggleAll = () => {
  if (allChecked.value) checkedIds.value = []
  else checkedIds.value = store.agents.map(a => a.id)
}

const onRoleChange = () => {
  const perms = ROLE_PERMISSION_MAP[form.roleName] || []
  form.selectedPerms = [...perms]
}

const toggleSelectedPerm = (code) => {
  const idx = form.selectedPerms.indexOf(code)
  idx >= 0 ? form.selectedPerms.splice(idx, 1) : form.selectedPerms.push(code)
}

const resetForm = () => {
  form.username = ''; form.password = ''; form.nickname = ''; form.phone = ''; form.email = ''
  form.roleName = ''; form.skills = []; form.selectedPerms = []
  editing.value = null; formError.value = ''
}

const openEdit = (a) => {
  editing.value = a
  form.username = a.username; form.password = ''; form.nickname = a.nickname || ''
  form.phone = a.phone || ''; form.email = a.email || ''
  form.roleName = a.roleName || ''; form.skills = a.skills ? [...a.skills] : []
  form.selectedPerms = []
  onRoleChange()
  showFormModal.value = true
}

const closeForm = () => { showFormModal.value = false; resetForm() }

const submitForm = async () => {
  formError.value = ''
  if (!form.username.trim()) { formError.value = '用户名不能为空'; return }
  if (!editing.value && !form.password.trim()) { formError.value = '密码不能为空'; return }
  if (!form.roleName) { formError.value = '请选择角色'; return }
  const payload = {
    username: form.username.trim(),
    nickname: form.nickname.trim() || undefined,
    phone: form.phone.trim() || undefined,
    email: form.email.trim() || undefined,
    roleName: form.roleName,
    skills: form.roleName === 'AGENT' && form.skills.length ? form.skills : undefined
  }
  let result
  if (editing.value) {
    if (form.password.trim()) payload.password = form.password.trim()
    payload.status = editing.value.status
    result = await store.editAgent(editing.value.id, payload)
  } else {
    payload.password = form.password.trim()
    result = await store.addAgent(payload)
  }
  if (result.success) { closeForm(); doSearch() } else { formError.value = result.message }
}

const toggleStatus = async (a) => {
  const r = await store.toggleAgentStatus(a.id, a.status)
  if (!r.success) alert(r.message)
}

const askDel = (a) => { delTarget.value = a; delDlg.value = true }
const doDel = async () => {
  if (!delTarget.value) return
  const r = await store.removeAgent(delTarget.value.id)
  delDlg.value = false
  if (!r.success) alert(r.message)
}

const batchEnable = async () => { await store.batchUpdateStatus(selectedIds.value, 1); checkedIds.value = [] }
const batchDisable = async () => { await store.batchUpdateStatus(selectedIds.value, 0); checkedIds.value = [] }
const doBatchDel = async () => { await store.batchDelete(selectedIds.value); checkedIds.value = []; confirmBatchDel.value = false }

const doSearch = () => {
  page.value = 1
  const params = { page: page.value, size: pageSize.value }
  if (filters.keyword) params.keyword = filters.keyword
  if (filters.role) params.role = filters.role
  if (filters.status) params.status = Number(filters.status)
  store.fetchAgents(params)
}

const debouncedSearch = () => { clearTimeout(searchTimer); searchTimer = setTimeout(doSearch, 300) }
const goPage = (p) => { page.value = p; doSearch() }

onMounted(() => store.fetchAgents({ page: 1, size: pageSize.value }))
</script>

<style scoped>
.staff-page { display: flex; flex-direction: column; gap: var(--s-4); padding: var(--page-pad-y) var(--page-pad-x); height: 100%; }

.toolbar { display: flex; flex-direction: column; gap: var(--s-2); flex-shrink: 0; padding-bottom: var(--s-1); }
.toolbar-row { display: flex; align-items: center; gap: var(--s-2); flex-wrap: wrap; }

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

.btn-create {
  padding: 8px 20px; border: none; border-radius: var(--radius-md);
  font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body);
  color: #fff; background: var(--brand); cursor: pointer;
  transition: background var(--dur-fast) var(--ease-soft); white-space: nowrap;
}
.btn-create:hover { background: var(--brand-deep); }

.batch-dropdown { display: flex; align-items: center; gap: var(--s-2); margin-left: auto; }
.batch-count { font-size: var(--text-sm); color: var(--ink-soft); }
.batch-btn { padding: 6px 14px; border: 1.5px solid oklch(0.88 0.005 210); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); background: var(--surface); color: var(--ink); cursor: pointer; }
.batch-btn:hover:not(:disabled) { border-color: var(--brand); }
.batch-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.batch-btn-danger { color: var(--danger); border-color: var(--danger); }
.batch-btn-danger:hover { background: var(--danger-soft); }

.table-wrap { flex: 1; min-height: 0; overflow-y: auto; }
.staff-table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
.staff-table th {
  text-align: left; padding: 10px 12px; background: oklch(0.96 0.005 210);
  color: var(--ink-soft); font-weight: var(--weight-semibold); font-size: var(--text-2xs);
  text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1px solid oklch(0.90 0.005 210);
  position: sticky; top: 0; z-index: 1;
}
.staff-table td { padding: 10px 12px; color: var(--ink); border-bottom: 1px solid oklch(0.94 0.005 210); }
.staff-table tbody tr:hover { background: oklch(0.97 0.01 255); }
.th-check, .td-check { width: 40px; text-align: center; }
.td-check input, .th-check input { accent-color: var(--brand); cursor: pointer; }
.empty { text-align: center; color: var(--ink-muted); padding: 48px 0 !important; }
.mono { font-family: var(--font-mono); font-size: var(--text-2xs); color: var(--ink-muted); }
.date { font-size: var(--text-2xs); color: var(--ink-muted); white-space: nowrap; }
.acts { display: flex; gap: 6px; white-space: nowrap; }

.btn-act {
  padding: 5px 14px; border: 1.5px solid oklch(0.78 0.01 210); border-radius: var(--radius-md);
  font-size: var(--text-xs); font-weight: var(--weight-medium); font-family: var(--font-body);
  color: var(--ink); background: var(--surface); cursor: pointer;
  transition: all var(--dur-fast) var(--ease-soft);
}
.btn-act:hover { border-color: var(--brand); color: var(--brand); background: var(--brand-pale); }
.btn-act-del { color: var(--danger); border-color: oklch(0.75 0.08 15); }
.btn-act-del:hover { border-color: var(--danger); background: var(--danger-soft); }

.role-badge { display: inline-block; font-size: var(--text-3xs); font-weight: var(--weight-medium); padding: 2px 8px; border-radius: var(--radius-full); }
.role-admin { background: oklch(0.92 0.06 75 / 0.3); color: oklch(0.38 0.06 75); }
.role-kb { background: oklch(0.92 0.04 260 / 0.3); color: oklch(0.35 0.06 260); }
.role-agent { background: oklch(0.92 0.04 310 / 0.3); color: oklch(0.30 0.05 310); }

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

.overlay { position: fixed; inset: 0; background: oklch(0.18 0.018 210 / 0.35); display: flex; align-items: center; justify-content: center; z-index: 200; padding: var(--s-4); animation: fadeIn var(--dur-fast) var(--ease-soft); }
.modal { background: var(--surface); border-radius: var(--radius-2xl); padding: var(--s-8); width: 100%; box-shadow: var(--shadow-xl); animation: popIn var(--dur-normal) var(--ease-out); }
.modal-lg { max-width: 720px; }
.modal-sm { max-width: 380px; }
.modal-title { font-family: var(--font-heading); font-size: var(--text-xl); font-weight: var(--weight-semibold); color: var(--ink); margin-bottom: var(--s-5); }
.modal-desc { font-size: var(--text-sm); color: var(--ink-soft); margin-bottom: var(--s-6); }
.modal-acts { display: flex; justify-content: flex-end; gap: var(--s-3); margin-top: var(--s-5); }

.form-two-col { display: flex; gap: var(--s-6); }
.col-left { flex: 1; }
.col-right { flex: 1; border-left: 1px solid oklch(0.90 0.005 210); padding-left: var(--s-6); }

.field { display: flex; flex-direction: column; gap: 4px; margin-bottom: var(--s-3); }
.field-label { font-size: var(--text-2xs); font-weight: var(--weight-medium); color: var(--ink-soft); text-transform: uppercase; letter-spacing: 0.06em; }
.req { color: var(--danger); }
.field-input {
  padding: 8px 12px; border: 1.5px solid oklch(0.88 0.005 210); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink); background: var(--base);
  transition: border-color var(--dur-fast) var(--ease-soft);
}
.field-input:focus { border-color: var(--brand); outline: none; box-shadow: 0 0 0 3px var(--brand-soft); }
.field-input:disabled { opacity: 0.5; }

.hint-text { font-size: var(--text-2xs); color: var(--ink-muted); font-weight: var(--weight-normal); text-transform: none; }

.skills-grid { display: flex; gap: 6px; flex-wrap: wrap; }
.skill-chip {
  padding: 5px 12px; border: 1.5px solid oklch(0.88 0.005 210); border-radius: var(--radius-full);
  font-size: var(--text-xs); color: var(--ink-soft); cursor: pointer; user-select: none;
  transition: all var(--dur-fast) var(--ease-soft);
}
.skill-chip.on { background: var(--brand-pale); border-color: var(--brand); color: var(--brand-deep); }
.skill-check { display: none; }

.perm-empty { padding: var(--s-6) var(--s-4); text-align: center; color: var(--ink-muted); font-size: var(--text-sm); }
.perm-list { display: flex; flex-direction: column; gap: 4px; max-height: 280px; overflow-y: auto; }
.perm-item {
  display: flex; align-items: center; gap: var(--s-2); padding: 6px 10px;
  border-radius: var(--radius-sm); cursor: pointer; font-size: var(--text-sm);
  transition: background var(--dur-fast) var(--ease-soft);
}
.perm-item:hover { background: oklch(0.96 0.02 255); }
.perm-item.checked { background: oklch(0.96 0.03 255); }
.perm-check { accent-color: var(--brand); cursor: pointer; }
.perm-code { font-family: var(--font-mono); font-size: var(--text-2xs); color: var(--ink-soft); }

.btn-brand {
  padding: 8px 24px; border: none; border-radius: var(--radius-md);
  font-size: var(--text-sm); font-weight: var(--weight-semibold); font-family: var(--font-body);
  color: #fff; background: var(--brand); cursor: pointer;
}
.btn-brand:hover:not(:disabled) { background: var(--brand-deep); }
.btn-brand:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-ghost {
  padding: 8px 24px; border: 1.5px solid oklch(0.88 0.005 210); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); color: var(--ink-soft); background: var(--surface); cursor: pointer;
}
.btn-ghost:hover { border-color: var(--ink-muted); }
.banner { padding: var(--s-3) var(--s-4); border-radius: var(--radius-md); font-size: var(--text-sm); }
.banner-err { background: var(--danger-soft); color: var(--danger); }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes popIn { from { opacity: 0; transform: scale(0.94) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }
</style>
