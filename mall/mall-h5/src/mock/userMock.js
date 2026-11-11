// 用户端前端 mock 服务层：集中提供无后端接口的运营展示数据（金刚区/神抢手/营销标签/神券/资产/定位）
// 设计约定：
//  - 页面只调本模块，后续接真实接口时只改这一层（mock-first 架构）
//  - 需要"用户操作后状态保留"的场景（券领取/神券包购买/地址切换）用 localStorage，key 前缀 user-mock-
//  - mockGet 统一模拟 300~500ms 网络延时，保证骨架屏/加载态可感知

const DELAY = () => 300 + Math.random() * 200

/** 统一模拟延时返回 */
export function mockGet(data) {
  return new Promise((resolve) => setTimeout(() => resolve(data), DELAY()))
}

/** 简单哈希：把 shopId 映射到稳定数字（营销标签按店铺稳定生成） */
function hashId(id) {
  const n = Number(id || 0)
  let h = 0
  const s = 'shop-' + n
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) % 100000
  }
  return h
}

// ===== 定位与地址（点击首页定位区切换，localStorage 保留） =====
const ADDR_KEY = 'user-mock-address'
export const ADDRESS_OPTIONS = [
  { id: 1, name: '农投国际中心', detail: '郑州市金水区农业路与经二路交叉口' },
  { id: 2, name: '郑州大学主校区', detail: '郑州市中原区科学大道 100 号' },
  { id: 3, name: '二七广场', detail: '郑州市二七区二七路与西大街交叉口' },
  { id: 4, name: '郑州东站', detail: '郑州市郑东新区商鼎路' }
]

export function getCurrentAddress() {
  const saved = localStorage.getItem(ADDR_KEY)
  if (saved) {
    try {
      const a = JSON.parse(saved)
      if (a && a.name) return a
    } catch (e) {
      // 损坏则回退默认
    }
  }
  return ADDRESS_OPTIONS[0]
}

export function setCurrentAddress(addr) {
  localStorage.setItem(ADDR_KEY, JSON.stringify(addr))
  return addr
}

export function getAddressOptions() {
  return mockGet(ADDRESS_OPTIONS)
}

// ===== 首页搜索热词（占位轮播） =====
export function getHotWords() {
  return mockGet(['奶茶', '麻辣烫', '汉堡炸鸡', '黄焖鸡米饭', '水果捞'])
}

// ===== 金刚区（2x5 宫格：emoji 图标 + 渐变底色 + 名称） =====
export function getKingkong() {
  const items = [
    { id: 1, name: '美食', emoji: '🍜', colors: ['#FFD54F', '#FFB300'] },
    { id: 2, name: '甜点饮品', emoji: '🧋', colors: ['#FFAB91', '#FF7043'] },
    { id: 3, name: '超市便利', emoji: '🛒', colors: ['#81C784', '#4CAF50'] },
    { id: 4, name: '看病买药', emoji: '💊', colors: ['#90CAF9', '#42A5F5'] },
    { id: 5, name: '下午茶', emoji: '🍰', colors: ['#F48FB1', '#EC407A'] },
    { id: 6, name: '拼好饭', emoji: '🍱', colors: ['#FFCC80', '#FF9800'] },
    { id: 7, name: '跑腿代购', emoji: '🏃', colors: ['#A5D6A7', '#66BB6A'] },
    { id: 8, name: '水果', emoji: '🍉', colors: ['#FF8A80', '#EF5350'] },
    { id: 9, name: '大牌优惠', emoji: '🎁', colors: ['#CE93D8', '#AB47BC'] },
    { id: 10, name: '更多', emoji: '➕', colors: ['#B0BEC5', '#78909C'] }
  ]
  return mockGet(items)
}

