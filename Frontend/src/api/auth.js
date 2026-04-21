import axios from 'axios'

const API_URL = 'http://localhost:8081/api/auth'

export const login = (user) => {
  return axios.post(API_URL + '/login', user)
}

export const register = (user) => {
  return axios.post(API_URL + '/register', user)
}
