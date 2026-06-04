import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUserProfile,
  buildUserProfile,
  searchProfiles,
  mergeProfiles
} from '@/domains/userprofile/userProfileService'

export const useUserProfileStore = defineStore('userprofile', () => {
  const currentProfile = ref(null)
  const profiles = ref([])
  const loading = ref(false)
  const building = ref(false)

  const fetchProfile = async (userId) => {
    loading.value = true
    try {
      const res = await getUserProfile(userId)
      if (res.data.code === 200) {
        currentProfile.value = res.data.data
        return res.data.data
      }
      return null
    } catch (err) {
      console.error('Fetch profile failed:', err)
      return null
    } finally {
      loading.value = false
    }
  }

  const buildProfile = async (userId) => {
    building.value = true
    try {
      const res = await buildUserProfile(userId)
      if (res.data.code === 200) {
        currentProfile.value = res.data.data
        return { success: true, data: res.data.data }
      }
      return { success: false, message: res.data.message }
    } catch (err) {
      console.error('Build profile failed:', err)
      return { success: false, message: '构建画像失败' }
    } finally {
      building.value = false
    }
  }

  const search = async (params) => {
    try {
      const res = await searchProfiles(params)
      if (res.data.code === 200) {
        profiles.value = res.data.data || []
        return profiles.value
      }
      return []
    } catch (err) {
      console.error('Search profiles failed:', err)
      return []
    }
  }

  const merge = async (sessionId, userId) => {
    try {
      const res = await mergeProfiles(sessionId, userId)
      if (res.data.code === 200) {
        return { success: true, data: res.data.data }
      }
      return { success: false, message: res.data.message }
    } catch (err) {
      console.error('Merge profiles failed:', err)
      return { success: false, message: '合并失败' }
    }
  }

  return {
    currentProfile,
    profiles,
    loading,
    building,
    fetchProfile,
    buildProfile,
    search,
    merge
  }
})
