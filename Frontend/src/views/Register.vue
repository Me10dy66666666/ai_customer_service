<template>
  <div class="register-page">
    <div class="register-container">
      <div class="header-row">
        <span class="back-btn" @click="router.push('/login')">&lt;</span>
        <h2>Register</h2>
      </div>
    <form @submit.prevent="handleRegister">
      <div class="form-group">
        <label>Username:</label>
        <input v-model="username" type="text" required placeholder="请输入用户名" autocomplete="username" />
      </div>
      <div class="form-group">
        <label>Password:</label>
        <input v-model="password" type="password" required placeholder="请输入密码" autocomplete="new-password" />
      </div>
      <div class="form-group">
        <label>Phone:</label>
        <input v-model="phone" type="text" required placeholder="请输入手机号" autocomplete="tel" />
      </div>
      <div class="form-group">
        <label>Verification Code:</label>
        <div class="captcha-container">
          <input v-model="captcha" type="text" placeholder="请输入验证码" required />
          <div class="captcha-box" @click="refreshCaptcha">{{ captchaCode }}</div>
        </div>
      </div>
      <button type="submit">Register</button>
      <p>Already have an account? <router-link to="/login">Login</router-link></p>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth'

const username = ref('')
const password = ref('')
const phone = ref('')
const captcha = ref('')
const captchaCode = ref('')
const error = ref('')
const router = useRouter()

const generateCaptcha = () => {
  const chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
  let result = ''
  for (let i = 0; i < 4; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

const refreshCaptcha = () => {
  captchaCode.value = generateCaptcha()
}

// Initialize captcha
refreshCaptcha()

const handleRegister = async () => {
  if (captcha.value.toLowerCase() !== captchaCode.value.toLowerCase()) {
    error.value = 'Invalid verification code'
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    error.value = '请输入有效的11位手机号码'
    return
  }
  try {
    // Get visitor session ID from session storage
    const sessionId = sessionStorage.getItem('chat_session_id')
    const registerData = {
      username: username.value,
      password: password.value,
      phone: phone.value,
      sessionId: sessionId
    }

    const response = await register(registerData)
    if (response.data.code === 200) {
      // Clear visitor session ID after successful registration
      if (sessionId) sessionStorage.removeItem('chat_session_id')
      
      alert('Registration successful! Please login.')
      router.push('/login')
    } else {
      error.value = response.data.message
    }
  } catch (err) {
    error.value = 'Registration failed. Please try again.'
    console.error(err)
  }
}
</script>

<style scoped>
.register-page {
  height: 100%;
  overflow-y: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}

.register-container {
  max-width: 400px;
  width: 100%;
  padding: 20px;
  border: 1px solid #ccc;
  border-radius: 5px;
}
.form-group {
  margin-bottom: 15px;
}
label {
  display: block;
  margin-bottom: 5px;
}
input {
  width: 100%;
  padding: 8px;
  box-sizing: border-box;
}
button {
  width: 100%;
  padding: 10px;
  background-color: #28a745;
  color: white;
  border: none;
  cursor: pointer;
}
button:hover {
  background-color: #218838;
}
.error {
  color: red;
  margin-top: 10px;
}
.form-text {
  font-size: 0.875em;
  color: #6c757d;
  display: block;
  margin-top: 5px;
}
.captcha-container {
  display: flex;
  align-items: center;
}
.captcha-container input {
  flex: 1;
  margin-right: 10px;
}
.captcha-box {
  width: 100px;
  height: 35px;
  background-color: #f0f0f0;
  border: 1px solid #ccc;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-weight: bold;
  letter-spacing: 2px;
  user-select: none;
}
.header-row {
  display: flex;
  align-items: center;
  position: relative;
  margin-bottom: 20px;
}
.header-row h2 {
  flex: 1;
  text-align: center;
  margin: 0;
}
.back-btn {
  font-size: 24px;
  cursor: pointer;
  color: #666;
  position: absolute;
  left: 0;
  padding: 5px;
}
.back-btn:hover {
  color: #333;
}
</style>