// ===== 神抢手（2x2 瀑布流商品 + 限时秒杀倒计时） =====
const FLASH_ITEMS = [
  { id: 1, name: '延庆观炸鸡（正弘城一只炸鸡）', price: '15.9', originPrice: '28', tag: '买贵必赔', emoji: '🍗', colors: ['#FFE082', '#FFA726'] },
  { id: 2, name: '沪上阿姨·鲜果茶（茉莉奶绿）', price: '9.9', originPrice: '16', tag: '买贵必赔', emoji: '🧋', colors: ['#B3E5FC', '#4FC3F7'] },
  { id: 3, name: '蜜雪冰城（柠檬水 2 杯）', price: '6.9', originPrice: '10', tag: '限时抢', emoji: '🍋', colors: ['#FFCDD2', '#EF9A9A'] },
  { id: 4, name: '正新鸡排（招牌大鸡排+饮料）', price: '13.9', originPrice: '22', tag: '买贵必赔', emoji: '🍖', colors: ['#DCEDC8', '#AED581'] }
]

export function getFlashSale() {
  return mockGet(FLASH_ITEMS)
}

// ===== 横向分类条（点击过滤店铺列表，mock 按 shopId 哈希分组） =====
export function getCategories() {
  const items = ['全部', '奶茶咖啡', '约30分钟', '多人餐', '小吃西餐', '汉堡披萨', '炸鸡炸串', '家常菜', '火锅']
  return mockGet(items)
}

/** 按分类名 + 店铺 id 哈希判断该店铺是否属于该分类（"全部"恒为真） */
export function shopInCategory(shopId, cat) {
  if (!cat || cat === '全部') return true
  return hashId(shopId) % 8 === (cat.length + cat.charCodeAt(0)) % 8
}

// ===== 营销标签（在真实店铺数据上叠加，按 shopId 稳定生成） =====
export function getMarketingTags(shopId) {
  const h = hashId(shopId)
  const tags = []
  if (h % 3 !== 0) {
    tags.push({ type: 'rating', text: '大众点评高分店铺' })
  }
  if (h % 2 === 0) {
    tags.push({ type: 'coupon', text: '神券 满28减13' })
  }
  // 特价商品预览
  const deals = [
    { name: '招牌套餐', save: '约省2元', emoji: '🍱' },
    { name: '超值单人餐', save: '约省3元', emoji: '🍛' },
    { name: '奶茶半价', save: '约省1.5元', emoji: '🧋' }
  ]
  const deal = deals[h % deals.length]
  const fee = { start: 15 + (h % 4) * 5, delivery: h % 3 === 0 ? 0 : 3 + (h % 3), distanceKm: (0.5 + (h % 60) / 10).toFixed(1), minutes: 18 + (h % 25) }
  return mockGet({ tags, deal, fee })
}

// ===== 我的神券（统一券模型：status 状态机 + localStorage 持久化，B 批） =====
// 券结构：{ id, type: 'red'|'shop', amount, threshold(0=无门槛), desc, scope, shopName?,
//           status: 'available'|'used'（expired/expiring 由时间与分组计算）, claimedAt, expireAt, usedAt?, orderNo? }
const MY_COUPONS_KEY = 'user-mock-my-coupons'        // 用户券列表（含状态）
const ORDER_COUPONS_KEY = 'user-mock-order-coupons'  // { [orderNo]: { couponId, discount } }
const EXTRA_CLAIMED_KEY = 'user-mock-extra-claimed'  // 旧键（首次初始化时迁移）

const H = 3600000
const DAY = 24 * H

// 初始持有券模板（首次初始化写入 my-coupons；expireAt 领取时定死持久化）
const BASE_COUPONS = [
  { id: 1, type: 'red', amount: '3', threshold: 0, desc: '无门槛', scope: '全场通用' },
  { id: 2, type: 'red', amount: '5', threshold: 0, desc: '无门槛', scope: '神抢手商品可用' },
  { id: 3, type: 'red', amount: '8', threshold: 25, desc: '满25可用', scope: '全场通用' },
  { id: 4, type: 'shop', amount: '13', threshold: 28, desc: '满28可用', shopName: '张姐麻辣烫' },
  { id: 5, type: 'shop', amount: '6', threshold: 20, desc: '满20可用', shopName: '沪上阿姨·鲜果茶' },
  { id: 6, type: 'shop', amount: '10', threshold: 35, desc: '满35可用', shopName: '延庆观炸鸡' },
  { id: 7, type: 'shop', amount: '5', threshold: 15, desc: '满15可用', shopName: '蜜雪冰城' }
]
// 初始有效期：id3 +8h、id5 +20h（券管理页「即将过期」演示），其余 +7d
const BASE_EXPIRE = (c) => (c.id === 3 ? 8 * H : c.id === 5 ? 20 * H : 7 * DAY)

