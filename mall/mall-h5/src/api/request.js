// 统一 axios 请求封装：token 注入 + 响应体 {code,msg,data} 统一处理
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 创建 axios 实例（走 Vite 代理 /api → http://localhost:8080）
const request = axios.create({
  baseURL: '',
  timeout: 10000
})

// 请求拦截器：localStorage 存在 token 则附加 Authorization 头
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 401 兜底：清登录态 + 提示 + 跳门户登录页。
// 防抖：同一时刻多个并发请求都 401 时只处理一次，避免连环弹窗与重复导航。
let lastUnauthorizedAt = 0
function handleUnauthorized(msg) {
  const now = Date.now()
  if (now - lastUnauthorizedAt < 3000) return
  lastUnauthorizedAt = now
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  ElMessage.error(msg || '登录已过期，请重新登录')
  const path = router.currentRoute.value.path
  if (path !== '/portal' && path !== '/login') {
    router.push('/portal')
  }
}

// 响应拦截器：统一处理 {code,msg,data}
request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body.code !== 200) {
      // 业务码 401（如登录接口密码错误）：清除本地登录态并跳登录页
      if (body.code === 401) {
        handleUnauthorized(body.msg)
      } else if (!(res.config && res.config.silent)) {
        ElMessage.error(body.msg || '请求失败')
      }
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    // 成功时直接返回 data 部分
    return body.data
  },
  (err) => {
    // HTTP 401：网关 JWT 校验失败（token 缺失/过期/伪造），清登录态并跳门户
    if (err.response && err.response.status === 401) {
      handleUnauthorized()
      return Promise.reject(err)
    }
    // silent 配置：静默失败不弹错误提示（如消息页 real→mock 降级探测）
    if (!(err.config && err.config.silent)) {
      ElMessage.error(err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default request
