<template>
  <!-- 商家端布局壳：白底品牌条 + 页面内容 + 底部 5 Tab 导航（订单/消息/店铺/推广/我的） -->
  <div class="shop-layout">
    <!-- 顶部白底品牌条 -->
    <div class="shop-header">
      <div class="header-left" @click="router.push('/shop/home')">
        <span class="header-logo">M</span>
        <span class="header-title">商家中心</span>
        <span v-if="stats.shopInfo.name" class="header-shop">· {{ stats.shopInfo.name }}</span>
      </div>
      <div class="header-right" @click="router.push('/shop/store')">
        <span :class="['status-dot', stats.shopInfo.status === 1 ? 'on' : 'off']"></span>
        <span class="status-text">{{ stats.shopInfo.status === 1 ? '营业中' : '休息中' }}</span>
      </div>
    </div>

    <!-- 页面内容 -->
    <div class="shop-body">
      <router-view />
    </div>

    <!-- 底部 5 Tab 导航 -->
    <van-tabbar
      fixed
      :model-value="activeTab"
      active-color="#FFC300"
      inactive-color="#999999"
      @change="onTabChange"
    >
      <van-tabbar-item icon="orders-o" :badge="stats.pendingCount || ''">订单</van-tabbar-item>
      <van-tabbar-item icon="chat-o" :badge="stats.unreadCount ? '' : ''" :dot="stats.unreadCount > 0">消息</van-tabbar-item>
      <van-tabbar-item icon="shop-o">店铺</van-tabbar-item>
      <van-tabbar-item icon="gift-o">推广</van-tabbar-item>
      <van-tabbar-item icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
// 商家端布局壳：30s 轮询待接单数（订单角标），shopInfo 缓存当前店铺信息（头部营业状态）
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useShopStatsStore } from '../../stores/shopStats'
import { shopDetail } from '@/api'

const route = useRoute()
const router = useRouter()
const stats = useShopStatsStore()

// Tab 配置与路径映射
const TABS = [
  { name: '订单', path: '/shop/orders', match: ['/shop/orders', '/shop/order/'] },
  { name: '消息', path: '/shop/messages', match: ['/shop/messages'] },
  { name: '店铺', path: '/shop/store', match: ['/shop/store', '/shop/dishes'] },
  { name: '推广', path: '/shop/promote', match: ['/shop/promote'] },
  { name: '我的', path: '/shop/me', match: ['/shop/me', '/shop/customers', '/shop/reviews'] }
]

// 当前高亮 Tab：首页概览不归属任何 Tab（返回 -1 全不高亮）
const activeTab = computed(() => {
  const p = route.path
  if (p === '/shop/home') return -1
  const idx = TABS.findIndex((t) => t.match.some((m) => p === m || p.startsWith(m)))
  return idx > -1 ? idx : 0
})

const onTabChange = (index) => {
  const t = TABS[index]
  if (t && t.path !== route.path) {
    router.push(t.path)
  }
}

let pollTimer = null

onMounted(async () => {
  // 解析 shopId + 加载店铺信息（头部营业状态）
  const shopId = await stats.ensureShopId()
  try {
    stats.shopInfo = (await shopDetail(shopId)) || {}
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
  // 布局级轮询：待接单角标 + 消息未读红点 30s 刷新（refreshUnread 内置 real→mock 静默降级）
  await stats.refreshPending()
  stats.refreshUnread()
  pollTimer = setInterval(() => {
    stats.refreshPending()
    stats.refreshUnread()
  }, 30000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.shop-layout {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
}
.shop-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}
.header-left {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.header-logo {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--mt-gradient, linear-gradient(135deg, #ffc300, #ffb300));
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.header-shop {
  font-size: 13px;
  color: var(--mt-text-3, #999);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.header-right {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}
.status-dot.on {
  background: var(--mt-success, #06ae56);
}
.status-dot.off {
  background: #c8c9cc;
}
.status-text {
  font-size: 13px;
  color: var(--mt-text-2, #666);
}
.shop-body {
  /* tabbar 高度 50px + 安全区，底部留白由各页面 page 类 padding-bottom 保证 */
  min-height: calc(100vh - 48px);
}
.shop-layout :deep(.van-tabbar) {
  max-width: 750px;
  left: 50%;
  transform: translateX(-50%);
}
.shop-layout :deep(.van-tabbar-item__icon) {
  font-size: 20px;
}
</style>
