import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@/styles/design-tokens.css'
import pinia from './core/pinia'
import router from './core/router'
import { setRouter } from './core/axios'
import App from './App.vue'

setRouter(router)

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
