import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getMyReviewStats,
  getMyReviewTrend,
  getMyMonthlyReview,
  getKbHealth,
  getDocStatusDist,
  getKbHealthTrend,
  getKbEffectTrend,
  getHotSearchWords,
  getZeroResultWords,
  getRecentReviews
} from '@/domains/analytics/kbAdminInsightService'

export const useKbAdminInsightStore = defineStore('kbAdminInsight', () => {
  const loading = ref(false)
  const reviewStats = ref(null)
  const reviewTrend = ref(null)
  const monthlyReview = ref(null)
  const kbHealth = ref(null)
  const docStatusDist = ref(null)
  const kbHealthTrend = ref(null)
  const kbEffectTrend = ref(null)
  const hotSearchWords = ref(null)
  const zeroResultWords = ref(null)
  const recentReviews = ref(null)

  function fetchReviewStats(reviewedBy, date) {
    return getMyReviewStats(reviewedBy, date).then(res => {
      if (res.data.code === 200) reviewStats.value = res.data.data
    }).catch(() => { reviewStats.value = null })
  }

  function fetchReviewTrend(reviewedBy, startDate, endDate) {
    return getMyReviewTrend(reviewedBy, startDate, endDate).then(res => {
      if (res.data.code === 200) reviewTrend.value = res.data.data
    }).catch(() => { reviewTrend.value = null })
  }

  function fetchMonthlyReview(reviewedBy, startDate, endDate) {
    return getMyMonthlyReview(reviewedBy, startDate, endDate).then(res => {
      if (res.data.code === 200) monthlyReview.value = res.data.data
    }).catch(() => { monthlyReview.value = null })
  }

  function fetchKbHealth() {
    return getKbHealth().then(res => {
      if (res.data.code === 200) kbHealth.value = res.data.data
    }).catch(() => { kbHealth.value = null })
  }

  function fetchDocStatusDist() {
    return getDocStatusDist().then(res => {
      if (res.data.code === 200) docStatusDist.value = res.data.data
    }).catch(() => { docStatusDist.value = null })
  }

  function fetchKbHealthTrend(startDate, endDate) {
    return getKbHealthTrend(startDate, endDate).then(res => {
      if (res.data.code === 200) kbHealthTrend.value = res.data.data
    }).catch(() => { kbHealthTrend.value = null })
  }

  function fetchKbEffectTrend(startDate, endDate) {
    return getKbEffectTrend(startDate, endDate).then(res => {
      if (res.data.code === 200) kbEffectTrend.value = res.data.data
    }).catch(() => { kbEffectTrend.value = null })
  }

  function fetchHotSearchWords(startDate, endDate) {
    return getHotSearchWords(startDate, endDate).then(res => {
      if (res.data.code === 200) hotSearchWords.value = res.data.data
    }).catch(() => { hotSearchWords.value = null })
  }

  function fetchZeroResultWords() {
    return getZeroResultWords().then(res => {
      if (res.data.code === 200) zeroResultWords.value = res.data.data
    }).catch(() => { zeroResultWords.value = null })
  }

  function fetchRecentReviews() {
    return getRecentReviews().then(res => {
      if (res.data.code === 200) recentReviews.value = res.data.data
    }).catch(() => { recentReviews.value = null })
  }

  async function fetchAll(reviewedBy, today, monthStart, monthEnd, trendStart, trendEnd) {
    loading.value = true
    try {
      await Promise.all([
        fetchReviewStats(reviewedBy, today),
        fetchReviewTrend(reviewedBy, trendStart, trendEnd),
        fetchMonthlyReview(reviewedBy, monthStart, monthEnd),
        fetchKbHealth(),
        fetchDocStatusDist(),
        fetchKbHealthTrend(trendStart, trendEnd),
        fetchKbEffectTrend(trendStart, trendEnd),
        fetchHotSearchWords(trendStart, trendEnd),
        fetchZeroResultWords(),
        fetchRecentReviews()
      ])
    } finally {
      loading.value = false
    }
  }

  return {
    loading, reviewStats, reviewTrend, monthlyReview,
    kbHealth, docStatusDist, kbHealthTrend, kbEffectTrend,
    hotSearchWords, zeroResultWords, recentReviews,
    fetchReviewStats, fetchReviewTrend, fetchMonthlyReview,
    fetchKbHealth, fetchDocStatusDist,
    fetchKbHealthTrend, fetchKbEffectTrend,
    fetchHotSearchWords, fetchZeroResultWords, fetchRecentReviews,
    fetchAll
  }
})
