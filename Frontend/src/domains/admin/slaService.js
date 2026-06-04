import http from '@/core/axios'

export function getSlaConfigs() {
  return http.get('/api/sla-config')
}

export function getSlaConfig(id) {
  return http.get(`/api/sla-config/${id}`)
}

export function createSlaConfig(config) {
  return http.post('/api/sla-config', config)
}

export function updateSlaConfig(id, config) {
  return http.put(`/api/sla-config/${id}`, config)
}