// 传统领取渠道券（天天膨胀/神券包/店铺神券，不出现在领券中心池）
const EXTRA_COUPONS = [
  { id: 101, type: 'red', amount: '18', threshold: 0, desc: '无门槛', scope: '天天膨胀掉落' },
  { id: 102, type: 'red', amount: '2', threshold: 0, desc: '无门槛', scope: '神券包赠送' },
  { id: 103, type: 'red', amount: '5', threshold: 0, desc: '无门槛', scope: '神券包赠送' },
  { id: 104, type: 'shop', amount: '13', threshold: 28, desc: '满28可用', shopName: '张姐麻辣烫' }
]

// 领券中心可领券池（6 张；id 205 领取后 24h 到期，验证「仅剩 X 小时」角标）
const CLAIMABLE_POOL = [
  { id: 201, type: 'red', amount: '3', threshold: 0, desc: '无门槛', scope: '全场通用' },
  { id: 202, type: 'red', amount: '15', threshold: 50, desc: '满50可用', scope: '全场通用' },
  { id: 203, type: 'shop', amount: '20', threshold: 60, desc: '满60可用', shopName: '蜜雪冰城' },
  { id: 204, type: 'shop', amount: '10', threshold: 35, desc: '满35可用', shopName: '沪上阿姨·鲜果茶' },
  { id: 205, type: 'red', amount: '5', threshold: 0, desc: '无门槛', scope: '全场通用·24h有效', expireHours: 24 },
  { id: 206, type: 'shop', amount: '12', threshold: 30, desc: '满30可用', shopName: '延庆观炸鸡' }
]

function saveMyCoupons(list) {
  localStorage.setItem(MY_COUPONS_KEY, JSON.stringify(list))
}

/** 读用户券列表：首次初始化（新键缺失）时用 BASE + 旧键已领 extra 生成并落库（旧键迁移） */
function loadMyCoupons() {
  let list = null
  try {
    const raw = localStorage.getItem(MY_COUPONS_KEY)
    if (raw) list = JSON.parse(raw)
  } catch (e) {
    list = null
  }
  if (Array.isArray(list)) return list
  const now = Date.now()
  const claimed = JSON.parse(localStorage.getItem(EXTRA_CLAIMED_KEY) || '[]')
  list = [
    ...BASE_COUPONS.map((c) => ({ ...c, status: 'available', claimedAt: now, expireAt: now + BASE_EXPIRE(c) })),
    ...EXTRA_COUPONS.filter((c) => claimed.includes(c.id))
      .map((c) => ({ ...c, status: 'available', claimedAt: now, expireAt: now + 7 * DAY }))
  ]
  saveMyCoupons(list)
  return list
}

/** 计算实时状态：过期按时间判定（expired 由调用方并入 used 分组置灰） */
function withStatus(c) {
  if (c.status === 'used') return c
  if (c.expireAt && c.expireAt <= Date.now()) return { ...c, status: 'expired' }
  return { ...c, status: 'available' }
}

function readOrderCoupons() {
  try {
    return JSON.parse(localStorage.getItem(ORDER_COUPONS_KEY) || '{}')
  } catch (e) {
    return {}
  }
}

/** 我的券列表（签名不变；返回含实时 status 的统一结构） */
export function getMyCoupons() {
  return mockGet(loadMyCoupons().map(withStatus))
}

