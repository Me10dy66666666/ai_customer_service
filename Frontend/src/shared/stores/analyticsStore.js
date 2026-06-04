import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getDailyStats,
  getTrendStats,
  getWorkOrderStats,
  exportReport,
  getConversionStats,
  getKnowledgeBaseEffectStats,
  getAiResolutionRateStats,
  getSlaOverview,
  getSlaTrendByBizTag,
  getSlaAgentRanking
} from '@/domains/analytics/analyticsService'

export const useAnalyticsStore = defineStore('analytics', () => {
  const loading = ref(false)
  const dailyStats = ref(null)
  const trendStats = ref(null)
  const workOrderStats = ref(null)
  const conversionStats = ref(null)
  const kbEffectStats = ref(null)
  const aiResolutionStats = ref(null)
  const slaOverview = ref(null)
  const slaBizTagTrend = ref(null)
  const slaAgentRanking = ref(null)
  const exporting = ref(false)

  const fetchDailyStats = async (startDate, endDate) => {
    loading.value = true
    try {
      const res = await getDailyStats(startDate, endDate)
      if (res.data.code === 200) {
        dailyStats.value = res.data.data
      }
    } catch {
      dailyStats.value = null
    } finally {
      loading.value = false
    }
  }

  const fetchTrendStats = async (startDate, endDate) => {
    try {
      const res = await getTrendStats(startDate, endDate)
      if (res.data.code === 200) {
        trendStats.value = res.data.data
      }
    } catch {
      trendStats.value = null
    }
  }

  const fetchWorkOrderStats = async (startDate, endDate) => {
    try {
      const res = await getWorkOrderStats(startDate, endDate)
      if (res.data.code === 200) {
        workOrderStats.value = res.data.data
      }
    } catch {
      workOrderStats.value = null
    }
  }

  const doExport = async (startDate, endDate) => {
    exporting.value = true
    try {
      const res = await exportReport(startDate, endDate)
      const url = globalThis.URL.createObjectURL(new Blob([res.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `report-${startDate}-${endDate}.csv`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      globalThis.URL.revokeObjectURL(url)
    } catch {
      exporting.value = false
      return
    } finally {
      exporting.value = false
    }
  }

  const fetchConversionStats = async (startDate, endDate) => {
    try {
      const res = await getConversionStats(startDate, endDate)
      if (res.data.code === 200) {
        conversionStats.value = res.data.data
      }
    } catch {
      conversionStats.value = null
    }
  }

  const fetchKbEffectStats = async (startDate, endDate) => {
    try {
      const res = await getKnowledgeBaseEffectStats(startDate, endDate)
      if (res.data.code === 200) {
        kbEffectStats.value = res.data.data
      }
    } catch {
      kbEffectStats.value = null
    }
  }

  const fetchAiResolutionStats = async (startDate, endDate) => {
    try {
      const res = await getAiResolutionRateStats(startDate, endDate)
      if (res.data.code === 200) {
        aiResolutionStats.value = res.data.data
      }
    } catch {
      aiResolutionStats.value = null
    }
  }

  const fetchSlaOverview = async (startDate, endDate) => {
    try {
      const res = await getSlaOverview(startDate, endDate)
      if (res.data.code === 200) {
        slaOverview.value = res.data.data
      }
    } catch {
      slaOverview.value = null
    }
  }

  const fetchSlaTrendByBizTag = async (startDate, endDate) => {
    try {
      const res = await getSlaTrendByBizTag(startDate, endDate)
      if (res.data.code === 200) {
        slaBizTagTrend.value = res.data.data
      }
    } catch {
      slaBizTagTrend.value = null
    }
  }

  const fetchSlaAgentRanking = async (startDate, endDate) => {
    try {
      const res = await getSlaAgentRanking(startDate, endDate)
      if (res.data.code === 200) {
        slaAgentRanking.value = res.data.data
      }
    } catch {
      slaAgentRanking.value = null
    }
  }

  const fetchAll = async (startDate, endDate) => {
    await Promise.all([
      fetchDailyStats(startDate, endDate),
      fetchTrendStats(startDate, endDate),
      fetchWorkOrderStats(startDate, endDate),
      fetchConversionStats(startDate, endDate),
      fetchKbEffectStats(startDate, endDate),
      fetchAiResolutionStats(startDate, endDate)
    ])
  }

  const fetchSlaAll = async (startDate, endDate) => {
    await Promise.all([
      fetchSlaOverview(startDate, endDate),
      fetchSlaTrendByBizTag(startDate, endDate),
      fetchSlaAgentRanking(startDate, endDate)
    ])
  }

  return {
    loading,
    exporting,
    dailyStats,
    trendStats,
    workOrderStats,
    conversionStats,
    kbEffectStats,
    aiResolutionStats,
    slaOverview,
    slaBizTagTrend,
    slaAgentRanking,
    fetchDailyStats,
    fetchTrendStats,
    fetchWorkOrderStats,
    fetchConversionStats,
    fetchKbEffectStats,
    fetchAiResolutionStats,
    fetchSlaOverview,
    fetchSlaTrendByBizTag,
    fetchSlaAgentRanking,
    fetchAll,
    fetchSlaAll,
    doExport
  }
})
