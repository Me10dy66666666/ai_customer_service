import http from '@/core/axios'

export function getMyDailyStats(agentId, date) {
  return http.get('/api/agent/stats/mine/daily', { params: { agentId, date } })
}

export function getMySatisfaction(agentId, date) {
  return http.get('/api/agent/stats/mine/satisfaction', { params: { agentId, date } })
}

export function getMyTrend(agentId, startDate, endDate) {
  return http.get('/api/agent/stats/mine/trend', { params: { agentId, startDate, endDate } })
}

export function getTeamRanking(agentId, startDate, endDate) {
  return http.get('/api/agent/stats/team/ranking', { params: { agentId, startDate, endDate } })
}

export function getTeamAverage(agentId, startDate, endDate) {
  return http.get('/api/agent/stats/team/average', { params: { agentId, startDate, endDate } })
}

export function getMyMonthlyStats(agentId, startDate, endDate) {
  return http.get('/api/agent/stats/mine/monthly', { params: { agentId, startDate, endDate } })
}

export function getSlaOverview(agentId, startDate, endDate) {
  return http.get('/api/agent/stats/sla/overview', { params: { agentId, startDate, endDate } })
}

export function getSlaTrend(agentId, startDate, endDate) {
  return http.get('/api/agent/stats/sla/trend', { params: { agentId, startDate, endDate } })
}

export function getTeamSlaRanking(startDate, endDate) {
  return http.get('/api/agent/stats/sla/team-ranking', { params: { startDate, endDate } })
}

export function getMyWorkOrderStats(agentId, startDate, endDate) {
  return http.get('/api/agent/stats/mine/workorder', { params: { agentId, startDate, endDate } })
}

export function getMyWorkOrderSatisfaction(agentId, startDate, endDate, workOrderType = 'all') {
  return http.get('/api/agent/stats/mine/workorder/satisfaction', { params: { agentId, startDate, endDate, workOrderType } })
}
