<template>
  <!-- 订单详情：状态流程条 + 订单信息 + 菜品明细 + 备注/催单记录 + 状态操作按钮 -->
  <div class="page">
    <!-- 返回 + 标题 -->
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="5" style="margin: 12px" />
    <template v-else>
      <!-- 状态流程条 -->
      <div class="card">
        <div class="card-title">{{ order.statusName || '订单状态' }}</div>
        <div class="steps">
          <div
            v-for="(s, i) in steps"
            :key="s.label"
            class="step"
            :class="{ done: i <= currentStep }"
          >
            <div class="step-dot" :class="{ on: i === currentStep }"></div>
            <div class="step-label">{{ s.label }}</div>
            <div v-if="i < steps.length - 1" class="step-line" :class="{ on: i < currentStep }"></div>
          </div>
        </div>
      </div>

      <!-- 菜品明细 -->
      <div class="card">
        <div class="card-title">菜品明细</div>
        <div v-for="(it, i) in items" :key="i" class="dish-row">
          <span class="dish-name">{{ it.dishName }}</span>
          <span class="dish-num">x{{ it.num }}</span>
          <span class="dish-price">¥{{ Number(it.unitPrice || 0).toFixed(2) }}</span>
        </div>
        <div class="sum-row">
          <span>配送费</span>
          <span>¥{{ Number(order.deliveryFee || 0).toFixed(2) }}</span>
        </div>
        <div class="sum-row total">
          <span>合计</span>
          <span>¥{{ Number(order.total || 0).toFixed(2) }}</span>
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="card">
        <div class="card-title">订单信息</div>
        <div class="info-row"><span class="info-label">订单编号</span><span>{{ order.orderNo }}</span></div>
        <div class="info-row"><span class="info-label">下单时间</span><span>{{ fmtTime(order.createdAt) }}</span></div>
        <div class="info-row"><span class="info-label">{{ order.deliveryType === 'PICKUP' ? '期望自提' : '期望送达' }}</span><span>{{ order.expectTime || '-' }}</span></div>
        <div class="info-row"><span class="info-label">订单备注</span><span>{{ order.remark || '无' }}</span></div>
        <div class="info-row"><span class="info-label">餐具份数</span><span>{{ order.tableware || 0 }} 份</span></div>
        <div class="info-row">
          <span class="info-label">催单记录</span>
          <span :class="{ urge: Number(order.urgeCount) > 0 }">
            {{ Number(order.urgeCount) > 0 ? order.urgeCount + ' 次（最近 ' + fmtTime(order.urgeLastTime) + '）' : '无' }}
          </span>
        </div>
        <div class="info-row"><span class="info-label">骑手</span><span>{{ order.riderId ? '骑手 #' + order.riderId : '待指派' }}</span></div>
      </div>

      <!-- 状态流转日志 -->
      <div v-if="logs.length" class="card">
        <div class="card-title">状态记录</div>
        <div v-for="(l, i) in logs" :key="i" class="log-row">
          <span class="log-time">{{ fmtTime(l.time) }}</span>
          <span class="log-text">{{ logText(l) }}</span>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div v-if="actionBtns().length" class="bottom-bar">
        <van-button
          v-for="b in actionBtns()"
          :key="b.text"
          round
          block
          :type="b.type"
          :class="b.type === 'primary' ? 'btn-yellow' : 'btn-gray'"
          :loading="acting"
          @click="b.handler()"
        >
          {{ b.text }}
        </van-button>
      </div>
      <div class="bottom-placeholder"></div>
    </template>
  </div>
</template>

<script setup>
// 订单详情：SHOP 角色可直接调 /api/order/{no} 详情（后端仅 USER 限制本人可见）
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { orderDetail, shopAccept, shopReject, shopFinishCooking } from '@/api'
import { useShopStatsStore } from '../../stores/shopStats'

const router = useRouter()
const route = useRoute()
const stats = useShopStatsStore()

const loading = ref(true)
const acting = ref(false)
const order = ref({})
const items = ref([])
const logs = ref([])

const steps = [
  { label: '待接单', at: 2 },
  { label: '备餐中', at: 9 },
  { label: '待取餐', at: 4 },
  { label: '配送中', at: 5 },
  { label: '已送达', at: 11 },
  { label: '已完成', at: 6 }
]

// 当前流程位置：按订单状态在 steps 中的下标；已取消/售后/待支付等特殊状态取 0
const currentStep = computed(() => {
  const s = Number(order.value.status)
  const idx = steps.findIndex((x) => x.at === s)
  if (idx > -1) return idx
  // 已送达确认(11)前的状态按 5 处理
  if (s === 5) return 3
  return 0
})

const fmtTime = (t) => (t ? String(t).slice(0, 19).replace('T', ' ') : '')

