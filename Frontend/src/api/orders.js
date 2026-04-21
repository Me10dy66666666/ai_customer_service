import axios from 'axios'

const API_URL = '/api/orders'

export const syncOrders = (userId) => {
  return axios.post(`${API_URL}/sync/${userId}`)
}

export const getUserOrders = (userId) => {
  return axios.get(`${API_URL}/user/${userId}`)
}
