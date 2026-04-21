import axios from 'axios'

const API_URL = '/api/work-orders'

// 创建工单
export const createWorkOrder = (workOrder) => {
    return axios.post(API_URL, workOrder)
}

// 获取工单列表 (可选 userId)
export const getWorkOrders = (userId) => {
    const params = userId ? { userId } : {}
    return axios.get(API_URL, { params })
}

// 获取单个工单详情
export const getWorkOrder = (id) => {
    return axios.get(`${API_URL}/${id}`)
}

// 更新工单状态
export const updateWorkOrderStatus = (id, status, handlerId, result) => {
    return axios.put(`${API_URL}/${id}/status`, {
        status,
        handlerId,
        result
    })
}
