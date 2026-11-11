<template>
  <!-- 我的订单页：6 状态 Tab（全部/待支付/进行中/待确认/已完成/售后）+ 卡片 + 按状态按钮组 -->
  <div class="orders">
    <div class="head">我的订单</div>
    <van-tabs v-model:active="activeTab" sticky class="tabs">
      <van-tab v-for="t in tabs" :key="t.title" :title="t.title" :name="t.title">
        <van-pull-refresh v-model="refreshing" @refresh="loadOrders">
          <van-skeleton v-if="loading && orders.length === 0" title :row="3" class="card" />
          <BaseEmpty
            v-else-if="filtered.length === 0"
            :description="activeTab === '全部' ? '还没有订单，去点一份外卖吧' : '该状态下暂无订单'"
            action-text="去点外卖"
            image="orders-o"
            @action="router.push('/user/home')"
          />
          <template v-else>
            <div v-for="o in filtered" :key="o.orderNo" class="order-card" @click="goDetail(o)">
              <div class="order-top">
                <div class="order-left">
                  <span class="order-shop">{{ shopName(o.shopId) }}</span>
                  <span class="order-status" :style="{ color: statusColor(o.status) }">{{ o.statusName }}</span>
                </div>
                <van-icon name="arrow" class="order-arrow" />
              </div>
              <div class="order-body">
                <div class="order-stripe" :style="{ background: statusColor(o.status) }"></div>
                <div class="order-content">
                  <div class="order-desc">
                    <span v-if="o.items && o.items.length">
                      {{ o.items[0].dishName }}<template v-if="o.items.length > 1"> 等 {{ totalNum(o) }} 件</template>
                      <template v-else> x{{ o.items[0].num }}</template>
                    </span>
                    <span v-else>外卖订单</span>
                  </div>
                  <div class="order-flags">
                    <span v-if="o.remark" class="flag">备注</span>
                    <span v-if="Number(o.tableware) > 0" class="flag">餐具{{ o.tableware }}份</span>
                    <span v-if="Number(o.urgeCount) > 0" class="flag warn">已催{{ o.urgeCount }}次</span>
                  </div>
                  <div class="order-time">{{ (o.createdAt || '').toString().slice(0, 19) }}</div>
                </div>
                <div class="order-amount">
                  <span v-if="couponMap[o.orderNo]" class="coupon-badge">券 ¥{{ Number(couponMap[o.orderNo].discount || 0).toFixed(2) }}</span>
                  <div class="order-total">¥{{ Number(o.total || 0).toFixed(2) }}</div>
                </div>
              </div>
              <div v-if="btns(o).length" class="order-ops" @click.stop>
                <div
                  v-for="b in btns(o)"
                  :key="b.text"
                  class="op-btn"
                  :class="[b.primary ? 'primary' : 'secondary', { disabled: b.disabled }]"
                  @click="!b.disabled && b.onClick()"
                >
                  {{ b.text }}
                </div>
              </div>
            </div>
          </template>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup>
// 订单列表：一次拉全量（pageSize 200）前端按 Tab 过滤；操作按钮按状态组合，动作为真实接口
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { orderList, pay, cancelOrder, urgeOrder, confirmOrder } from '@/api'
import api from '../../api'
import BaseEmpty from '../../components/user/BaseEmpty.vue'
import { getOrderCoupon, releaseCoupon } from '../../mock/userMock'

defineOptions({ name: 'UserOrders' })

const router = useRouter()

// Tab 配置：状态数组为空表示全部
const tabs = [
  { title: '全部', statuses: [] },
  { title: '待支付', statuses: [1] },
  { title: '进行中', statuses: [2, 3, 9, 4, 5] },
  { title: '待确认', statuses: [11] },
  { title: '已完成', statuses: [6] },
  { title: '售后', statuses: [7, 8] }
]

const activeTab = ref('全部')
const orders = ref([])
const loading = ref(true)
const refreshing = ref(false)
const shopMap = ref({})
// B 批：{ [orderNo]: { couponId, discount } } 券抵扣记录映射（总价旁「券 ¥X」标）
const couponMap = ref({})

// 状态色：待支付橙 / 进行中蓝 / 待确认黄 / 已完成绿 / 取消售后灰
const statusColor = (s) => {
  const map = { 1: '#FF9A14', 2: '#1989FA', 3: '#1989FA', 9: '#FFC300', 4: '#FFC300', 5: '#1989FA', 11: '#FFC300', 6: '#07C160', 7: '#C8C9CC', 8: '#C8C9CC' }
  return map[s] || '#C8C9CC'
}

const shopName = (id) => shopMap.value[id] || '商家 #' + id

const totalNum = (o) => (o.items || []).reduce((sum, i) => sum + Number(i.num || 0), 0)

const filtered = computed(() => {
  const tab = tabs.find((t) => t.title === activeTab.value) || tabs[0]
  if (!tab.statuses.length) return orders.value
  return orders.value.filter((o) => tab.statuses.includes(Number(o.status)))
})

