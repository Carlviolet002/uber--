// 统一 API 对象：集中管理所有后端接口，页面既可 `import api` 也可按需具名引入
import request from './request'

const api = {
  // ===== 用户模块 =====
  register: (d) => request.post('/api/user/register', d),
  login: (d) => request.post('/api/user/login', d),
  me: () => request.get('/api/user/me'),
  updateMe: (d) => request.put('/api/user/me', d),
  userList: (p) => request.get('/api/user/list', { params: p }),
  addressList: () => request.get('/api/user/address'),
  addressAdd: (d) => request.post('/api/user/address', d),
  addressUpdate: (id, d) => request.put('/api/user/address/' + id, d),
  addressDelete: (id) => request.delete('/api/user/address/' + id),

  // ===== 站内消息模块（D6-C，real 模式；mock 模式 404 由页面静默降级到 shopMock） =====
  messages: (p) => request.get('/api/user/messages', { params: p, silent: true }),
  messageUnreadCount: () => request.get('/api/user/messages/unread-count', { silent: true }),
  messageRead: (id) => request.put('/api/user/messages/' + id + '/read', null, { silent: true }),
  messageReadAll: () => request.put('/api/user/messages/read-all', null, { silent: true }),

  // ===== 商家模块 =====
  shopList: (p) => request.get('/api/shop/list', { params: p }),
  shopDetail: (id) => request.get('/api/shop/' + id),
  dishes: (id) => request.get('/api/shop/' + id + '/dishes'),
  categories: (id) => request.get('/api/shop/' + id + '/categories'),
  shopStatus: (id, status) => request.put('/api/shop/' + id + '/status', { status }),
  dishAdd: (d) => request.post('/api/shop/dish', d),
  dishUpdate: (id, d) => request.put('/api/shop/dish/' + id, d),
  dishDelete: (id) => request.delete('/api/shop/dish/' + id),
  shopAccept: (no) => request.put('/api/shop/order/' + no + '/accept'),
  shopReject: (no, reason) => request.put('/api/shop/order/' + no + '/reject', { reason }),
  shopOrders: (p) => request.get('/api/shop/orders', { params: p }),
  shopFinishCooking: (no) => request.put('/api/shop/order/' + no + '/finish-cooking'),
  setSoldOut: (dishId, soldOut) => request.put('/api/shop/dish/' + dishId + '/sold-out', null, { params: { soldOut } }),
  getShopOrders: (p) => request.get('/api/shop/orders', { params: p }),
  getShopById: (id) => request.get('/api/shop/' + id),
  shopMine: () => request.get('/api/shop/mine'),

  // ===== 订单模块 =====
  createOrder: (d) => request.post('/api/order', d),
  pay: (no) => request.put('/api/order/' + no + '/pay'),
  orderList: (p) => request.get('/api/order/list', { params: p }),
  orderDetail: (no) => request.get('/api/order/' + no),
  cancelOrder: (no, reason) => request.put('/api/order/' + no + '/cancel', { reason }),
  urgeOrder: (no) => request.post('/api/order/' + no + '/urge'),
  confirmOrder: (no) => request.put('/api/order/' + no + '/confirm'),
  submitReview: (no, d) => request.post('/api/order/' + no + '/review', d),
  getReview: (no) => request.get('/api/order/' + no + '/review'),
  getShopReviews: (shopId) => request.get('/api/shop/' + shopId + '/reviews'),
  replyReview: (reviewId, content) => request.put('/api/shop/review/' + reviewId + '/reply', { content }),

  // ===== 管理端（运营后台）=====
  // P1-18 数据看板
  adminStats: () => request.get('/api/order/admin/stats'),
  // P1-17 评价管理：分页列表（支持按店铺/关键词过滤）
  adminReviews: (p) => request.get('/api/order/admin/reviews', { params: p }),
  // P1-17 平台审核：auditStatus 1 通过 / 2 下架
  auditReview: (id, auditStatus) => request.put('/api/order/admin/review/' + id + '/audit', { auditStatus }),
  // P1-17 平台回复（区别于商家回复）
  platformReplyReview: (id, content) => request.put('/api/order/admin/review/' + id + '/reply', { content }),

  // ===== 派单模块 =====
  riderLogin: (d) => request.post('/api/dispatch/rider/login', d),
  riderOnline: () => request.put('/api/dispatch/rider/online'),
  riderOffline: () => request.put('/api/dispatch/rider/offline'),
  riderLocation: (lng, lat) => request.post('/api/dispatch/rider/location', { lng, lat }),
  riderOrders: () => request.get('/api/dispatch/rider/orders'),
  riderPosition: (id) => request.get('/api/dispatch/rider/position/' + id),
  // 回读当前骑手在线状态（工作台刷新后正确显示开关，避免与实际状态相反）
  riderStatus: () => request.get('/api/dispatch/rider/status'),
  riderAccept: (no) => request.put('/api/dispatch/rider/order/' + no + '/accept'),
  riderPicked: (no) => request.put('/api/dispatch/rider/order/' + no + '/picked'),
  riderDelivered: (no) => request.put('/api/dispatch/rider/order/' + no + '/delivered'),
  // 轮询请求静默：离线时的 403 由页面统一提示一次，避免 10 秒轮询反复弹窗
  riderPool: () => request.get('/api/dispatch/rider/pool', { silent: true }),
  grabOrder: (no) => request.post('/api/dispatch/rider/pool/' + no + '/grab'),
  riderRecommend: () => request.get('/api/dispatch/rider/recommend', { silent: true }),
  adminRecords: (p) => request.get('/api/dispatch/admin/records', { params: p }),
  adminBatches: (p) => request.get('/api/dispatch/admin/batches', { params: p }),
  adminTrigger: () => request.post('/api/dispatch/admin/trigger'),
  adminConfig: () => request.get('/api/dispatch/admin/config'),
  adminConfigPut: (strategy) => request.put('/api/dispatch/admin/config', { strategy }),
  adminRiders: (p) => request.get('/api/dispatch/admin/riders', { params: p }),
  adminRiderAdd: (d) => request.post('/api/dispatch/admin/rider', d),
  // P3 策略对比：读取 mall-sim 离线仿真产物，展示 A/B/C 三策略指标
  simCompare: () => request.get('/api/dispatch/admin/sim-compare')
}

export default api

// 具名导出：兼容业务页面 `import { xxx } from '@/api'` 的按需引入写法
export const {
  register,
  login,
  me,
  updateMe,
  userList,
  addressList,
  addressAdd,
  addressUpdate,
  addressDelete,
  messages,
  messageUnreadCount,
  messageRead,
  messageReadAll,
  shopList,
  shopDetail,
  dishes,
  categories,
  shopStatus,
  dishAdd,
  dishUpdate,
  dishDelete,
  shopAccept,
  shopReject,
  shopOrders,
  shopFinishCooking,
  setSoldOut,
  getShopOrders,
  getShopById,
  shopMine,
  createOrder,
  pay,
  orderList,
  orderDetail,
  cancelOrder,
  urgeOrder,
  confirmOrder,
  submitReview,
  getReview,
  getShopReviews,
  replyReview,
  adminStats,
  adminReviews,
  auditReview,
  platformReplyReview,
  riderLogin,
  riderOnline,
  riderOffline,
  riderLocation,
  riderOrders,
  riderPosition,
  riderStatus,
  riderAccept,
  riderPicked,
  riderDelivered,
  riderPool,
  grabOrder,
  riderRecommend,
  adminRecords,
  adminBatches,
  adminTrigger,
  adminConfig,
  adminConfigPut,
  adminRiders,
  adminRiderAdd,
  simCompare
} = api
