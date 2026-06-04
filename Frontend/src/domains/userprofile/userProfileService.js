import http from '@/core/axios'

export const getUserProfile = (userId) => http.get(`/api/analysis/profile/${userId}`)
export const buildUserProfile = (userId) => http.post(`/api/analysis/profile/build/${userId}`)
export const searchProfiles = (params) => http.get('/api/analysis/profiles', { params })
export const mergeProfiles = (sessionId, userId) =>
  http.post('/api/analysis/merge', null, { params: { sessionId, userId } })
