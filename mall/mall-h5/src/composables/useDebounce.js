// 300ms 防抖：搜索联想、FAQ 过滤等输入场景共用
import { ref } from 'vue'

export function useDebounce(fn, delay = 300) {
  let timer = null
  const pending = ref(false)

  const debounced = (...args) => {
    pending.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      pending.value = false
      fn(...args)
    }, delay)
  }

  const cancel = () => {
    if (timer) clearTimeout(timer)
    pending.value = false
  }

  return { debounced, cancel, pending }
}
