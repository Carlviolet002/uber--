<template>
  <!-- 骑手收入统计（P1，演示数据 mock；保持与工作台一致的黄色风格） -->
  <div class="page">
    <div class="header">
      <div class="header-title">收入统计</div>
      <div class="header-sub">{{ nickname }}</div>
    </div>

    <!-- 汇总卡片 -->
    <div class="sum-cards">
      <div class="sum-card primary">
        <div class="sum-label">今日收入</div>
        <div class="sum-value">¥{{ summary.today }}</div>
        <div class="sum-sub">今日 {{ summary.todayOrders }} 单</div>
      </div>
      <div class="sum-card">
        <div class="sum-label">本周收入</div>
        <div class="sum-value">¥{{ summary.week }}</div>
        <div class="sum-sub">本周 {{ summary.weekOrders }} 单</div>
      </div>
      <div class="sum-card">
        <div class="sum-label">累计收入</div>
        <div class="sum-value">¥{{ summary.total }}</div>
        <div class="sum-sub">累计 {{ summary.totalOrders }} 单</div>
      </div>
    </div>

    <!-- 近 7 日明细 -->
    <van-cell-group inset title="近 7 日明细（演示数据）">
      <van-cell
        v-for="d in daily"
        :key="d.date"
        :title="d.date"
        :label="`完成 ${d.orders} 单`"
        :value="'¥' + d.amount"
      />
    </van-cell-group>

    <div class="tip">说明：收入为演示数据（后端暂无结算接口），仅用于展示骑手端 P1 页面。</div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const nickname = computed(
  () => userStore.userInfo.nickname || userStore.userInfo.username || '骑手'
)

// 演示数据（Q1：前端 mock）
const summary = ref({ today: '86.50', todayOrders: 12, week: '520.00', weekOrders: 68, total: '3860.00', totalOrders: 512 })

const daily = ref([
  { date: '今天', orders: 12, amount: '86.50' },
  { date: '昨天', orders: 15, amount: '102.00' },
  { date: '前天', orders: 9, amount: '63.50' },
  { date: '3 天前', orders: 11, amount: '78.00' },
  { date: '4 天前', orders: 10, amount: '70.00' },
  { date: '5 天前', orders: 8, amount: '58.00' },
  { date: '6 天前', orders: 3, amount: '62.00' }
])
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
  padding-bottom: 24px;
}
.header {
  padding: 24px 16px;
  background: var(--mt-gradient, linear-gradient(135deg, #ffc300, #ffb300));
  color: #fff;
}
.header-title {
  font-size: 22px;
  font-weight: 600;
}
.header-sub {
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.9;
}
.sum-cards {
  display: flex;
  gap: 10px;
  padding: 12px;
}
.sum-card {
  flex: 1;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  padding: 14px 10px;
  text-align: center;
}
.sum-card.primary {
  background: var(--mt-gradient, linear-gradient(135deg, #ffc300, #ffb300));
  color: #fff;
}
.sum-card.primary .sum-label,
.sum-card.primary .sum-sub {
  color: rgba(255, 255, 255, 0.9);
}
.sum-label {
  font-size: 12px;
  color: var(--mt-text-2, #666);
}
.sum-value {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
}
.sum-sub {
  margin-top: 4px;
  font-size: 11px;
  color: var(--mt-text-2, #666);
}
.tip {
  margin: 16px;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}
</style>