/** 可用（未过期未使用）券数：神券频道「可用 N 张」与资产联动 */
export function getMyCouponCount() {
  return loadMyCoupons().map(withStatus).filter((c) => c.status === 'available').length
}

/** 领取券（领券中心池 + 传统渠道 101-104）：重复领取返回 false */
export function claimCoupon(id) {
  const list = loadMyCoupons()
  if (list.some((c) => Number(c.id) === Number(id))) return false
  const src = [...CLAIMABLE_POOL, ...EXTRA_COUPONS].find((c) => Number(c.id) === Number(id))
  if (!src) return false
  const now = Date.now()
  const expireAt = src.expireHours ? now + src.expireHours * H : now + 7 * DAY
  list.push({ ...src, status: 'available', claimedAt: now, expireAt })
  saveMyCoupons(list)
  // 同步旧键（兼容历史逻辑读取处）
  const claimed = JSON.parse(localStorage.getItem(EXTRA_CLAIMED_KEY) || '[]')
  if (!claimed.includes(src.id)) {
    claimed.push(src.id)
    localStorage.setItem(EXTRA_CLAIMED_KEY, JSON.stringify(claimed))
  }
  return true
}

/** 领券中心可领券池（返回全池带 claimed 标志，已领的灰态显示；全部领完页面显示「今日券已领完」） */
export function getClaimableCoupons() {
  const owned = new Set(loadMyCoupons().map((c) => Number(c.id)))
  const list = CLAIMABLE_POOL.map((c) => ({ ...c, claimed: owned.has(c.id) }))
  return mockGet(list)
}

/** 领券中心领取（useLock 防重复；重复返回 false） */
export function claimFromCenter(id) {
  return claimCoupon(id)
}

/** 券分组：{ available, expiring(24h 内), used(含 expired 置灰) } */
export function getCouponGroups() {
  const now = Date.now()
  const available = []
  const expiring = []
  const used = []
  loadMyCoupons().forEach((c) => {
    const s = withStatus(c)
    if (s.status === 'used') used.push(s)
    else if (s.status === 'expired') used.push(s)
    else if (s.expireAt && s.expireAt - now < DAY) expiring.push({ ...s, status: 'expiring' })
    else available.push(s)
  })
  return mockGet({ available, expiring, used })
}

/** 前端计价：门槛校验 amount >= threshold ? min(券面额, amount) : 0 */
export function computeDiscount(coupon, amount) {
  const a = Number(amount || 0)
  const face = Number(coupon.amount || 0)
  const threshold = Number(coupon.threshold || 0)
  if (a < threshold) return 0
  return Math.min(face, a)
}

/** 下单写券：记录抵扣 + 券 status→used（usedAt/orderNo） */
export function writeOrderCoupon(orderNo, couponId, discount) {
  const map = readOrderCoupons()
  map[orderNo] = { couponId: Number(couponId), discount: Number(discount || 0) }
  localStorage.setItem(ORDER_COUPONS_KEY, JSON.stringify(map))
  const list = loadMyCoupons()
  const c = list.find((x) => Number(x.id) === Number(couponId))
  if (c) {
    c.status = 'used'
    c.usedAt = Date.now()
    c.orderNo = orderNo
    saveMyCoupons(list)
  }
  return map[orderNo]
}

/** 读订单抵扣记录（无记录返回 null，附带券信息） */
export function getOrderCoupon(orderNo) {
  const rec = readOrderCoupons()[orderNo]
  if (!rec) return null
  const coupon = loadMyCoupons().find((x) => Number(x.id) === Number(rec.couponId)) || null
  return { couponId: rec.couponId, discount: rec.discount, coupon }
}

/** 退券：订单取消/退款时券恢复 available、清 usedAt/orderNo、删抵扣记录 */
export function releaseCoupon(orderNo) {
  const map = readOrderCoupons()
  const rec = map[orderNo]
  if (!rec) return false
  delete map[orderNo]
  localStorage.setItem(ORDER_COUPONS_KEY, JSON.stringify(map))
  const list = loadMyCoupons()
  const c = list.find((x) => Number(x.id) === Number(rec.couponId))
  if (c && c.status === 'used' && c.orderNo === orderNo) {
    c.status = 'available'
    delete c.usedAt
    delete c.orderNo
    saveMyCoupons(list)
  }
  return true
}

