import http from '@/core/axios'

export const syncOrders = (userId) => http.post(`/api/orders/sync/${userId}`)
export const getUserOrders = (userId) => http.get(`/api/orders/user/${userId}`)
