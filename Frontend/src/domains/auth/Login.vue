<template>
  <div class="auth-shell">
    <div class="auth-panel">
      <h1 class="auth-wordmark">Serene</h1>
      <p class="auth-tagline">登录以继续使用智能客服</p>

      <form class="auth-form" @submit.prevent="handleLogin">
        <div class="field">
          <label class="field-label" for="login-username">用户名</label>
          <input id="login-username" name="login-username" v-model="username" type="text" required placeholder="输入用户名" autocomplete="username" class="field-input" />
        </div>
        <div class="field">
          <label class="field-label" for="login-password">密码</label>
          <input id="login-password" name="login-password" v-model="password" type="password" required placeholder="输入密码" autocomplete="current-password" class="field-input" />
        </div>
        <button type="submit" class="btn-fill" :disabled="submitting">
          <span v-if="submitting" class="dot-pulse"></span>
          <span>{{ submitting ? '验证中…' : '登 录' }}</span>
        </button>
        <p v-if="error" class="hint-error">{{ error }}</p>
      </form>

      <p class="switch-line">还没有账号？<router-link to="/register">创建账号</router-link></p>
      <button class="ghost-link" @click="$router.push('/')">← 返回首页</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/shared/composables/useAuth'

const username = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)
const router = useRouter()
const { login } = useAuth()

const handleLogin = async () => {
  if (submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const result = await login({ username: username.value, password: password.value })
    if (result.success) {
      if (result.roles.includes('ADMIN')) router.push('/admin/agent-management')
      else if (result.roles.includes('KB_ADMIN')) router.push('/admin/knowledge-review')
      else if (result.roles.includes('AGENT')) router.push('/admin/work-orders')
      else router.push('/chat')
    } else {
      error.value = result.message || '用户名或密码错误'
    }
  } catch {
    error.value = '网络异常，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-shell {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s-4);
  background:
    radial-gradient(ellipse 80% 60% at 50% -20%, var(--brand-pale), transparent),
    var(--base);
}

.auth-panel {
  width: 100%;
  max-width: 400px;
  padding: var(--s-12) var(--s-8) var(--s-10);
  background: var(--surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
}

.auth-wordmark {
  font-family: var(--font-heading);
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  color: var(--brand);
  text-align: center;
  letter-spacing: -0.02em;
}

.auth-tagline {
  text-align: center;
  color: var(--ink-soft);
  font-size: var(--text-sm);
  margin: var(--s-2) 0 var(--s-10);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--s-5);
}

.field { display: flex; flex-direction: column; gap: var(--s-2); }

.field-label {
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--ink-soft);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.field-input {
  width: 100%;
  padding: var(--s-3) var(--s-4);
  border: 1.5px solid var(--border);
  border-radius: var(--radius-md);
  font-size: var(--text-md);
  font-family: var(--font-body);
  color: var(--ink);
  background: var(--base);
  transition: border-color var(--dur-fast) var(--ease-soft),
              box-shadow var(--dur-fast) var(--ease-soft);
}
.field-input::placeholder { color: var(--ink-muted); font-weight: var(--weight-light); }
.field-input:focus {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
  outline: none;
}

.btn-fill {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--s-2);
  width: 100%;
  padding: var(--s-3) var(--s-4);
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  font-family: var(--font-body);
  color: oklch(0.97 0.005 95);
  background: var(--brand);
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-soft),
              transform var(--dur-fast) var(--ease-out);
}
.btn-fill:hover:not(:disabled) { background: var(--brand-deep); transform: translateY(-1px); }
.btn-fill:active:not(:disabled) { transform: translateY(0); }
.btn-fill:disabled { opacity: 0.6; cursor: not-allowed; }

.dot-pulse {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 0.8s var(--ease-in-out) infinite alternate;
}
@keyframes pulse { to { opacity: 0.3; transform: scale(0.7); } }

.hint-error {
  font-size: var(--text-xs);
  color: var(--danger);
  background: var(--danger-soft);
  padding: var(--s-3) var(--s-4);
  border-radius: var(--radius-sm);
  text-align: center;
}

.switch-line {
  margin-top: var(--s-8);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--ink-soft);
}
.switch-line a { font-weight: var(--weight-medium); }

.ghost-link {
  display: block;
  width: 100%;
  margin-top: var(--s-5);
  padding: var(--s-2);
  border: none; background: none;
  font-size: var(--text-xs);
  font-family: var(--font-body);
  color: var(--ink-muted);
  cursor: pointer; text-align: center;
  transition: color var(--dur-fast) var(--ease-soft);
}
.ghost-link:hover { color: var(--brand); }
</style>