// ===== 神券包（购买状态持久化；购买后 102/103 券走统一结构自动到账） =====
const BUNDLE_KEY = 'user-mock-bundle-bought'
export function getCouponBundle() {
  const bought = localStorage.getItem(BUNDLE_KEY) === '1'
  return mockGet({
    title: '神券包',
    desc: '5元 × 20张 无门槛',
    price: '7.9',
    originPrice: '100',
    bought,
    sold: 23876
  })
}

export function buyCouponBundle() {
  const already = localStorage.getItem(BUNDLE_KEY) === '1'
  if (already) return false
  localStorage.setItem(BUNDLE_KEY, '1')
  // 购买后券包券自动到账（统一券结构）
  ;[102, 103].forEach((id) => claimCoupon(id))
  return true
}

// ===== 我的资产（与券数据联动：红包/代金券按可用券实时计算） =====
export function getMyAssets() {
  const bundleBought = localStorage.getItem(BUNDLE_KEY) === '1'
  const available = loadMyCoupons().map(withStatus).filter((c) => c.status === 'available')
  const redCount = 16 + available.filter((c) => c.type === 'red' && ![1, 2, 3].includes(c.id)).length + (bundleBought ? 2 : 0)
  const voucherCount = available.filter((c) => c.type === 'shop').length
  const extra = JSON.parse(localStorage.getItem(EXTRA_CLAIMED_KEY) || '[]')
  return mockGet({
    redCount, // 美团红包
    voucherCount, // 商家代金券（可用店铺券实时数）
    subsidy: '20', // 津贴（元）
    wallet: '32.5', // 钱包余额（元）
    savedTotal: 58 + extra.length * 6 // 累计已省（元）
  })
}

// ===== 订单交易快照（订单详情页弹窗静态内容） =====
export function getTradeSnapshot(orderNo) {
  return mockGet({
    orderNo,
    title: '交易快照',
    subTitle: '发生交易争议时，可作为判断依据',
    rows: [
      { label: '下单时间', value: '2026-10-20 11:10:16' },
      { label: '支付时间', value: '2026-10-20 11:10:18' },
      { label: '支付方式', value: '微信支付' },
      { label: '商家名称', value: '张姐麻辣烫' },
      { label: '配送方式', value: '商家自送' },
      { label: '快照编号', value: 'SN202610201110161376' }
    ]
  })
}

// ==================== P1 新增：搜索 ====================
const SEARCH_HISTORY_KEY = 'user-mock-search-history'

/** 搜索历史（localStorage，最多 10 条，去重、最新在前） */
export function getSearchHistory() {
  try {
    return JSON.parse(localStorage.getItem(SEARCH_HISTORY_KEY) || '[]')
  } catch (e) {
    return []
  }
}

export function addSearchHistory(kw) {
  const list = getSearchHistory().filter((k) => k !== kw)
  list.unshift(kw)
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(list.slice(0, 10)))
}

export function clearSearchHistory() {
  localStorage.removeItem(SEARCH_HISTORY_KEY)
}

/** 热搜榜（8 条带热度值） */
export function getHotSearch() {
  return mockGet([
    { kw: '麻辣烫', hot: '98w' },
    { kw: '奶茶', hot: '87w' },
    { kw: '炸鸡', hot: '76w' },
    { kw: '黄焖鸡', hot: '65w' },
    { kw: '汉堡', hot: '58w' },
    { kw: '烧烤', hot: '49w' },
    { kw: '水果捞', hot: '41w' },
    { kw: '火锅', hot: '38w' }
  ])
}

