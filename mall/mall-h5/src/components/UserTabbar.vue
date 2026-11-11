<template>
  <!-- 用户端底部导航：首页 / 订单 / 购物车 / 我的（地址页），选中色美团黄 -->
  <div class="user-tabbar">
    <van-tabbar
      fixed
      :model-value="active"
      :style="offset ? { bottom: offset + 'px' } : {}"
      active-color="#FFC300"
      inactive-color="#999999"
      @change="onChange"
    >
      <van-tabbar-item v-for="(t, i) in tabs" :key="t.path" :icon="t.icon">{{ t.name }}</van-tabbar-item>
    </van-tabbar>
    <!-- 占位块：撑开页面底部，避免内容被固定 tabbar 遮挡 -->
    <div class="user-tabbar__placeholder" :style="{ height: 50 + offset + 'px' }"></div>
  </div>
</template>

<script setup>
// 用户端底部导航：根据当前路由高亮对应 tab，点击跳转
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

defineProps({
  // 底部上移距离（px）：页面存在其他固定底栏时使用，避免遮挡
  offset: { type: Number, default: 0 }
})

const route = useRoute()
const router = useRouter()

// tab 配置：顺序即高亮下标
const tabs = [
  { name: '首页', path: '/user/shops', icon: 'shop-o' },
  { name: '订单', path: '/user/orders', icon: 'orders-o' },
  { name: '购物车', path: '/user/cart', icon: 'cart-o' },
  { name: '我的', path: '/user/addresses', icon: 'user-o' }
]

// 当前高亮 tab：订单详情页归属“订单”tab
const active = computed(() => {
  const p = route.path
  if (p === '/user/shops') return 0
  if (p === '/user/orders' || p.startsWith('/user/order/')) return 1
  if (p === '/user/cart') return 2
  if (p === '/user/addresses') return 3
  return 0
})

// 点击 tab 跳转对应页面
const onChange = (index) => {
  const t = tabs[index]
  if (t && t.path !== route.path) {
    router.push(t.path)
  }
}
</script>

<style scoped>
.user-tabbar :deep(.van-tabbar-item__icon) {
  font-size: 20px;
}
</style>
