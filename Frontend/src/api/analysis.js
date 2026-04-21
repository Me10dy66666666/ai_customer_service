import axios from 'axios'

const API_URL = '/api/admin/stats'

// 获取日统计数据 (概览)
export const getDailyStats = (date) => {
  return axios.get(`${API_URL}/daily`, {
    params: { date }
  })
}

// 获取趋势数据（含每日咨询量、每日平均满意度）
export const getTrendStats = (startDate, endDate) => {
  return axios.get(`${API_URL}/trend`, {
    params: { startDate, endDate }
  })
}

// 工单分析统计
export const getWorkOrderStats = (startDate, endDate) => {
  return axios.get(`${API_URL}/work-orders`, {
    params: { startDate, endDate }
  })
}

// 导出咨询报告（返回 CSV 文件流，前端触发下载）
export const exportReport = (startDate, endDate) => {
  return axios.get(`${API_URL}/export`, {
    params: { startDate, endDate },
    responseType: 'blob'
  })
}

// 转化率分析
export const getConversionStats = (startDate, endDate) => {
  return axios.get(`${API_URL}/conversion`, {
    params: { startDate, endDate }
  })
}

// 知识库效果分析
export const getKnowledgeBaseEffectStats = (startDate, endDate) => {
  return axios.get(`${API_URL}/knowledge-base-effect`, {
    params: { startDate, endDate }
  })
}

// 获取用户画像
export const getUserProfile = (userId) => {
  // 后端映射在 UserProfileController: /api/analysis/profile/{userId}
  return axios.get(`/api/analysis/profile/${userId}`)
}

export const buildUserProfile = (userId) => {
  // 后端映射在 UserProfileController: /api/analysis/profile/build/{userId}
  return axios.post(`/api/analysis/profile/build/${userId}`)
}

// 搜索用户画像
export const searchProfiles = (params) => {
  return axios.get('/api/analysis/profiles', { params })
}

// 合并用户画像
export const mergeProfiles = (sessionId, userId) => {
  return axios.post('/api/analysis/merge', null, {
    params: { sessionId, userId }
  })
}