/** 联想词库（前缀匹配返回前 8 条） */
const SUGGEST_WORDS = [
  '麻辣烫', '麻辣香锅', '奶茶', '奶盖', '奶油蛋糕', '炸鸡', '炸串', '炸酱面',
  '黄焖鸡米饭', '汉堡', '烧烤', '烤鱼', '水果捞', '水果拼盘', '火锅', '火锅食材',
  '米线', '米粉', '寿司', '沙拉', '披萨', '意面', '饺子', '馄饨', '盖浇饭', '煲仔饭',
  '凉皮', '肉夹馍', '螺蛳粉', '酸辣粉', '奶茶咖啡', '甜品'
]

export function getSuggest(kw) {
  const k = (kw || '').trim()
  if (!k) return mockGet([])
  const list = SUGGEST_WORDS.filter((w) => w.includes(k)).slice(0, 8)
  return mockGet(list)
}

/** 搜索结果菜品（mock 聚合，点击进对应店铺） */
const SEARCH_DISHES = [
  { id: 1, name: '招牌麻辣烫', shopName: '张姐麻辣烫', shopId: 1, price: '18.8', sold: 2314, emoji: '🍲' },
  { id: 2, name: '茉莉奶绿', shopName: '沪上阿姨·鲜果茶', shopId: 2, price: '9.9', sold: 1987, emoji: '🧋' },
  { id: 3, name: '柠檬水', shopName: '蜜雪冰城', shopId: 4, price: '4', sold: 8765, emoji: '🍋' },
  { id: 4, name: '招牌大鸡排', shopName: '正新鸡排', shopId: 3, price: '15', sold: 1567, emoji: '🍗' },
  { id: 5, name: '黄焖鸡米饭', shopName: '杨铭宇黄焖鸡', shopId: 5, price: '16.8', sold: 2102, emoji: '🍛' },
  { id: 6, name: '牛肉汉堡套餐', shopName: '华莱士', shopId: 6, price: '19.9', sold: 1423, emoji: '🍔' },
  { id: 7, name: '水果捞大份', shopName: '鲜果时光', shopId: 7, price: '12.9', sold: 987, emoji: '🍓' },
  { id: 8, name: '麻辣香锅', shopName: '蜀香麻辣香锅', shopId: 8, price: '22.5', sold: 1311, emoji: '🥘' }
]

export function getSearchDishes(kw) {
  const k = (kw || '').trim()
  const list = SEARCH_DISHES.filter((d) => d.name.includes(k) || d.shopName.includes(k))
  return mockGet(list)
}

// ==================== P1 新增：通用频道 ====================
const CHANNEL_SPECIAL = {
  美食: {
    banner: { emoji: '🍜', title: '美食狂欢季', sub: '人气爆款 5 折起', colors: ['#FFE082', '#FFA726'] },
    cats: ['全部', '家常菜', '火锅', '川湘菜', '小吃快餐']
  },
  甜点饮品: {
    banner: { emoji: '🧋', title: '甜心下午茶', sub: '奶茶甜品 第二杯半价', colors: ['#F8BBD0', '#F06292'] },
    cats: ['全部', '奶茶咖啡', '蛋糕甜点', '冰淇淋', '水果捞']
  },
  超市便利: {
    banner: { emoji: '🛒', title: '超市到家', sub: '满 49 减 15 起', colors: ['#C8E6C9', '#66BB6A'] },
    cats: ['全部', '零食饮料', '粮油调味', '日用百货', '酒水乳品']
  }
}

const CHANNEL_DEFAULT = {
  banner: { emoji: '🎁', title: '', sub: '好物低至 1 折', colors: ['#FFE082', '#FFC300'] },
  cats: ['全部', '约30分钟', '多人餐']
}

/** 频道配置：3 个专属（美食/甜点饮品/超市便利），其余通用模板 */
export function getChannelConfig(name) {
  const special = CHANNEL_SPECIAL[name]
  if (special) return mockGet(special)
  return mockGet({
    banner: { ...CHANNEL_DEFAULT.banner, title: (name || '精选') + '频道' },
    cats: [...CHANNEL_DEFAULT.cats]
  })
}

