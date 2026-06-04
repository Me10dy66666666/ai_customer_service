import http from '@/core/axios'

export const login = (user) => http.post('/api/auth/login', user)
export const register = (user) => http.post('/api/auth/register', user)
