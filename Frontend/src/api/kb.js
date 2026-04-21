import axios from 'axios'

const API_URL = '/api/kb'

export const uploadDocument = (formData) => {
  return axios.post(`${API_URL}/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      // 'Authorization': `Bearer ${localStorage.getItem('token')}` // Uncomment if auth is needed
    }
  })
}

export const getDocuments = () => {
  return axios.get(`${API_URL}/list`)
}

export const deleteDocument = (id) => {
  return axios.delete(`${API_URL}/${id}`)
}

export const updateDocumentStatus = (id, status) => {
  return axios.put(`${API_URL}/${id}/status`, null, {
    params: { status }
  })
}
