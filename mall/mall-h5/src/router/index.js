// 路由配置：三端合一（用户端 / 骑手端 / 管理端），通过 meta.roles 做角色控制
import { createRouter, createWebHashHistory } from 'vue-router'
// 用户端页面
import UserShopDetail from '../views/user/ShopDetail.vue'
import UserCart from '../views/user/Cart.vue'
import UserCheckout from '../views/user/Checkout.vue'
import UserOrderDetail from '../views/user/OrderDetail.vue'
import UserAddresses from '../views/user/Addresses.vue'
// 用户端布局壳与 4 Tab 页面：懒加载
const UserLayout = () => import('../views/user/UserLayout.vue')
const UserHome = () => import('../views/user/Home.vue')
const UserCoupons = () => import('../views/user/Coupons.vue')
const UserOrders = () => import('../views/user/Orders.vue')
const UserMe = () => import('../views/user/Me.vue')
// 用户端 P1 二级页：懒加载
const UserSearch = () => import('../views/user/Search.vue')
const UserChannel = () => import('../views/user/Channel.vue')
const UserFavorites = () => import('../views/user/Favorites.vue')
const UserCouponManage = () => import('../views/user/CouponManage.vue')
const UserService = () => import('../views/user/Service.vue')
// 骑手端页面
import RiderTasks from '../views/rider/Tasks.vue'
import RiderTaskDetail from '../views/rider/TaskDetail.vue'
const RiderIncome = () => import('../views/rider/Income.vue')
const RiderReport = () => import('../views/rider/Report.vue')
// 管理端页面
import AdminLayout from '../views/admin/Layout.vue'
import AdminUsers from '../views/admin/Users.vue'
import AdminShops from '../views/admin/Shops.vue'
import AdminRiders from '../views/admin/Riders.vue'
import AdminOrders from '../views/admin/Orders.vue'
import AdminDispatch from '../views/admin/Dispatch.vue'
// 管理端 P1-17/18 与 P3 新页面：懒加载
const AdminDashboard = () => import('../views/admin/DataDashboard.vue')
const AdminReviewManage = () => import('../views/admin/ReviewManage.vue')
const AdminSimulation = () => import('../views/admin/SimulationCompare.vue')
// 403 页面
import Forbidden from '../views/Forbidden.vue'
// 门户页与商家端页面：懒加载（商家端由业务侧同步开发）
const Portal = () => import('../views/portal/Portal.vue')
const ShopLayout = () => import('../views/shop/ShopLayout.vue')
const ShopHome = () => import('../views/shop/Home.vue')
const ShopOrders = () => import('../views/shop/Orders.vue')
const ShopOrderDetail = () => import('../views/shop/OrderDetail.vue')
const ShopMessages = () => import('../views/shop/Messages.vue')
const ShopStore = () => import('../views/shop/Store.vue')
const ShopDishes = () => import('../views/shop/Dishes.vue')
const ShopPromote = () => import('../views/shop/Promote.vue')
const ShopMe = () => import('../views/shop/Me.vue')
const ShopCustomers = () => import('../views/shop/Customers.vue')
const ShopReviews = () => import('../views/shop/Reviews.vue')

