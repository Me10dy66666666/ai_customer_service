import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCategoryList } from '@/domains/knowledge/knowledgeService'
import http from '@/core/axios'

export const useCategoryStore = defineStore('category', () => {
  const categoryNames = ref([])

  async function fetchCategoryNames() {
    try {
      const res = await getCategoryList()
      if (res.data.code === 200) {
        categoryNames.value = res.data.data || []
      }
    } catch { /* API失败保留旧数据 */ }
  }

  async function addCategory(name) {
    await http.post('/api/knowledge/categories', { name })
    await fetchCategoryNames()
  }

  async function removeCategory(name) {
    await http.delete(`/api/knowledge/categories/${encodeURIComponent(name)}`)
    await fetchCategoryNames()
  }

  return { categoryNames, fetchCategoryNames, addCategory, removeCategory }
})
