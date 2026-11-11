<template>
  <!-- 商家首页数据概览：黄渐变品牌区 + 三列核心数据(count-up) + 近7日趋势 + 金刚区 -->
  <div class="page">
    <!-- 黄渐变品牌区（全站唯一大块黄） -->
    <div class="hero">
      <div class="hero-top">
        <div>
          <div class="hero-name">{{ stats.shopInfo.name || '我的店铺' }}</div>
          <div class="hero-date">{{ todayLabel }} · {{ greeting }}</div>
        </div>
        <van-tag round :color="stats.shopInfo.status === 1 ? '#07c160' : '#969799'">
          {{ stats.shopInfo.status === 1 ? '营业中' : '休息中' }}
        </van-tag>
      </div>
    </div>

    <!-- 今日概览：三列核心数据 -->
    <div class="overview-card">
      <van-skeleton v-if="loading" title :row="2" />
      <div v-else class="overview-row">
        <div class="overview-item">
          <div class="overview-value">¥{{ revenueFmt }}</div>
          <div class="overview-label">今日营业额</div>
        </div>
        <div class="overview-divider"></div>
        <div class="overview-item">
          <div class="overview-value">{{ countFmt }}</div>
          <div class="overview-label">今日订单数</div>
        </div>
        <div class="overview-divider"></div>
        <div class="overview-item">
          <div class="overview-value">¥{{ avgFmt }}</div>
          <div class="overview-label">客单价</div>
        </div>
      </div>
    </div>

    <!-- 近 7 日营业额趋势（mock 假数据） -->
    <div class="section-card">
      <div class="section-head">
        <span class="section-title">近 7 日营业额</span>
        <span class="section-sub">演示数据</span>
      </div>
      <van-skeleton v-if="loading" :row="3" />
      <div v-else class="trend-chart">
        <div v-for="(d, i) in trend" :key="d.date" class="trend-col">
          <div class="trend-amount">¥{{ d.amount }}</div>
          <div class="trend-bar-wrap">
            <div
              class="trend-bar"
              :class="{ today: i === trend.length - 1 }"
              :style="{ height: barHeight(d.amount) + '%' }"
            ></div>
          </div>
          <div class="trend-date" :class="{ today: i === trend.length - 1 }">{{ d.date }}</div>
        </div>
      </div>
    </div>

    <!-- 金刚区 4×4 -->
    <div class="section-card">
      <div class="section-head">
        <span class="section-title">常用功能</span>
      </div>
      <div class="grid">
        <div v-for="g in gridItems" :key="g.name" class="grid-item" :class="{ disabled: g.disabled }" @click="onGrid(g)">
          <div class="grid-icon" :style="{ background: g.bg }">{{ g.icon }}</div>
          <div class="grid-name">{{ g.name }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 首页数据概览：三列核心数据由真实订单前端聚合（count-up 动画），趋势图用 shopMock 假数据
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useShopStatsStore } from '../../stores/shopStats'
import { fetchAllOrders, todayStats } from './composables/useShopStats'
import { useCountUp } from './composables/useCountUp'
import { getTrend7d } from '../../mock/shopMock'

const router = useRouter()
const stats = useShopStatsStore()

const loading = ref(true)
const orders = ref([])
const trend = ref([])

const todayLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了，注意休息'
  if (h < 12) return '上午好，生意兴隆'
  if (h < 18) return '下午好，再接再厉'
  return '晚上好，今日辛苦'
})

// 三列核心数据（count-up 动画）
const today = computed(() => todayStats(orders.value))
const revenueC = useCountUp(() => today.value.revenue, { decimals: 0, duration: 900 })
const countC = useCountUp(() => today.value.count, { decimals: 0, duration: 900 })
const avgC = useCountUp(() => today.value.avg, { decimals: 2, duration: 900 })
const revenueFmt = computed(() => revenueC.format(0))
const countFmt = computed(() => countC.format(0))
const avgFmt = computed(() => avgC.format(2))

// 柱状图高度：按最大值归一（40%~100%）
const maxAmount = computed(() => Math.max(...trend.value.map((d) => d.amount), 1))
const barHeight = (v) => Math.round((v / maxAmount.value) * 60 + 40)

const gridItems = [
  { name: '订单管理', icon: '📋', bg: '#FFF3CC', path: '/shop/orders' },
  { name: '菜品管理', icon: '🍜', bg: '#FFE9E0', path: '/shop/dishes' },
  { name: '评价管理', icon: '⭐', bg: '#E8F8EF', path: '/shop/reviews' },
  { name: '顾客管理', icon: '👥', bg: '#E8F0FF', path: '/shop/customers' },
  { name: '消息中心', icon: '💬', bg: '#F3E8FF', path: '/shop/messages' },
  { name: '店铺设置', icon: '🏪', bg: '#FFF0E0', path: '/shop/store' },
  { name: '推广中心', icon: '📣', bg: '#FFE8EC', path: '/shop/promote' },
  { name: '营业助手', icon: '🔧', bg: '#F0F0F0', disabled: true }
]

const onGrid = (g) => {
  if (g.disabled) return
  router.push(g.path)
}

onMounted(async () => {
  const shopId = await stats.ensureShopId()
  try {
    const [all, t] = await Promise.all([fetchAllOrders(shopId), getTrend7d()])
    orders.value = all
    trend.value = t
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: 70px;
}
.hero {
  padding: 20px 16px 24px;
  background: var(--mt-gradient, linear-gradient(135deg, #ffc300, #ffb300));
  border-radius: 0 0 20px 20px;
}
.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hero-name {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.hero-date {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}
.overview-card {
  margin: -12px 12px 0;
  padding: 16px 8px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
  position: relative;
  z-index: 1;
}
.overview-row {
  display: flex;
  align-items: center;
}
.overview-item {
  flex: 1;
  text-align: center;
}
.overview-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--mt-text, #222);
}
.overview-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mt-text-3, #999);
}
.overview-divider {
  width: 1px;
  height: 28px;
  background: #f0f0f0;
}
.section-card {
  margin: 10px 12px 0;
  padding: 14px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.section-sub {
  font-size: 12px;
  color: var(--mt-text-3, #999);
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 140px;
}
.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.trend-amount {
  font-size: 10px;
  color: var(--mt-text-3, #999);
  margin-bottom: 4px;
}
.trend-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.trend-bar {
  width: 55%;
  min-height: 6px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, #ffe180, #ffc300);
  opacity: 0.55;
}
.trend-bar.today {
  opacity: 1;
  background: linear-gradient(180deg, #ffc300, #ff9a14);
}
.trend-date {
  margin-top: 6px;
  font-size: 10px;
  color: var(--mt-text-3, #999);
}
.trend-date.today {
  color: #ff9a14;
  font-weight: 600;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  row-gap: 16px;
}
.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
}
.grid-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.grid-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.grid-name {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mt-text-2, #666);
}
</style>
