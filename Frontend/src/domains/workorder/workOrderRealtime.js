/**
 * Pure adapter for work-order events received from the agent WebSocket.
 * Keeping this seam free of Vue and transport details makes reconnect replay
 * deterministic and keeps the WorkOrder view focused on presentation.
 */

const eventTypes = new Set(['workorder_created', 'workorder_reply', 'SUMMARY_READY', 'summary_ready', 'sla_alert'])

export function isWorkOrderRealtimeEvent(message) {
  return Boolean(message && eventTypes.has(message.type))
}

function eventWorkOrderId(message) {
  return message.workOrderId ?? message.work_order_id ?? null
}

function findWorkOrder(workOrders, id) {
  return workOrders.find(order => String(order.id) === String(id))
}

/**
 * Apply one server event to the local list. Returns true when the list or its
 * metadata changed and the caller should surface an unread/update signal.
 */
export function applyWorkOrderRealtimeEvent(message, workOrders) {
  if (!isWorkOrderRealtimeEvent(message)) return false
  const id = eventWorkOrderId(message)
  if (id === null || id === undefined) return message.type === 'sla_alert'

  if (message.type === 'workorder_created') {
    const existing = findWorkOrder(workOrders, id)
    if (existing) return false
    workOrders.push({
      id,
      title: message.title || '',
      description: message.description || '',
      type: message.woType || message.typeName || '',
      status: message.status || 'pending',
      createTime: message.createTime || '',
      result: null
    })
    return true
  }

  const existing = findWorkOrder(workOrders, id)
  if (!existing) return true

  if (message.type === 'workorder_reply') {
    if (message.result !== undefined) existing.result = message.result
    return true
  }

  if (message.type === 'SUMMARY_READY' || message.type === 'summary_ready') {
    const fields = ['priority', 'tags', 'summary', 'bizTag', 'emotionLevel', 'dispatchConfidence']
    for (const field of fields) {
      if (message[field] !== undefined) existing[field] = message[field]
    }
    return true
  }

  return true
}

export function chatMessageFromWorkOrderEvent(message, sessionId) {
  if (!message || String(message.sessionId ?? '') !== String(sessionId ?? '')) return null
  if (message.type === 'workorder_reply' || message.type === 'agent_msg') {
    return { role: 'agent', content: message.content || '' }
  }
  if (message.type === 'user_msg_sent' || message.type === 'user_msg') {
    return { role: 'user', content: message.content || '' }
  }
  return null
}
