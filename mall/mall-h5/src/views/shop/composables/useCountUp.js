// 数字滚动动画：金额/数量从旧值平滑滚动到新值（ease-out 三次缓动）
import { ref, watch, computed, isRef } from 'vue'

/**
 * @param target 目标值：可以是 ref 或返回数值的 getter
 * @param opts   { decimals=0 保留小数位, duration=800 动画时长 ms }
 * @returns { display } 动画中的当前值 ref（配合 format 使用）
 */
export function useCountUp(target, { decimals = 0, duration = 800 } = {}) {
  const display = ref(0)
  const targetVal = isRef(target) ? target : computed(() => target())
  let rafId = null

  watch(
    targetVal,
    (val) => {
      const from = Number(display.value) || 0
      const to = Number(val) || 0
      if (from === to) {
        display.value = to
        return
      }
      if (rafId) cancelAnimationFrame(rafId)
      const start = performance.now()
      const step = (now) => {
        const p = Math.min((now - start) / duration, 1)
        const eased = 1 - Math.pow(1 - p, 3) // ease-out cubic
        display.value = from + (to - from) * eased
        if (p < 1) {
          rafId = requestAnimationFrame(step)
        } else {
          display.value = to
          rafId = null
        }
      }
      rafId = requestAnimationFrame(step)
    },
    { immediate: true }
  )

  return {
    display,
    // 按保留位格式化当前值
    format: (d = decimals) => Number(display.value).toFixed(d)
  }
}
