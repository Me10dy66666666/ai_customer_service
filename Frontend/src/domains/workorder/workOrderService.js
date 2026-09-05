// Compatibility surface for existing stores while the API boundary moves to TypeScript.
export {
  createWorkOrder,
  getWorkOrders,
  getWorkOrder,
  getUnassignedWorkOrders,
  updateWorkOrderStatus,
  claimWorkOrder,
  replyWorkOrder,
  transferWorkOrder,
  pauseSla,
  resumeSla
} from './workOrderApi'