const STATUS_NAMES = { 1: '待支付', 2: '待接单', 3: '派单中', 4: '待取餐', 5: '配送中', 6: '已完成', 7: '已取消', 8: '已退款', 9: '备餐中', 11: '已送达待确认' }
const OPERATOR_NAMES = { USER: '用户', SHOP: '商家', RIDER: '骑手', SYSTEM: '系统' }

const logText = (l) => {
  const from = l.fromStatus == null ? '创建' : STATUS_NAMES[l.fromStatus] || l.fromStatus
  const to = STATUS_NAMES[l.toStatus] || l.toStatus
  return `${OPERATOR_NAMES[l.operatorType] || l.operatorType || '系统'}：${from} → ${to}`
}

const actionBtns = () => {
  const s = Number(order.value.status)
  if (s === 2) {
    return [
      { text: '接单', type: 'primary', handler: onAccept },
      { text: '拒单', type: 'default', handler: onReject }
    ]
  }
  if (s === 9) {
    return [{ text: '出餐完成', type: 'primary', handler: onFinish }]
  }
  return []
}

const reload = async () => {
  try {
    const d = await orderDetail(route.params.no)
    order.value = d.order || {}
    items.value = d.items || []
    logs.value = d.logs || []
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

const onAccept = async () => {
  acting.value = true
  try {
    await shopAccept(order.value.orderNo)
    showToast('接单成功')
    stats.refreshPending()
    await reload()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    acting.value = false
  }
}

const onReject = async () => {
  try {
    await showConfirmDialog({ title: '拒单确认', message: '确认拒绝该订单吗？' })
  } catch (e) {
    return
  }
  acting.value = true
  try {
    await shopReject(order.value.orderNo, '商家拒单')
    showToast('已拒单')
    stats.refreshPending()
    await reload()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    acting.value = false
  }
}

const onFinish = async () => {
  acting.value = true
  try {
    await shopFinishCooking(order.value.orderNo)
    showToast('出餐完成，等待骑手取餐')
    await reload()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    acting.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
}
.card {
  margin: 8px 12px;
  padding: 14px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-text, #222);
  margin-bottom: 10px;
}
.steps {
  display: flex;
  align-items: flex-start;
}
.step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}
.step-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #e5e5e5;
  z-index: 1;
}
.step-dot.on {
  background: var(--mt-yellow, #ffc300);
  box-shadow: 0 0 0 3px #fff3cc;
}
.step.done .step-dot {
  background: var(--mt-yellow, #ffc300);
}
.step-label {
  margin-top: 6px;
  font-size: 11px;
  color: var(--mt-text-3, #999);
}
.step.done .step-label {
  color: var(--mt-text, #222);
  font-weight: 600;
}
.step-line {
  position: absolute;
  top: 5px;
  left: calc(-50% + 6px);
  right: calc(50% + 6px);
  height: 2px;
  background: #e5e5e5;
}
.step-line.on {
  background: var(--mt-yellow, #ffc300);
}
.dish-row {
  display: flex;
  align-items: center;
  font-size: 14px;
  padding: 5px 0;
  color: var(--mt-text, #222);
}
.dish-name {
  flex: 1;
}
.dish-num {
  margin: 0 10px;
  color: var(--mt-text-3, #999);
}
.dish-price {
  font-weight: 600;
}
.sum-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--mt-text-2, #666);
  padding-top: 6px;
}
.sum-row.total {
  font-size: 15px;
  font-weight: 700;
  color: var(--mt-text, #222);
  border-top: 1px solid #f5f5f5;
  margin-top: 6px;
  padding-top: 10px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 5px 0;
  color: var(--mt-text, #222);
}
.info-label {
  color: var(--mt-text-3, #999);
}
.info-row .urge {
  color: #ee0a24;
  font-weight: 600;
}
.log-row {
  display: flex;
  font-size: 12px;
  padding: 4px 0;
  color: var(--mt-text-3, #999);
}
.log-time {
  width: 130px;
  flex-shrink: 0;
}
.log-text {
  color: var(--mt-text-2, #666);
}
.bottom-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 50px;
  width: 100%;
  max-width: 750px;
  padding: 10px 16px;
  background: #fff;
  box-sizing: border-box;
  display: flex;
  gap: 10px;
}
.bottom-placeholder {
  height: 70px;
}
.btn-yellow {
  --van-button-primary-background: var(--mt-yellow, #ffc300);
  --van-button-primary-border-color: var(--mt-yellow, #ffc300);
  --van-button-primary-color: #333;
  --van-button-radius: var(--mt-radius-btn, 24px);
}
.btn-gray {
  --van-button-default-background: #f2f3f5;
  --van-button-default-border-color: #f2f3f5;
  --van-button-default-color: #646566;
  --van-button-radius: var(--mt-radius-btn, 24px);
}
</style>
