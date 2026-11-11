// 浏览器地理定位封装：Promise 化、优雅降级（不支持/拒绝均返回 null）
import { ref } from 'vue'

export function useGeolocation() {
  const status = ref('idle') // idle | locating | success | error
  const error = ref('')
  const coords = ref(null)

  // 返回 Promise<{lng,lat}|null>
  const locate = () => {
    return new Promise((resolve) => {
      if (!('geolocation' in navigator)) {
        status.value = 'error'
        error.value = '当前环境不支持定位'
        resolve(null)
        return
      }
      status.value = 'locating'
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          coords.value = { lng: pos.coords.longitude, lat: pos.coords.latitude }
          status.value = 'success'
          resolve(coords.value)
        },
        (err) => {
          status.value = 'error'
          error.value = err && err.message ? err.message : '定位失败'
          resolve(null)
        },
        { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 }
      )
    })
  }

  return { status, error, coords, locate }
}