// ==================== P1 新增：神抢手秒杀 ====================
const FLASH_STOCK_KEY = 'user-mock-flash-stock'
const FLASH_GRABBED_KEY = 'user-mock-flash-grabbed'

/** 秒杀详情：库存/已抢按商品 id 稳定初始，localStorage 递减 */
export function getFlashDetail(id) {
  const base = 60 + Number(id || 0) * 13
  let stock = null
  try {
    const map = JSON.parse(localStorage.getItem(FLASH_STOCK_KEY) || '{}')
    stock = map[id] != null ? map[id] : base
  } catch (e) {
    stock = base
  }
  const item = FLASH_ITEMS.find((f) => f.id === Number(id)) || FLASH_ITEMS[0]
  const grabbed = JSON.parse(localStorage.getItem(FLASH_GRABBED_KEY) || '[]')
  return mockGet({
    ...item,
    stock,
    sold: base - stock,
    limit: 1,
    grabbed: grabbed.includes(item.id),
    // 秒杀商品 → 店铺映射（抢购成功加购后跳对应店铺）：1 延庆观炸鸡→3、2 沪上阿姨→2、3 蜜雪冰城→4、4 正新鸡排→3
    shopId: { 1: 3, 2: 2, 3: 4, 4: 3 }[item.id] || 1
  })
}

/** 抢购：库存/限购校验 */
export function grabFlash(id) {
  const item = FLASH_ITEMS.find((f) => f.id === Number(id)) || FLASH_ITEMS[0]
  const grabbed = JSON.parse(localStorage.getItem(FLASH_GRABBED_KEY) || '[]')
  if (grabbed.includes(item.id)) return { ok: false, msg: '每个账号限购 1 份' }
  const base = 60 + Number(item.id) * 13
  let map = {}
  try {
    map = JSON.parse(localStorage.getItem(FLASH_STOCK_KEY) || '{}')
  } catch (e) {
    map = {}
  }
  const stock = map[item.id] != null ? map[item.id] : base
  if (stock <= 0) return { ok: false, msg: '手慢了，已抢光' }
  map[item.id] = stock - 1
  localStorage.setItem(FLASH_STOCK_KEY, JSON.stringify(map))
  grabbed.push(item.id)
  localStorage.setItem(FLASH_GRABBED_KEY, JSON.stringify(grabbed))
  return { ok: true, msg: '抢购成功，已放入购物车' }
}

// ==================== P1 新增：收藏 ====================
const FAV_KEY = 'user-mock-favorites'

/** 收藏列表（localStorage 存店铺快照，营销标签由调用方按 shopId 补齐） */
export function getFavorites() {
  try {
    return JSON.parse(localStorage.getItem(FAV_KEY) || '[]')
  } catch (e) {
    return []
  }
}

export function isFavorited(shopId) {
  return getFavorites().some((f) => Number(f.shopId) === Number(shopId))
}

/** 切换收藏：存在则移除，否则快照入列；返回 { faved, list } */
export function toggleFavorite(shop) {
  const list = getFavorites()
  const idx = list.findIndex((f) => Number(f.shopId) === Number(shop.id))
  if (idx >= 0) {
    list.splice(idx, 1)
    localStorage.setItem(FAV_KEY, JSON.stringify(list))
    return { faved: false, list }
  }
  list.unshift({
    shopId: shop.id,
    name: shop.name,
    image: shop.image,
    minPrice: shop.minPrice,
    rating: shop.rating,
    categoryName: shop.categoryName,
    savedAt: Date.now()
  })
  localStorage.setItem(FAV_KEY, JSON.stringify(list))
  return { faved: true, list }
}

