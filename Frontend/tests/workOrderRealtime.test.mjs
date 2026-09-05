import test from 'node:test'
import assert from 'node:assert/strict'
import { applyWorkOrderRealtimeEvent, chatMessageFromWorkOrderEvent } from '../src/domains/workorder/workOrderRealtime.js'

test('upserts work-order creation and applies async summary facts', () => {
  const orders = []
  assert.equal(applyWorkOrderRealtimeEvent({
    type: 'workorder_created', workOrderId: 7, title: '退款', status: 'pending'
  }, orders), true)
  assert.equal(orders.length, 1)
  assert.equal(applyWorkOrderRealtimeEvent({
    type: 'SUMMARY_READY', workOrderId: 7, priority: 'high', summary: '需要人工处理'
  }, orders), true)
  assert.deepEqual(orders[0], {
    id: 7, title: '退款', description: '', type: '', status: 'pending', createTime: '', result: null,
    priority: 'high', summary: '需要人工处理'
  })
})

test('normalizes only messages for the selected session', () => {
  assert.deepEqual(chatMessageFromWorkOrderEvent({ type: 'agent_msg', sessionId: 's1', content: '收到' }, 's1'), {
    role: 'agent', content: '收到'
  })
  assert.equal(chatMessageFromWorkOrderEvent({ type: 'agent_msg', sessionId: 's2', content: '无关' }, 's1'), null)
})
