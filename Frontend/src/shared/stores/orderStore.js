import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { syncOrders, getUserOrders } from '@/domains/order/orderService'
import { useAuthStore } from '@/shared/stores/authStore'

export const useOrderStore = defineStore('order', () => {
  const orders = ref([])
  const loading = ref(false)
  const syncing = ref(false)

  const orderCount = computed(() => orders.value.length)

  const fetchOrders = async () => {
    const auth = useAuthStore()
    if (!auth.userId) return

    loading.value = true
    try {
      const res = await getUserOrders(auth.userId)
      if (res.data.code === 200) {
        orders.value = res.data.data || []
      }
    } catch (err) {
      console.error('Failed to fetch orders:', err)
    } finally {
      loading.value = false
    }
  }

  const sync = async () => {
    const auth = useAuthStore()
    if (!auth.userId) return { success: false, message: '未登录' }

    syncing.value = true
    try {
      const res = await syncOrders(auth.userId)
      if (res.data.code === 200) {
        orders.value = res.data.data || []
        return { success: true }
      }
      return { success: false, message: res.data.message }
    } catch (err) {
      console.error('Sync failed:', err)
      return { success: false, message: '同步失败' }
    } finally {
      syncing.value = false
    }
  }

  return {
    orders,
    loading,
    syncing,
    orderCount,
    fetchOrders,
    sync
  }
})
