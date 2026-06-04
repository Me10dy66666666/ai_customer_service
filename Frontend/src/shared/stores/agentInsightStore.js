import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getMyDailyStats,
  getMySatisfaction,
  getMyTrend,
  getTeamRanking,
  getTeamAverage,
  getMyMonthlyStats,
  getSlaOverview,
  getSlaTrend,
  getTeamSlaRanking,
  getMyWorkOrderStats,
  getMyWorkOrderSatisfaction
} from '@/domains/analytics/agentInsightService'

export const useAgentInsightStore = defineStore('agentInsight', () => {
  const loading = ref(false)
  const dailyStats = ref(null)
  const satisfactionDist = ref(null)
  const trendStats = ref(null)
  const teamRanking = ref(null)
  const monthlyStats = ref(null)
  const slaOverview = ref(null)
  const slaTrend = ref(null)
  const slaTeamRanking = ref(null)
  const workOrderStats = ref(null)
  const teamAverage = ref(null)
  const woSatisfaction = ref(null)

  function fetchDailyStats(agentId, date) {
    return getMyDailyStats(agentId, date).then(res => {
      if (res.data.code === 200) dailyStats.value = res.data.data
    }).catch(() => { dailyStats.value = null })
  }

  function fetchSatisfaction(agentId, date) {
    return getMySatisfaction(agentId, date).then(res => {
      if (res.data.code === 200) satisfactionDist.value = res.data.data
    }).catch(() => { satisfactionDist.value = null })
  }

  function fetchTrend(agentId, startDate, endDate) {
    return getMyTrend(agentId, startDate, endDate).then(res => {
      if (res.data.code === 200) trendStats.value = res.data.data
    }).catch(() => { trendStats.value = null })
  }

  function fetchTeamRanking(agentId, startDate, endDate) {
    return getTeamRanking(agentId, startDate, endDate).then(res => {
      if (res.data.code === 200) teamRanking.value = res.data.data
    }).catch(() => { teamRanking.value = null })
  }

  function fetchMonthlyStats(agentId, startDate, endDate) {
    return getMyMonthlyStats(agentId, startDate, endDate).then(res => {
      if (res.data.code === 200) monthlyStats.value = res.data.data
    }).catch(() => { monthlyStats.value = null })
  }

  function fetchSlaOverview(agentId, startDate, endDate) {
    return getSlaOverview(agentId, startDate, endDate).then(res => {
      if (res.data.code === 200) slaOverview.value = res.data.data
    }).catch(() => { slaOverview.value = null })
  }

  function fetchSlaTrend(agentId, startDate, endDate) {
    return getSlaTrend(agentId, startDate, endDate).then(res => {
      if (res.data.code === 200) slaTrend.value = res.data.data
    }).catch(() => { slaTrend.value = null })
  }

  function fetchTeamSlaRanking(startDate, endDate) {
    return getTeamSlaRanking(startDate, endDate).then(res => {
      if (res.data.code === 200) slaTeamRanking.value = res.data.data
    }).catch(() => { slaTeamRanking.value = null })
  }

  function fetchTeamAverage(agentId, startDate, endDate) {
    return getTeamAverage(agentId, startDate, endDate).then(res => {
      if (res.data.code === 200) teamAverage.value = res.data.data
    }).catch(() => { teamAverage.value = null })
  }

  function fetchWorkOrderStats(agentId, startDate, endDate) {
    return getMyWorkOrderStats(agentId, startDate, endDate).then(res => {
      if (res.data.code === 200) {
        workOrderStats.value = res.data.data
        if (res.data.data?._error) {
          console.error('[AgentInsight] 工单统计服务异常:', res.data.data._error)
        }
      } else {
        console.error('[AgentInsight] 工单统计API错误:', res.data.code, res.data.message)
        workOrderStats.value = null
      }
    }).catch(err => {
      if (err.response) {
        console.error('[AgentInsight] 工单统计HTTP' + err.response.status + ':', err.response.data)
      } else {
        console.error('[AgentInsight] 工单统计请求失败:', err.message)
      }
      workOrderStats.value = null
    })
  }

  function fetchWoSatisfaction(agentId, startDate, endDate, workOrderType) {
    return getMyWorkOrderSatisfaction(agentId, startDate, endDate, workOrderType).then(res => {
      if (res.data.code === 200) {
        woSatisfaction.value = res.data.data
        if (res.data.data?._error) {
          console.error('[AgentInsight] 工单满意度服务异常:', res.data.data._error)
        }
      } else {
        console.error('[AgentInsight] 工单满意度API错误:', res.data.code, res.data.message)
        woSatisfaction.value = null
      }
    }).catch(err => {
      if (err.response) {
        console.error('[AgentInsight] 工单满意度HTTP' + err.response.status + ':', err.response.data)
      } else {
        console.error('[AgentInsight] 工单满意度请求失败:', err.message)
      }
      woSatisfaction.value = null
    })
  }

  async function fetchAll(agentId, today, monthStart, monthEnd, trendStart, trendEnd) {
    console.log('[AgentInsight] fetchAll 参数:', { agentId, today, monthStart, monthEnd, trendStart, trendEnd })
    loading.value = true
    try {
      await Promise.all([
        fetchDailyStats(agentId, today),
        fetchSatisfaction(agentId, today),
        fetchTrend(agentId, trendStart, trendEnd),
        fetchTeamRanking(agentId, trendStart, trendEnd),
        fetchMonthlyStats(agentId, monthStart, monthEnd),
        fetchTeamAverage(agentId, trendStart, trendEnd),
        fetchWorkOrderStats(agentId, monthStart, monthEnd),
        fetchWoSatisfaction(agentId, monthStart, monthEnd, 'all'),
      ])
    } finally {
      loading.value = false
    }
  }

  return {
    loading, dailyStats, satisfactionDist, trendStats,
    teamRanking, monthlyStats, slaOverview, slaTrend, slaTeamRanking, workOrderStats, teamAverage, woSatisfaction,
    fetchDailyStats, fetchSatisfaction, fetchTrend,
    fetchTeamRanking, fetchMonthlyStats, fetchAll,
    fetchSlaOverview, fetchSlaTrend, fetchTeamSlaRanking, fetchTeamAverage, fetchWorkOrderStats, fetchWoSatisfaction
  }
})
