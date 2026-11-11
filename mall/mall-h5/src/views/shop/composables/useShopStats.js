// 商家端统计聚合逻辑：概览数字/收入/订单计数统一从这里算，后续接后端统计接口时只改本层
import { useUserStore } from '../../../stores/user'
import { getShopOrders, shopList, shopMine } from '@/api'

/**
 * 解析商家 shopId：优先调用后端 /api/shop/mine，按登录账号 ownerUserId 取真实店铺
 * （后端懒建档，根治「门户注册商家后进入报 商家不存在」）。
 * 失败（mock/接口未就绪）时回退旧解析逻辑：登录信息 shopId → 昵称匹配店铺名 → userId-2 兜底 → 1。
 */
export async function resolveShopId() {
  const userStore = useUserStore()
  const info = userStore.userInfo || {}
  try {
    const mine = await shopMine()
    if (mine && mine.id) return Number(mine.id)
  } catch (e) {
    // 接口失败，回退旧解析逻辑
  }
  if (info.shopId) return Number(info.shopId)
  try {
    const shops = (await shopList()) || []
    const hit = shops.find((s) => s.name === info.nickname)
    if (hit) return Number(hit.id)
  } catch (e) {
    // 列表失败走兜底逻辑
  }
  const uid = Number(info.userId ?? info.id)
  if (uid) return uid - 2
  return 1
}

/** 兼容后端直接返回数组或 {records} 分页结构 */
export function toList(data) {
  return Array.isArray(data) ? data : (data && data.records) || []
}

/** 拉取商家全部订单（mock 模式数据量小，一次 pageSize=200 拉全量后本地过滤/聚合） */
export async function fetchAllOrders(shopId) {
  const data = await getShopOrders({ shopId, page: 1, pageSize: 200 })
  return toList(data)
}

/** 订单金额安全转数字 */
export function numOf(v) {
  return Number(v) || 0
}

/** 判定订单是否为"有效完成"（已完成/已送达待确认都算收入） */
export function isFinished(o) {
  const s = Number(o.status)
  return s === 6 || s === 11
}

/** 是否为今天创建的订单 */
export function isToday(o) {
  const t = o.createdAt || o.createTime
  if (!t) return false
  const d = new Date(String(t).replace('T', ' '))
  const now = new Date()
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  )
}

/** 今日概览：营业额 / 订单数 / 客单价（仅统计有效完成的订单） */
export function todayStats(orders) {
  const done = (orders || []).filter(isFinished)
  const today = done.filter(isToday)
  const revenue = today.reduce((sum, o) => sum + numOf(o.total), 0)
  const count = today.length
  const avg = count > 0 ? revenue / count : 0
  return { revenue, count, avg }
}

/** 收入汇总：今日 / 本月 / 累计（仅统计有效完成的订单） */
export function incomeSummary(orders) {
  const done = (orders || []).filter(isFinished)
  const now = new Date()
  const today = done.filter(isToday)
  const month = done.filter((o) => {
    const t = o.createdAt || o.createTime
    if (!t) return false
    const d = new Date(String(t).replace('T', ' '))
    return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()
  })
  const sum = (arr) => arr.reduce((s, o) => s + numOf(o.total), 0)
  return { today: sum(today), month: sum(month), total: sum(done) }
}

/** 各状态订单计数（供订单页胶囊角标） */
export function statusCounts(orders) {
  const counts = {}
  ;(orders || []).forEach((o) => {
    const s = Number(o.status)
    counts[s] = (counts[s] || 0) + 1
  })
  return counts
}
