import http from '@/core/axios'

export const getDailyStats = (startDate, endDate) =>
  http.get('/api/admin/stats/daily', { params: { startDate, endDate } })

export const getTrendStats = (startDate, endDate) =>
  http.get('/api/admin/stats/trend', { params: { startDate, endDate } })

export const getWorkOrderStats = (startDate, endDate) =>
  http.get('/api/admin/stats/work-orders', { params: { startDate, endDate } })

export const exportReport = (startDate, endDate) =>
  http.get('/api/admin/stats/export', {
    params: { startDate, endDate },
    responseType: 'blob'
  })

export const getConversionStats = (startDate, endDate) =>
  http.get('/api/admin/stats/conversion', { params: { startDate, endDate } })

export const getKnowledgeBaseEffectStats = (startDate, endDate) =>
  http.get('/api/admin/stats/knowledge-base-effect', { params: { startDate, endDate } })

export const getAiResolutionRateStats = (startDate, endDate) =>
  http.get('/api/admin/stats/ai-resolution-rate', { params: { startDate, endDate } })

export const getSlaOverview = (startDate, endDate) =>
  http.get('/api/admin/stats/sla/overview', { params: { startDate, endDate } })

export const getSlaTrendByBizTag = (startDate, endDate) =>
  http.get('/api/admin/stats/sla/trend-by-biz-tag', { params: { startDate, endDate } })

export const getSlaAgentRanking = (startDate, endDate) =>
  http.get('/api/admin/stats/sla/agent-ranking', { params: { startDate, endDate } })
