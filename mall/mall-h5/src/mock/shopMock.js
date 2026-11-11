// 商家端前端 mock 服务层：集中提供无后端接口的静态展示数据（消息/推广/顾客/趋势/店铺可编辑字段）
// 设计约定：
//  - 页面只调本模块，后续接真实接口时只改这一层（mock-first 架构）
//  - 需要"用户操作后状态保留"的场景（消息已读/顾客标记/推广开关）才用 localStorage，key 前缀 shop-mock-
//  - mockGet 统一模拟 300~500ms 网络延时，保证骨架屏/加载态可感知

const DELAY = () => 300 + Math.random() * 200

/** 统一模拟延时返回 */
export function mockGet(data) {
  return new Promise((resolve) => setTimeout(() => resolve(data), DELAY()))
}

/** 生成最近 7 天的日期标签（MM-DD） */
function last7Days() {
  const arr = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    arr.push(`${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`)
  }
  return arr
}

// ===== 近 7 日营业额趋势（演示假数据：H2 历史订单少，真实聚合只有今天有柱子）=====
export function getTrend7d() {
  const days = last7Days()
  // 金额序列（元）：今天最后一位，前端高亮
  const amounts = [168, 232, 145, 289, 196, 254, 318]
  return mockGet(days.map((date, i) => ({ date, amount: amounts[i] })))
}

// ===== 平台消息（演示假数据，TODO 接后端消息服务 / real 模式 RabbitMQ）=====
const PLATFORM_MSG_KEY = 'shop-mock-platform-msg-read'
const PLATFORM_MESSAGES = [
  { id: 1, title: '平台规则更新', summary: '《外卖平台商家服务规范》v3 将于 11 月 1 日生效，请及时查看。', time: '10-18 09:30' },
  { id: 2, title: '营业资质提醒', summary: '您的营业执照将于 2027-03-15 到期，记得提前续期哦。', time: '10-17 14:20' },
  { id: 3, title: '新商家成长计划', summary: '完成 3 项新手任务，可领取 50 元推广红包。', time: '10-15 10:05' },
  { id: 4, title: '食品安全培训', summary: '本周四 15:00 线上培训「夏季餐品储存规范」，欢迎报名。', time: '10-12 16:40' },
  { id: 5, title: '系统维护通知', summary: '平台将于 10-21 02:00-04:00 停机维护，期间订单不受影响。', time: '10-11 08:00' },
  { id: 6, title: '好评激励活动', summary: '10 月「星级商家」评选开始，好评率前 10% 可获流量扶持。', time: '10-08 11:15' }
]

export function getPlatformMessages() {
  const read = JSON.parse(localStorage.getItem(PLATFORM_MSG_KEY) || '[]')
  return mockGet(PLATFORM_MESSAGES.map((m) => ({ ...m, read: read.includes(m.id) })))
}

export function markMessageRead(id) {
  const read = JSON.parse(localStorage.getItem(PLATFORM_MSG_KEY) || '[]')
  if (!read.includes(id)) {
    read.push(id)
    localStorage.setItem(PLATFORM_MSG_KEY, JSON.stringify(read))
  }
}

export function unreadPlatformCount() {
  const read = JSON.parse(localStorage.getItem(PLATFORM_MSG_KEY) || '[]')
  return PLATFORM_MESSAGES.filter((m) => !read.includes(m.id)).length
}

// ===== 推广活动（演示假数据，TODO 接营销服务）=====
const PROMO_KEY = 'shop-mock-promo-enabled'
const PROMOTIONS = [
  { id: 1, type: 'full-reduce', title: '满减活动', desc: '满 20 减 2 · 满 35 减 5', tag: '进行中', time: '10-01 ~ 10-31' },
  { id: 2, type: 'new-customer', title: '新客立减', desc: '新客首单立减 ¥3', tag: '进行中', time: '长期有效' },
  { id: 3, type: 'banner', title: '推广位素材', desc: '首页轮播位 · 招牌麻辣烫', tag: '待投放', time: '—' }
]

