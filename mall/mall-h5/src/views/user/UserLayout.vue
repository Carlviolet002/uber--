<template>
  <!-- 用户端布局壳：4 Tab 底部导航（外卖/神券/订单/我的），订单 Tab 带进行中角标，30s 轮询刷新 -->
  <div class="user-layout">
    <!-- keep-alive：Tab 切换不丢状态/滚动位置；组件名由各 Tab 页 defineOptions 注册 -->
    <router-view v-slot="{ Component }">
      <keep-alive include="UserHome,UserCoupons,UserOrders,UserMe">
        <component :is="Component" />
      </keep-alive>
    </router-view>
    <van-tabbar
      fixed
      :model-value="active"
      active-color="#FFC300"
      inactive-color="#999999"
      @change="onChange"
    >
      <van-tabbar-item icon="shop-o">外卖</van-tabbar-item>
      <van-tabbar-item>
        <template #icon>
          <span class="god-icon" :class="{ on: active === 1 }">神</span>
        </template>
        神券
      </van-tabbar-item>
      <van-tabbar-item icon="orders-o" :badge="pendingCount > 0 ? pendingCount : ''">订单</van-tabbar-item>
      <van-tabbar-item icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
    <!-- 占位块：撑开页面底部，避免内容被固定 tabbar 遮挡 -->
    <div class="user-layout__placeholder"></div>
  </div>
</template>

<script setup>
// 用户端布局壳：按当前路由高亮 tab；进行中订单数（2/3/9/4/5/11）作订单角标，30s 轮询
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderList } from '../../api'

const route = useRoute()
const router = useRouter()

const tabs = [
  { name: '外卖', path: '/user/home' },
  { name: '神券', path: '/user/coupons' },
  { name: '订单', path: '/user/orders' },
  { name: '我的', path: '/user/me' }
]

// 当前高亮 tab：4 个 Tab 页精确匹配，二级页按归属映射到对应 Tab
// （避免进入订单详情/地址页时一律错误高亮「外卖」）
const active = computed(() => {
  const p = route.path
  if (p === '/user/coupons' || p === '/user/coupons/manage') return 1
  if (p === '/user/orders' || p.startsWith('/user/order/')) return 2
  if (p === '/user/me' || p === '/user/addresses' || p === '/user/service' || p === '/user/favorites') return 3
  return 0
})

const pendingCount = ref(0)
let timer = null

// 进行中状态：2 待接单 / 3 派单中 / 9 备餐中 / 4 待取餐 / 5 配送中 / 11 已送达待确认
const refreshPending = async () => {
  try {
    const data = await orderList({ page: 1, pageSize: 200 })
    const list = (data && data.records) || []
    pendingCount.value = list.filter((o) => [2, 3, 9, 4, 5, 11].includes(Number(o.status))).length
  } catch (e) {
    // 接口异常保持旧值
  }
}

const onChange = (index) => {
  const t = tabs[index]
  if (t && t.path !== route.path) {
    router.push(t.path)
  }
}

onMounted(() => {
  refreshPending()
  timer = setInterval(refreshPending, 30000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.user-layout {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg);
}
.user-layout__placeholder {
  height: 50px;
}
/* 神券 tab 图标：黄色渐变圆底 + 黑"神"字，选中态亮黄 */
.god-icon {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: #333;
  background: linear-gradient(135deg, #ffe082, #ffc300);
  border-radius: 6px;
  display: inline-block;
}
.god-icon.on {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px rgba(255, 195, 0, 0.4);
}
</style>
