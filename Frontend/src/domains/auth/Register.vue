<template>
  <div class="auth-shell">
    <div class="auth-panel">
      <h1 class="auth-wordmark">加入 Serene</h1>
      <p class="auth-tagline">创建一个账号，解锁完整服务体验</p>

      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="field">
          <label class="field-label" for="reg-username">用户名</label>
          <input id="reg-username" name="reg-username" v-model="username" type="text" required placeholder="至少 2 个字符" autocomplete="username" class="field-input" />
        </div>
        <div class="field">
          <label class="field-label" for="reg-password">密码</label>
          <input id="reg-password" name="reg-password" v-model="password" type="password" required placeholder="至少 6 位" autocomplete="new-password" class="field-input" />
        </div>
        <div class="field">
          <label class="field-label" for="reg-phone">手机号</label>
          <input id="reg-phone" name="reg-phone" v-model="phone" type="text" required placeholder="11 位手机号码" autocomplete="tel" class="field-input" />
        </div>
        <div class="field">
          <label class="field-label" for="reg-captcha">验证码</label>
          <div class="captcha-row">
            <input id="reg-captcha" name="reg-captcha" v-model="captcha" type="text" required placeholder="右侧验证码" class="field-input captcha-in" />
            <button type="button" class="captcha-display" @click="refreshCaptcha">{{ captchaCode }}</button>
          </div>
        </div>
        <button type="submit" class="btn-fill" :disabled="submitting">
          <span v-if="submitting" class="dot-pulse"></span>
          <span>{{ submitting ? '创建中…' : '注 册' }}</span>
        </button>
        <p v-if="error" class="hint-error">{{ error }}</p>
      </form>

      <p class="switch-line">已有账号？<router-link to="/login">立即登录</router-link></p>
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
const phone = ref('')
const captcha = ref('')
const captchaCode = ref('')
const error = ref('')
const submitting = ref(false)
const router = useRouter()
const { register } = useAuth()

const generateCaptcha = () => {
  const chars = '23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz'
  let r = ''
  for (let i = 0; i < 4; i++) r += chars.charAt(Math.floor(Math.random() * chars.length))
  return r
}
const refreshCaptcha = () => { captchaCode.value = generateCaptcha() }
refreshCaptcha()

const handleRegister = async () => {
  error.value = ''
  if (username.value.trim().length < 2) { error.value = '用户名至少需要 2 个字符'; return }
  if (password.value.length < 6) { error.value = '密码长度不能少于 6 位'; return }
  if (captcha.value.toLowerCase() !== captchaCode.value.toLowerCase()) {
    error.value = '验证码错误，请重新输入'
    refreshCaptcha(); captcha.value = ''; return
  }
  if (!/^1[3-9]\d{9}$/.test(phone.value)) { error.value = '请输入有效的 11 位手机号码'; return }
  if (submitting.value) return
  submitting.value = true
  try {
    const result = await register({ username: username.value, password: password.value, phone: phone.value })
    if (result.success) router.push('/login')
    else error.value = result.message || '注册失败'
  } catch { error.value = '网络异常，请稍后重试' }
  finally { submitting.value = false }
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
  max-width: 420px;
  padding: var(--s-10) var(--s-8) var(--s-10);
  background: var(--surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
}

.auth-wordmark {
  font-family: var(--font-heading);
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  color: var(--brand);
  text-align: center;
  letter-spacing: -0.02em;
}

.auth-tagline {
  text-align: center;
  color: var(--ink-soft);
  font-size: var(--text-sm);
  margin: var(--s-2) 0 var(--s-8);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
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

.captcha-row { display: flex; gap: var(--s-3); align-items: center; }
.captcha-in { flex: 1; }

.captcha-display {
  flex-shrink: 0;
  width: 96px; height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--brand-pale), var(--brand-soft));
  font-family: var(--font-mono);
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  letter-spacing: 4px;
  color: var(--brand-deep);
  cursor: pointer;
  user-select: none;
  transition: transform var(--dur-fast) var(--ease-out);
}
.captcha-display:hover { transform: scale(1.06); }

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
