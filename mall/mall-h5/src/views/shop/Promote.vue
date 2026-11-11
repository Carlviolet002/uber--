<template>
  <!-- 推广中心（P0 静态版）：收益概览 + 活动卡片（满减/新客立减/推广位），数据来自 shopMock -->
  <div class="page">
    <!-- 收益概览 -->
    <div class="income-card">
      <div class="income-title">推广收益概览</div>
      <div class="income-row">
        <div class="income-item">
          <div class="income-value">0</div>
          <div class="income-label">推广订单（单）</div>
        </div>
        <div class="income-item">
          <div class="income-value">0</div>
          <div class="income-label">曝光（次）</div>
        </div>
        <div class="income-item">
          <div class="income-value">¥0.00</div>
          <div class="income-label">推广花费</div>
        </div>
      </div>
      <div class="income-tip">演示数据 · 待接营销服务</div>
    </div>

    <!-- 活动卡片 -->
    <van-skeleton v-if="loading" title :row="3" style="margin: 12px" />
    <div v-else v-for="p in promos" :key="p.id" class="promo-card">
      <div class="promo-head">
        <span class="promo-icon">{{ iconOf(p.type) }}</span>
        <div class="promo-info">
          <div class="promo-title">{{ p.title }}</div>
          <div class="promo-desc">{{ p.desc }}</div>
        </div>
        <van-tag :type="p.enabled ? 'warning' : 'default'" plain>{{ p.enabled ? p.tag : '已暂停' }}</van-tag>
      </div>
      <div class="promo-foot">
        <span class="promo-time">活动时间：{{ p.time }}</span>
        <van-switch
          :model-value="p.enabled"
          size="20px"
          active-color="#FFC300"
          @update:model-value="(v) => onToggle(p, v)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
// 推广中心：静态 mock 展示页（P0），活动开关状态存 localStorage
import { ref, onMounted } from 'vue'
import { showToast } from 'vant'
import { getPromotions, togglePromotion } from '../../mock/shopMock'

const loading = ref(true)
const promos = ref([])

const iconOf = (type) => {
  const map = { 'full-reduce': '🎟️', 'new-customer': '🧧', banner: '📣' }
  return map[type] || '📣'
}

const onToggle = (p, v) => {
  togglePromotion(p.id, v)
  p.enabled = v
  showToast(v ? '活动已开启' : '活动已暂停')
}

onMounted(async () => {
  try {
    promos.value = await getPromotions()
  } catch (e) {
    // mock 层无网络异常，此处仅兜底
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
.income-card {
  margin: 8px 12px;
  padding: 16px 14px;
  background: var(--mt-gradient, linear-gradient(135deg, #ffc300, #ffb300));
  border-radius: var(--mt-radius, 16px);
}
.income-title {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}
.income-row {
  display: flex;
  margin-top: 14px;
}
.income-item {
  flex: 1;
  text-align: center;
}
.income-value {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}
.income-label {
  margin-top: 3px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
}
.income-tip {
  margin-top: 12px;
  text-align: center;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
}
.promo-card {
  margin: 8px 12px;
  padding: 12px 14px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.promo-head {
  display: flex;
  align-items: center;
}
.promo-icon {
  font-size: 26px;
  margin-right: 10px;
}
.promo-info {
  flex: 1;
  min-width: 0;
}
.promo-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.promo-desc {
  margin-top: 3px;
  font-size: 12px;
  color: var(--mt-text-3, #999);
}
.promo-foot {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f7f8fa;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.promo-time {
  font-size: 11px;
  color: var(--mt-text-3, #999);
}
</style>