// ==================== P1 新增：客服 ====================
export function getServiceFaq() {
  return mockGet([
    { q: '订单迟迟未送达怎么办？', a: '您可在订单详情页点击「催单」，商家和骑手会收到提醒；若超时严重，可申请售后或联系客服协助处理。' },
    { q: '如何申请退款？', a: '订单完成后（或配送中）进入订单详情，点击「申请售后」，选择退款类型和原因提交即可。' },
    { q: '配送超时了有赔偿吗？', a: '若订单超时送达，可联系在线客服申请超时赔付，审核通过后赔付金将发放至您的账户。' },
    { q: '优惠券如何使用？', a: '下单结算页的「优惠券」区域选择可用券，系统会自动计算抵扣金额；每笔订单限用一张券。' },
    { q: '如何开发票？', a: '在「我的-客服中心」联系在线客服，提供订单号与抬头信息，电子发票将在 3 个工作日内发送至您的手机。' },
    { q: '如何联系骑手？', a: '订单进入配送中后，订单详情页会显示骑手信息，可点击拨打骑手电话。' },
    { q: '红包/神券会过期吗？', a: '红包和神券均有有效期，可在「神券-我的神券」查看；过期自动失效，请及时使用。' }
  ])
}

/** 在线客服 mock 自动回复：关键词命中 FAQ，否则通用话术；「转人工」提示工作时段 */
export function getServiceReply(text) {
  const t = (text || '').trim()
  if (t.includes('转人工')) {
    return '人工客服工作时间为 9:00-21:00，请在该时段输入「转人工」，客服小姐姐马上为您服务哦～'
  }
  if (t.includes('退款')) return '如需退款，请在订单详情点击「申请售后」，选择退款类型并提交，商家会在 24 小时内处理。'
  if (t.includes('催') || t.includes('慢') || t.includes('没送到')) return '很抱歉给您带来不便！您可在订单详情点击「催单」提醒商家和骑手，我们也会持续跟进配送进度。'
  if (t.includes('券') || t.includes('红包') || t.includes('优惠')) return '优惠券在结算页「优惠券」区域选择使用，每单限用一张，满足门槛即可抵扣～'
  if (t.includes('发票')) return '请提供订单号和发票抬头，电子发票将在 3 个工作日内发送至您的手机。'
  return '您好，我是智能客服小美，已收到您的问题～您可以描述一下遇到的具体情况（如：退款/催单/优惠券/发票），我会尽力帮您解决。'
}

// ==================== P1 新增：特价商品流 ====================
export function getDealProducts() {
  return mockGet([
    { id: 1, name: '麻辣烫双人套餐', price: '19.9', originPrice: '32', sold: 3210, emoji: '🍲', colors: ['#FFE0B2', '#FFB74D'], shopId: 1 },
    { id: 2, name: '柠檬水 2 杯', price: '6.9', originPrice: '10', sold: 5213, emoji: '🍋', colors: ['#FFCDD2', '#EF9A9A'], shopId: 4 },
    { id: 3, name: '招牌鸡排+饮料', price: '13.9', originPrice: '22', sold: 2876, emoji: '🍗', colors: ['#DCEDC8', '#AED581'], shopId: 3 },
    { id: 4, name: '茉莉奶绿大杯', price: '8.8', originPrice: '14', sold: 4102, emoji: '🧋', colors: ['#B3E5FC', '#4FC3F7'], shopId: 2 },
    { id: 5, name: '黄焖鸡+米饭', price: '15.9', originPrice: '21', sold: 1987, emoji: '🍛', colors: ['#FFE082', '#FFCA28'], shopId: 5 },
    { id: 6, name: '汉堡双人餐', price: '22.9', originPrice: '36', sold: 1543, emoji: '🍔', colors: ['#C5CAE9', '#7986CB'], shopId: 6 },
    { id: 7, name: '水果捞大份', price: '11.9', originPrice: '18', sold: 2678, emoji: '🍓', colors: ['#F8BBD0', '#F06292'], shopId: 7 },
    { id: 8, name: '麻辣香锅单人', price: '18.9', originPrice: '27', sold: 1765, emoji: '🥘', colors: ['#FFCCBC', '#FF8A65'], shopId: 8 }
  ])
}
