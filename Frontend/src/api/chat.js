import axios from 'axios'

const API_URL = 'http://localhost:8081/api/chat'

export const sendMessage = (payload) => {
  return axios.post(API_URL + '/send', payload)
}

export const getHistory = (sessionId) => {
  return axios.get(API_URL + '/history', {
    params: { sessionId }
  })
}