// 订单操作按钮组
const btns = (o) => {
  const s = Number(o.status)
  const list = []
  if (s === 1) {
    list.push({ text: '去支付', primary: true, onClick: () => onPay(o) })
    list.push({ text: '取消订单', primary: false, onClick: () => onCancel(o) })
  } else if (s === 2) {
    // 「随时确认收货」：已支付后任意非终态均可确认完成
    list.push({ text: '确认收货', primary: true, onClick: () => onConfirm(o) })
  } else if ([3, 9, 4, 5].includes(s)) {
    list.push({ text: '确认收货', primary: true, onClick: () => onConfirm(o) })
    list.push({ text: '催单', primary: false, onClick: () => onUrge(o) })
  } else if (s === 11) {
    list.push({ text: '确认收货', primary: true, onClick: () => onConfirm(o) })
  } else if (s === 6) {
    const done = Number(o.reviewed || 0) === 1
    // 评价后按钮保留但置灰禁用，文字改「已评价」（不再直接消失）
    list.push({
      text: done ? '已评价' : '去评价',
      primary: !done,
      disabled: done,
      onClick: () => goDetail(o)
    })
    list.push({ text: '再来一单', primary: done, onClick: () => onAgain(o) })
  } else if ([7, 8].includes(s)) {
    list.push({ text: '再来一单', primary: false, onClick: () => onAgain(o) })
  }
  return list
}

const goDetail = (o) => router.push('/user/order/' + o.orderNo)

const onPay = async (o) => {
  try {
    await pay(o.orderNo)
    showToast('支付成功')
    await loadOrders()
  } catch (e) {
    // 拦截器已提示
  }
}

const onCancel = async (o) => {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确认取消该订单吗？' })
  } catch (e) {
    return
  }
  try {
    await cancelOrder(o.orderNo, '用户主动取消')
    // B 批：取消订单退券（有抵扣记录才退）
    if (releaseCoupon(o.orderNo)) {
      showToast('订单已取消，优惠券已退回')
    } else {
      showToast('订单已取消')
    }
    await loadOrders()
  } catch (e) {
    // 拦截器已提示
  }
}

const onUrge = async (o) => {
  try {
    await urgeOrder(o.orderNo)
    showToast('已催单，商家与骑手将尽快处理')
    await loadOrders()
  } catch (e) {
    // 限频等失败提示已由拦截器处理
  }
}

const onConfirm = async (o) => {
  try {
    await showConfirmDialog({
      title: '确认收货',
      message: '确认已收到商品吗？提前确认将直接完结订单'
    })
  } catch (e) {
    return
  }
  try {
    await confirmOrder(o.orderNo)
    showToast('已确认收货')
    await loadOrders()
  } catch (e) {
    // 拦截器已提示
  }
}

const onAgain = (o) => {
  router.push('/user/shop/' + o.shopId)
}

const loadOrders = async () => {
  loading.value = true
  try {
    const data = await orderList({ page: 1, pageSize: 200 })
    orders.value = (data && data.records) || []
    // B 批：批量读取券抵扣记录（有记录的订单总价旁显示「券 ¥X」）
    const map = {}
    orders.value.forEach((o) => {
      const rec = getOrderCoupon(o.orderNo)
      if (rec) map[o.orderNo] = rec
    })
    couponMap.value = map
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 店铺名映射（订单只有 shopId，前端拉店铺列表建映射）
const loadShopMap = async () => {
  try {
    const list = (await api.shopList()) || []
    const map = {}
    list.forEach((s) => {
      map[s.id] = s.name
    })
    shopMap.value = map
  } catch (e) {
    // 静默处理
  }
}

onMounted(async () => {
  await loadShopMap()
})

// keep-alive：每次激活刷新订单列表（支付/取消等操作后回来是最新数据）
onActivated(loadOrders)
</script>

<style scoped>
.orders {
  min-height: 100vh;
  background: var(--mt-bg);
}
.head {
  background: #fff;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: #333;
  padding: 14px 0;
}
.tabs :deep(.van-tabs__nav) {
  background: #fff;
}
.order-card {
  background: #fff;
  border-radius: var(--mt-radius);
  margin: 12px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  cursor: pointer;
}
.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.order-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.order-shop {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.order-status {
  font-size: 12px;
  font-weight: 600;
}
.order-arrow {
  font-size: 12px;
  color: var(--mt-text-3);
}
.order-body {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.order-stripe {
  width: 4px;
  height: 44px;
  border-radius: 2px;
  flex-shrink: 0;
}
.order-content {
  flex: 1;
  min-width: 0;
}
.order-desc {
  font-size: 13px;
  color: var(--mt-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-flags {
  margin-top: 4px;
  display: flex;
  gap: 6px;
}
.flag {
  font-size: 10px;
  color: var(--mt-text-3);
  background: var(--mt-bg);
  border-radius: 4px;
  padding: 1px 6px;
}
.flag.warn {
  color: #ff5000;
  background: rgba(255, 80, 0, 0.08);
}
.order-time {
  margin-top: 4px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.order-total {
  font-size: 15px;
  font-weight: 700;
  color: #ff5000;
  flex-shrink: 0;
}
/* B 批：券标 + 金额区 */
.order-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}
.coupon-badge {
  font-size: 10px;
  color: #b8860b;
  background: rgba(255, 195, 0, 0.15);
  border-radius: 4px;
  padding: 1px 6px;
  margin-bottom: 3px;
}
.order-ops {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.op-btn {
  min-width: 88px;
  height: 38px;
  line-height: 38px;
  text-align: center;
  border-radius: 19px;
  font-size: 13px;
  font-weight: 600;
  padding: 0 16px;
  cursor: pointer;
}
.op-btn.primary {
  background: #ff5000;
  color: #fff;
}
.op-btn.secondary {
  background: #f5f5f5;
  color: #333;
}
/* 已评价：置灰 + 禁止点击（覆盖 primary/secondary 配色） */
.op-btn.disabled {
  background: #f5f5f5;
  color: #c8c9cc;
  cursor: not-allowed;
}
</style>
