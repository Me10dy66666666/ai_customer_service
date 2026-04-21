<template>
  <div class="login-page">
    <div class="login-container">
      <div class="header-row">
        <span class="back-btn" @click="router.push('/')">&lt;</span>
        <h2>Login</h2>
      </div>
    <form @submit.prevent="handleLogin">
      <div class="form-group">
        <label>Username:</label>
        <input v-model="username" type="text" required placeholder="请输入用户名" autocomplete="username" />
      </div>
      <div class="form-group">
        <label>Password:</label>
        <input v-model="password" type="password" required placeholder="请输入密码" autocomplete="current-password" />
      </div>
      <button type="submit">Login</button>
      <p>Don't have an account? <router-link to="/register">Register</router-link></p>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/auth'

const username = ref('')
const password = ref('')
const error = ref('')
const router = useRouter()

const handleLogin = async () => {
  try {
    // Get visitor session ID from session storage
    const sessionId = sessionStorage.getItem('chat_session_id')
    const loginData = { 
      username: username.value, 
      password: password.value,
      sessionId: sessionId // Send session ID to merge visitor data
    }
    
    const response = await login(loginData)
    if (response.data.code === 200) {
      // Clear visitor session ID after successful login
      if (sessionId) sessionStorage.removeItem('chat_session_id')
      
      sessionStorage.setItem('token', response.data.data.token)
      sessionStorage.setItem('userType', response.data.data.userType)
      sessionStorage.setItem('userId', response.data.data.userId)
      
      // Store user info
      const userType = response.data.data.userType
      if (userType === 3) {
        alert('管理员登录成功!')
        router.push('/admin/knowledge-base')
      } else {
        alert('登录成功!')
        router.push('/chat')
      }
    } else {
      error.value = response.data.message
    }
  } catch (err) {
    error.value = 'Login failed. Please try again.'
    console.error(err)
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  overflow-y: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}

.login-container {
  max-width: 400px;
  width: 100%;
  padding: 20px;
  border: 1px solid #ccc;
  border-radius: 5px;
  background: #fff;
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
  background-color: #007bff;
  color: white;
  border: none;
  cursor: pointer;
}
button:hover {
  background-color: #0056b3;
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
