import axios from 'axios'

let router = null
export const setRouter = (r) => { router = r }

const http = axios.create({
  baseURL: '',
  timeout: 30000
})

http.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('roles')
      sessionStorage.removeItem('userId')
      if (router) {
        router.push('/login')
      } else {
        globalThis.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default http
