// 用户状态仓库：登录态持久化到 localStorage，多端（用户/商家/骑手/管理员）共用
// 多角色支持：userInfo.roles 为位掩码（USER=1/SHOP=2/RIDER=4/ADMIN=8），roleNames 为角色名数组
import { defineStore } from 'pinia'
import api from '../api'

// 角色名 → 位掩码
const ROLE_BIT = { USER: 1, SHOP: 2, RIDER: 4, ADMIN: 8 }
// 单角色位掩码 → 首页
const HOME_BY_BIT = { 1: '/user/home', 2: '/shop/home', 4: '/rider/tasks', 8: '/admin' }

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || '{}')
  }),
  getters: {
    // 角色位掩码（缺省 0，未登录）
    roles: (s) => s.userInfo.roles || 0,
    // 兼容字段：legacy 单值角色（取 roleNames 第一个或 role）
    role: (s) => s.userInfo.role || (s.userInfo.roleNames && s.userInfo.roleNames[0]) || '',
    isLogin: (s) => !!s.token,
    // 角色中文名：USER 用户 / SHOP 商家 / RIDER 骑手 / ADMIN 管理员
    roleName: (s) => {
      const map = { USER: '用户', SHOP: '商家', RIDER: '骑手', ADMIN: '管理员' }
      const names = s.userInfo.roleNames || (s.userInfo.role ? [s.userInfo.role] : [])
      return names.map((n) => map[n] || n).join('/') || '用户'
    },
    // 判断是否拥有某角色（位掩码）
    hasRole: (s) => (name) => {
      const bit = ROLE_BIT[name]
      if (!bit) return false
      return (s.userInfo.roles || 0) & bit ? true : false
    }
  },
  actions: {
    // 按位掩码返回首页：单一角色直达对应端；多角色回门户多选
    homeByRoles() {
      const mask = this.roles
      if (!mask) return '/portal'
      const bits = [8, 4, 2, 1].filter((b) => (mask & b) === b)
      if (bits.length > 1) return '/portal'
      return HOME_BY_BIT[bits[0]] || '/portal'
    },
    // 保存登录态到 state 与 localStorage
    setLogin({ token, userInfo }) {
      this.token = token
      this.userInfo = userInfo
      localStorage.setItem('token', token)
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    },
    // 退出登录：清空本地登录态
    logout() {
      this.token = ''
      this.userInfo = {}
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    },
    // 用户登录：调用登录接口并保存登录态，返回用户信息
    async loginAction(form) {
      const data = await api.login(form)
      this.setLogin({ token: data.token, userInfo: data })
      return data
    }
  }
})
