<template>
  <!-- 券卡公共组件：神券频道/券管理/Checkout 选券/领券中心共用 -->
  <div
    class="coupon-card"
    :class="[coupon.type, { selected, disabled: isDisabled || (mode === 'claim' && coupon.claimed) }]"
    @click="emit('click', coupon)"
  >
    <div class="coupon-left">
      <div class="coupon-amount">
        <span class="coupon-yen">¥</span>
        <span class="coupon-num">{{ coupon.amount }}</span>
      </div>
      <div class="coupon-desc">{{ coupon.desc }}</div>
    </div>
    <div class="coupon-right">
      <div class="coupon-scope">{{ coupon.shopName || coupon.scope }}</div>
      <div v-if="mode === 'claim'" class="coupon-use" @click.stop="emit('claim', coupon)">
        {{ coupon.claimed ? '已领取' : '领取' }}
      </div>
      <div v-else-if="mode === 'select' && selected" class="coupon-check">✓</div>
      <div v-else-if="mode !== 'claim'" class="coupon-use">{{ statusText }}</div>
    </div>
    <!-- 即将过期角标：仅剩 X 小时 -->
    <span v-if="expireBadge" class="coupon-badge">{{ expireBadge }}</span>
    <!-- 已使用/已过期水印 -->
    <span v-if="isDisabled" class="coupon-watermark">{{ watermarkText }}</span>
  </div>
</template>

<script setup>
// 券状态兼容：A 批无 status 字段时视为可用；B 批接入 status/expireAt/usedAt 后自动生效
import { computed } from 'vue'

const props = defineProps({
  coupon: { type: Object, required: true },
  mode: { type: String, default: 'normal' }, // normal | select | claim
  selected: { type: Boolean, default: false }
})

const emit = defineEmits(['click', 'claim'])

const isDisabled = computed(() => ['used', 'expired'].includes(props.coupon.status))

const watermarkText = computed(() =>
  props.coupon.status === 'used' ? '已使用' : props.coupon.status === 'expired' ? '已过期' : ''
)

// 即将过期：expireAt 距现在不足 24 小时 → 「仅剩 X 小时」
const expireBadge = computed(() => {
  if (!props.coupon.expireAt || isDisabled.value) return ''
  const hours = Math.max(1, Math.ceil((props.coupon.expireAt - Date.now()) / 3600000))
  if (hours <= 24) return '仅剩 ' + hours + ' 小时'
  return ''
})

const statusText = computed(() => {
  if (isDisabled.value) return watermarkText.value
  return '去使用'
})
</script>

<style scoped>
.coupon-card {
  flex-shrink: 0;
  width: 150px;
  min-height: 92px;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: transform 0.15s ease, opacity 0.2s ease;
}
.coupon-card:active {
  transform: scale(0.97);
}
.coupon-card.red {
  background: linear-gradient(135deg, #ff6b6b, #ff5000);
  color: #fff;
}
.coupon-card.shop {
  background: #fff;
  border: 1px solid #ffe0b2;
  color: #333;
}
.coupon-card.disabled {
  opacity: 0.55;
  filter: grayscale(0.4);
}
.coupon-card.selected {
  border: 2px solid var(--mt-yellow);
  box-shadow: 0 0 0 2px rgba(255, 195, 0, 0.25);
}
.coupon-left {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.coupon-amount {
  display: flex;
  align-items: baseline;
}
.coupon-yen {
  font-size: 13px;
  font-weight: 700;
}
.coupon-num {
  font-size: 30px;
  font-weight: 800;
}
.coupon-card.shop .coupon-num {
  color: #ff5000;
}
.coupon-desc {
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.9;
}
.coupon-right {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
}
.coupon-scope {
  font-size: 9px;
  writing-mode: vertical-rl;
  opacity: 0.85;
  letter-spacing: 1px;
}
.coupon-card.shop .coupon-scope {
  color: var(--mt-text-3);
}
.coupon-use {
  font-size: 10px;
  font-weight: 600;
  background: var(--mt-yellow);
  color: #333;
  border-radius: 10px;
  padding: 2px 8px;
}
.coupon-card.red .coupon-use {
  background: rgba(255, 255, 255, 0.9);
  color: #ff5000;
}
.coupon-card.disabled .coupon-use {
  background: #e5e5e5;
  color: #999;
}
.coupon-check {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--mt-yellow);
  color: #333;
  font-size: 13px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 即将过期角标：右上角 */
.coupon-badge {
  position: absolute;
  right: 0;
  top: 0;
  font-size: 9px;
  font-weight: 600;
  color: #fff;
  background: #ff9a14;
  border-radius: 0 12px 0 8px;
  padding: 2px 6px;
  z-index: 1;
}
/* 已使用/已过期水印：斜置印章 */
.coupon-watermark {
  position: absolute;
  right: 6px;
  bottom: 30px;
  font-size: 12px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.35);
  border: 2px solid rgba(0, 0, 0, 0.3);
  border-radius: 6px;
  padding: 1px 6px;
  transform: rotate(-15deg);
  z-index: 1;
}
.coupon-card.red .coupon-watermark {
  color: rgba(255, 255, 255, 0.8);
  border-color: rgba(255, 255, 255, 0.8);
}
</style>
