<template>
  <!-- 我的：黄渐变头部 + 黄底钱包卡（今日/本月/累计收入 count-up）+ 菜单 + 退出登录 -->
  <div class="page">
    <!-- 黄渐变头部 + 钱包卡 -->
    <div class="me-header">
      <div class="me-user">
        <div class="avatar">🏪</div>
        <div class="me-info">
          <div class="me-name">{{ stats.shopInfo.name || '我的店铺' }}</div>
          <div class="me-sub">{{ nickname }} · {{ stats.shopInfo.address || '' }}</div>
        </div>
      </div>
      <!-- 钱包卡 -->
      <div class="wallet-card">
        <div class="wallet-label">💰 经营收入</div>
        <div class="wallet-row">
          <div class="wallet-item">
            <div class="wallet-value">¥{{ todayFmt }}</div>
            <div class="wallet-name">今日收入</div>
          </div>
          <div class="wallet-item">
            <div class="wallet-value">¥{{ monthFmt }}</div>
            <div class="wallet-name">本月收入</div>
          </div>
          <div class="wallet-item">
            <div class="wallet-value">¥{{ totalFmt }}</div>
            <div class="wallet-name">累计收入</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 菜单组 -->
    <div class="menu-card">
      <div class="menu-item" @click="router.push('/shop/reviews')">
        <span class="menu-icon">⭐</span><span class="menu-name">评价管理</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
      <div class="menu-item" @click="router.push('/shop/customers')">
        <span class="menu-icon">👥</span><span class="menu-name">顾客管理</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
      <div class="menu-item" @click="router.push('/shop/store')">
        <span class="menu-icon">🏪</span><span class="menu-name">店铺设置</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
      <div class="menu-item" @click="router.push('/shop/promote')">
        <span class="menu-icon">📣</span><span class="menu-name">推广中心</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
    </div>

    <div class="menu-card">
      <div class="menu-item" @click="onSwitchAccount">
        <span class="menu-icon">🔁</span><span class="menu-name">切换账号</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
      <div class="menu-item" @click="onLogout">
        <span class="menu-icon">🚪</span><span class="menu-name" style="color: #ee0a24">退出登录</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
    </div>
  </div>
</template>

<script setup>
// 我的：收入三列由已完成订单前端聚合（count-up），切换账号/退出复用门户逻辑
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '../../stores/user'
import { useShopStatsStore } from '../../stores/shopStats'
import { fetchAllOrders, incomeSummary } from './composables/useShopStats'
import { useCountUp } from './composables/useCountUp'

const router = useRouter()
const userStore = useUserStore()
const stats = useShopStatsStore()

const nickname = computed(
  () => userStore.userInfo.nickname || userStore.userInfo.username || '商家'
)

const income = ref({ today: 0, month: 0, total: 0 })
const todayC = useCountUp(() => income.value.today, { decimals: 0, duration: 900 })
const monthC = useCountUp(() => income.value.month, { decimals: 0, duration: 900 })
const totalC = useCountUp(() => income.value.total, { decimals: 0, duration: 900 })
const todayFmt = computed(() => todayC.format(0))
const monthFmt = computed(() => monthC.format(0))
const totalFmt = computed(() => totalC.format(0))

// 切换账号：清登录态回门户
const onSwitchAccount = async () => {
  try {
    await showConfirmDialog({ title: '切换账号', message: '切换账号将退出当前登录，返回门户页' })
  } catch (e) {
    return
  }
  userStore.logout()
  showToast('已退出登录')
  router.push('/portal')
}

const onLogout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确认退出登录吗？' })
  } catch (e) {
    return
  }
  userStore.logout()
  showToast('已退出登录')
  router.push('/portal')
}

onMounted(async () => {
  try {
    const orders = await fetchAllOrders(await stats.ensureShopId())
    income.value = incomeSummary(orders)
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: 70px;
}
.me-header {
  padding: 20px 16px 18px;
  background: var(--mt-gradient, linear-gradient(135deg, #ffc300, #ffb300));
  border-radius: 0 0 20px 20px;
}
.me-user {
  display: flex;
  align-items: center;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.me-info {
  margin-left: 12px;
}
.me-name {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}
.me-sub {
  margin-top: 3px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}
.wallet-card {
  margin-top: 16px;
  background: rgba(0, 0, 0, 0.28);
  border-radius: 14px;
  padding: 12px 10px;
}
.wallet-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 8px;
}
.wallet-row {
  display: flex;
}
.wallet-item {
  flex: 1;
  text-align: center;
}
.wallet-value {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}
.wallet-name {
  margin-top: 3px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.8);
}
.menu-card {
  margin: 10px 12px 0;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  padding: 13px 14px;
  border-bottom: 1px solid #f7f8fa;
  cursor: pointer;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-icon {
  width: 24px;
  margin-right: 10px;
  text-align: center;
}
.menu-name {
  flex: 1;
  font-size: 14px;
  color: var(--mt-text, #222);
}
</style>
