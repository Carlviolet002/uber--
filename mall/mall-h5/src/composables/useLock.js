// 防重复提交锁：locked 期间 run() 直接返回 false；结束后 300ms 内同样拦截（高频点击场景：领券/抢购/购买）
import { ref } from 'vue'

export function useLock() {
  const locked = ref(false)

  const run = async (fn) => {
    if (locked.value) return false
    locked.value = true
    try {
      return await fn()
    } finally {
      setTimeout(() => {
        locked.value = false
      }, 300)
    }
  }

  return { locked, run }
}
