import http from '@/core/axios'

export function getMyReviewStats(reviewedBy, date) {
  return http.get('/api/knowledge/stats/reviewer/daily', { params: { reviewedBy, date } })
}

export function getMyReviewTrend(reviewedBy, startDate, endDate) {
  return http.get('/api/knowledge/stats/reviewer/trend', { params: { reviewedBy, startDate, endDate } })
}

export function getMyMonthlyReview(reviewedBy, startDate, endDate) {
  return http.get('/api/knowledge/stats/reviewer/monthly', { params: { reviewedBy, startDate, endDate } })
}

export function getKbHealth() {
  return http.get('/api/knowledge/stats/kb-health')
}

export function getDocStatusDist() {
  return http.get('/api/knowledge/stats/doc-status-dist')
}

export function getKbHealthTrend(startDate, endDate) {
  return http.get('/api/knowledge/stats/kb-health-trend', { params: { startDate, endDate } })
}

export function getKbEffectTrend(startDate, endDate) {
  return http.get('/api/knowledge/stats/kb-effect-trend', { params: { startDate, endDate } })
}

export function getHotSearchWords(startDate, endDate, limit = 10) {
  return http.get('/api/knowledge/stats/hot-search-words', { params: { startDate, endDate, limit } })
}

export function getZeroResultWords(limit = 10) {
  return http.get('/api/knowledge/stats/zero-result-words', { params: { limit } })
}

export function getRecentReviews(limit = 5) {
  return http.get('/api/knowledge/stats/recent-reviews', { params: { limit } })
}
