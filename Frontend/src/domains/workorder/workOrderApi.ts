import http from '@/core/axios'

export interface WorkOrder {
  id: string | number
  title?: string
  description?: string
  type?: string
  status?: string
  priority?: string
  tags?: string
  summary?: string
  sessionId?: string | null
  [key: string]: unknown
}

export interface ApiEnvelope<T> {
  code: number
  data: T
  msg?: string
  message?: string
}

export interface WorkOrderPage {
  list: WorkOrder[]
  total: number
  page: number
  size?: number
}

export type WorkOrderListPayload = WorkOrder[] | WorkOrderPage

export interface WorkOrderRealtimeEvent {
  type: string
  workOrderId?: string | number
  sessionId?: string
  content?: string
  [key: string]: unknown
}

type Identifier = string | number
type NullableIdentifier = Identifier | null | undefined

export const createWorkOrder = (workOrder: Record<string, unknown>) =>
  http.post<ApiEnvelope<WorkOrder>>('/api/work-orders', workOrder)

export const getWorkOrders = (
  userId: NullableIdentifier,
  page = 1,
  size = 50,
  handlerId: NullableIdentifier = null
) => {
  const params: Record<string, string | number> = { page, size }
  if (userId !== null && userId !== undefined && userId !== '') params.userId = userId
  if (handlerId !== null && handlerId !== undefined && handlerId !== '') params.handlerId = handlerId
  return http.get<ApiEnvelope<WorkOrderListPayload>>('/api/work-orders', { params })
}

export const getWorkOrder = (id: Identifier) =>
  http.get<ApiEnvelope<WorkOrder>>(`/api/work-orders/${id}`)

export const getUnassignedWorkOrders = () =>
  http.get<ApiEnvelope<WorkOrder[]>>('/api/work-orders/unassigned')

export const updateWorkOrderStatus = (
  id: Identifier,
  status: string,
  handlerId: NullableIdentifier,
  result: unknown
) => http.put<ApiEnvelope<WorkOrder>>(`/api/work-orders/${id}/status`, { status, handlerId, result })

export const claimWorkOrder = (id: Identifier, handlerId: Identifier) =>
  http.post<ApiEnvelope<{ claimed: boolean }>>(`/api/work-orders/${id}/claim`, null, { params: { handlerId } })

export const replyWorkOrder = (id: Identifier, content: string, agentId: Identifier) =>
  http.post<ApiEnvelope<unknown>>(`/api/work-orders/${id}/reply`, { content, agentId })

export const transferWorkOrder = (id: Identifier, targetHandlerId: Identifier, reason: string) =>
  http.post<ApiEnvelope<unknown>>(`/api/work-orders/${id}/transfer`, { targetHandlerId, reason })

export const pauseSla = (id: Identifier, reason: string, agentId: Identifier) =>
  http.post<ApiEnvelope<unknown>>(`/api/work-orders/${id}/pause-sla`, { reason, agentId })

export const resumeSla = (id: Identifier, agentId: Identifier) =>
  http.post<ApiEnvelope<unknown>>(`/api/work-orders/${id}/resume-sla`, { agentId })
