// 定位状态仓库：缓存用户经纬度，供店铺列表按距离排序复用
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useGeolocation } from '../composables/useGeolocation'

const LS_KEY = 'user-geo'

export const useLocationStore = defineStore('location', () => {
  const lng = ref(null)
  const lat = ref(null)
  const ready = ref(false)
  const { locate } = useGeolocation()

  // 启动时恢复上次缓存的坐标（自动按距离排序）
  const saved = localStorage.getItem(LS_KEY)
  if (saved) {
    try {
      const c = JSON.parse(saved)
      if (c && typeof c.lng === 'number' && typeof c.lat === 'number') {
        lng.value = c.lng
        lat.value = c.lat
        ready.value = true
      }
    } catch (e) {
      // 损坏则忽略，回落到默认（不传坐标）
    }
  }

  // 触发一次定位；成功则缓存并返回 true
  const locateNow = async () => {
    const c = await locate()
    if (c) {
      lng.value = c.lng
      lat.value = c.lat
      ready.value = true
      localStorage.setItem(LS_KEY, JSON.stringify(c))
      return true
    }
    return false
  }

  const hasCoords = () => lng.value != null && lat.value != null

  return { lng, lat, ready, locateNow, hasCoords }
})