const routes = [
  { path: '/', redirect: '/portal' },
  { path: '/portal', component: Portal, meta: { title: '骑手外卖 MALL' } },
  { path: '/login', redirect: '/portal' },
  // 注册已收敛进门户（Portal.vue 内嵌注册表单），不再有独立 /register 路由
  // ===== 用户端（布局壳 UserLayout + 4 Tab；所有 user 页均为 children，底部 Tabbar 常驻） =====
  {
    path: '/user',
    component: UserLayout,
    meta: { requiresAuth: true, roles: ['USER'] },
    children: [
      { path: '', redirect: '/user/home' },
      { path: 'home', component: UserHome, meta: { title: '点外卖' } },
      { path: 'coupons', component: UserCoupons, meta: { title: '神券' } },
      { path: 'orders', component: UserOrders, meta: { title: '我的订单' } },
      { path: 'me', component: UserMe, meta: { title: '我的' } },
      // 以下二级页同样被 UserLayout 包裹 → 任意 user 页面底部 4 Tab 始终存在
      // requiresAuth / roles 由父路由 /user 的 meta 合并提供，无需重复声明
      { path: 'shop/:id', component: UserShopDetail, meta: { title: '店铺详情' } },
      { path: 'cart', component: UserCart, meta: { title: '购物车' } },
      { path: 'checkout', component: UserCheckout, meta: { title: '确认订单' } },
      { path: 'order/:no', component: UserOrderDetail, meta: { title: '订单详情' } },
      { path: 'addresses', component: UserAddresses, meta: { title: '收货地址' } },
      { path: 'search', component: UserSearch, meta: { title: '搜索' } },
      { path: 'channel/:name', component: UserChannel, meta: { title: '频道' } },
      { path: 'favorites', component: UserFavorites, meta: { title: '我的收藏' } },
      { path: 'coupons/manage', component: UserCouponManage, meta: { title: '券管理' } },
      { path: 'service', component: UserService, meta: { title: '客服中心' } }
    ]
  },
  // ===== 商家端（布局壳 + 子路由，底部 5 Tab：订单/消息/店铺/推广/我的） =====
  {
    path: '/shop',
    component: ShopLayout,
    meta: { requiresAuth: true, roles: ['SHOP'] },
    children: [
      { path: '', redirect: '/shop/home' },
      { path: 'home', component: ShopHome, meta: { title: '商家首页' } },
      { path: 'orders', component: ShopOrders, meta: { title: '订单管理' } },
      { path: 'order/:no', component: ShopOrderDetail, meta: { title: '订单详情' } },
      { path: 'messages', component: ShopMessages, meta: { title: '消息中心' } },
      { path: 'store', component: ShopStore, meta: { title: '店铺设置' } },
      { path: 'dishes', component: ShopDishes, meta: { title: '菜品管理' } },
      { path: 'promote', component: ShopPromote, meta: { title: '推广中心' } },
      { path: 'me', component: ShopMe, meta: { title: '我的' } },
      { path: 'customers', component: ShopCustomers, meta: { title: '顾客管理' } },
      { path: 'reviews', component: ShopReviews, meta: { title: '评价管理' } }
    ]
  },
  // ===== 骑手端 =====
  { path: '/rider/login', redirect: '/portal' },
  { path: '/rider/tasks', component: RiderTasks, meta: { title: '骑手工作台', requiresAuth: true, roles: ['RIDER'] } },
  { path: '/rider/task/:no', component: RiderTaskDetail, meta: { title: '任务详情', requiresAuth: true, roles: ['RIDER'] } },
  { path: '/rider/income', component: RiderIncome, meta: { title: '收入统计', requiresAuth: true, roles: ['RIDER'] } },
  { path: '/rider/report', component: RiderReport, meta: { title: '异常上报', requiresAuth: true, roles: ['RIDER'] } },
  // ===== 管理端 =====
  { path: '/admin/login', redirect: '/portal' },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN'] },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', component: AdminDashboard, meta: { title: '运营数据看板' } },
      { path: 'users', component: AdminUsers, meta: { title: '用户管理' } },
      { path: 'shops', component: AdminShops, meta: { title: '商家管理' } },
      { path: 'riders', component: AdminRiders, meta: { title: '骑手管理' } },
      { path: 'orders', component: AdminOrders, meta: { title: '订单监控' } },
      { path: 'reviews', component: AdminReviewManage, meta: { title: '评价管理' } },
      { path: 'dispatch', component: AdminDispatch, meta: { title: '派单管理' } },
      { path: 'simulation', component: AdminSimulation, meta: { title: '策略仿真对比' } }
    ]
  },
  // ===== 403 =====
  { path: '/403', component: Forbidden, meta: { title: '无权限' } }
]

const router = createRouter({
  // 使用 hash 模式，静态部署无需服务端配合
  history: createWebHashHistory(),
  routes
})

// 全局前置守卫：登录校验 + 角色校验（位掩码）
// - 未登录访问 requiresAuth → /portal?redirect=原路径
// - 已登录但无任何 meta.roles 中声明的角色 → /403
// - /portal（含 /login、/rider/login、/admin/login 重定向）无需鉴权，正常放行
// meta.roles 仍为角色名数组（如 ['SHOP']），与 userInfo.roles 位掩码比对
const ROLE_BIT = { USER: 1, SHOP: 2, RIDER: 4, ADMIN: 8 }
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
  const mask = info.roles || 0
  if (to.meta.requiresAuth && !token) {
    // 未登录 → 门户页，携带原始路径便于登录后跳回
    next('/portal?redirect=' + encodeURIComponent(to.fullPath))
  } else if (to.meta.requiresAuth && to.meta.roles) {
    // 角色不符（位掩码与任一所需角色均无交集）→ 403
    const allowed = to.meta.roles.some((name) => (mask & (ROLE_BIT[name] || 0)) !== 0)
    if (!allowed) next('/403')
    else next()
  } else {
    next()
  }
})

// 路由切换后设置页面标题
router.afterEach((to) => {
  document.title = to.meta.title || '校园即时配送平台'
})

export default router