export function getPromotions() {
  const enabled = JSON.parse(localStorage.getItem(PROMO_KEY) || '{}')
  return mockGet(PROMOTIONS.map((p) => ({ ...p, enabled: enabled[p.id] !== false })))
}

export function togglePromotion(id, enabled) {
  const map = JSON.parse(localStorage.getItem(PROMO_KEY) || '{}')
  map[id] = enabled
  localStorage.setItem(PROMO_KEY, JSON.stringify(map))
}

// ===== 顾客列表（演示假数据，TODO 接 GET /api/shop/customers）=====
const CUSTOMER_KEY = 'shop-mock-customers'
const DEFAULT_CUSTOMERS = [
  { id: 1, nick: '张*', phoneTail: '8821', orderCount: 12, totalSpend: 268.5, lastTime: '10-19 12:40', marked: true, blocked: false },
  { id: 2, nick: '李*同学', phoneTail: '3307', orderCount: 8, totalSpend: 156.0, lastTime: '10-18 18:22', marked: false, blocked: false },
  { id: 3, nick: '王*', phoneTail: '9912', orderCount: 21, totalSpend: 512.8, lastTime: '10-19 11:03', marked: true, blocked: false },
  { id: 4, nick: '赵*', phoneTail: '4560', orderCount: 3, totalSpend: 66.5, lastTime: '10-16 19:45', marked: false, blocked: false },
  { id: 5, nick: '陈*', phoneTail: '7789', orderCount: 15, totalSpend: 389.0, lastTime: '10-19 08:12', marked: false, blocked: false },
  { id: 6, nick: '刘*', phoneTail: '2034', orderCount: 5, totalSpend: 98.0, lastTime: '10-14 13:30', marked: false, blocked: true },
  { id: 7, nick: '周*', phoneTail: '6615', orderCount: 9, totalSpend: 203.4, lastTime: '10-17 20:05', marked: false, blocked: false },
  { id: 8, nick: '吴*', phoneTail: '1278', orderCount: 2, totalSpend: 42.0, lastTime: '10-10 12:18', marked: false, blocked: false }
]

export function getCustomers() {
  const saved = JSON.parse(localStorage.getItem(CUSTOMER_KEY) || 'null')
  return mockGet(saved || DEFAULT_CUSTOMERS)
}

export function updateCustomer(id, patch) {
  const saved = JSON.parse(localStorage.getItem(CUSTOMER_KEY) || 'null')
  const list = saved || DEFAULT_CUSTOMERS
  const target = list.find((c) => c.id === id)
  if (target) Object.assign(target, patch)
  localStorage.setItem(CUSTOMER_KEY, JSON.stringify(list))
  return list
}

// ===== 收入明细（演示假数据，TODO 接后端统计接口）=====
export function getIncomeDetail() {
  return mockGet([
    { date: '10-19', orders: 18, amount: 318.0 },
    { date: '10-18', orders: 15, amount: 254.0 },
    { date: '10-17', orders: 11, amount: 196.0 },
    { date: '10-16', orders: 17, amount: 289.0 },
    { date: '10-15', orders: 9, amount: 145.0 }
  ])
}

// ===== 店铺可编辑字段（演示数据，TODO 接 PUT /api/shop/{id}）=====
const SHOP_EDIT_KEY = 'shop-mock-shop-edit'
const DEFAULT_SHOP_EDIT = { notice: '欢迎光临，本店招牌麻辣烫，微辣中辣特辣任选～', openTime: '10:00 - 22:00', minPrice: 15, deliveryFee: 3 }

export function getShopEditable() {
  const saved = JSON.parse(localStorage.getItem(SHOP_EDIT_KEY) || 'null')
  return mockGet(saved || DEFAULT_SHOP_EDIT)
}

export function saveShopEditable(patch) {
  const saved = JSON.parse(localStorage.getItem(SHOP_EDIT_KEY) || 'null')
  const merged = { ...(saved || DEFAULT_SHOP_EDIT), ...patch }
  localStorage.setItem(SHOP_EDIT_KEY, JSON.stringify(merged))
  return merged
}
