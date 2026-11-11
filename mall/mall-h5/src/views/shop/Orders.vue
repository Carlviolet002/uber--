<template>
  <!-- 订单管理：状态胶囊（含售后）+ 订单卡（状态色条/明细/催单标记）+ 接单/拒单/出餐操作，5s 轮询 -->
  <div class="page">
    <!-- 状态胶囊（横向滚动） -->
    <div class="capsule-bar">
      <div
        v-for="c in capsules"
        :key="c.key"
        class="capsule"
        :class="{ active: activeKey === c.key }"
        @click="activeKey = c.key"
      >
        {{ c.name }}
        <span v-if="c.count > 0" class="capsule-count">{{ c.count }}</span>
      </div>
    </div>

    <!-- 订单列表 -->
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-skeleton v-if="firstLoading" title :row="4" style="margin: 12px" />
      <van-empty
        v-else-if="filteredList.length === 0"
        :description="emptyText(activeKey)"
      />
      <div
        v-for="o in filteredList"
        :key="o.orderNo"
        class="order-card"
        :style="{ borderLeftColor: statusColor(o.status) }"
        @click="router.push('/shop/order/' + o.orderNo)"
      >
        <div class="card-head">
          <span class="order-no">{{ o.orderNo }}</span>
          <van-tag plain :color="statusColor(o.status)">{{ o.statusName || '未知' }}</van-tag>
        </div>
        <div class="dish-lines">
          <div v-for="(it, i) in o.items || []" :key="i" class="dish-line">
            {{ it.dishName || '菜品' }} x{{ it.num }}
          </div>
          <div v-if="!o.items || o.items.length === 0" class="dish-line">订单商品（详情查看）</div>
        </div>
        <!-- 备注 / 餐具 / 催单标记 -->
        <div class="flags">
          <span v-if="o.remark" class="flag flag-remark">备注：{{ o.remark }}</span>
          <span v-if="Number(o.tableware) > 0" class="flag">餐具 x{{ o.tableware }}</span>
          <span v-if="Number(o.urgeCount) > 0" class="flag flag-urge">用户催单 x{{ o.urgeCount }}</span>
        </div>
        <div class="card-foot">
          <span class="order-time">{{ fmtTime(o.createdAt) }}<template v-if="o.expectTime"> · {{ o.deliveryType === 'PICKUP' ? '期望自提' : '期望送达' }} {{ o.expectTime }}</template></span>
          <span class="order-price">¥{{ Number(o.total || 0).toFixed(2) }}</span>
        </div>
        <div v-if="actionBtns(o).length" class="card-ops" @click.stop>
          <van-button
            v-for="b in actionBtns(o)"
            :key="b.text"
            size="small"
            :type="b.type"
            :class="b.type === 'primary' ? 'btn-yellow' : 'btn-gray'"
            :loading="actingNo === o.orderNo"
            @click="b.handler(o)"
          >
            {{ b.text }}
          </van-button>
        </div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup>
// 订单管理：一次拉取全量订单（pageSize=200）后本地按状态胶囊过滤；5s 轮询 + 下拉刷新
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { useShopStatsStore } from '../../stores/shopStats'
import { fetchAllOrders, statusCounts } from './composables/useShopStats'
import { shopAccept, shopReject, shopFinishCooking } from '@/api'

const router = useRouter()
const stats = useShopStatsStore()

const orders = ref([])
const activeKey = ref('pending') // 默认落在「待接单」
const refreshing = ref(false)
const firstLoading = ref(true)
const actingNo = ref('')

// 胶囊配置：key → 名称 → 状态集合（null 表示全部）
const capsules = [
  { key: 'all', name: '全部', statuses: null },
  { key: 'pending', name: '待接单', statuses: [2] },
  { key: 'cooking', name: '备餐中', statuses: [9] },
  { key: 'pickup', name: '待取餐', statuses: [4] },
  { key: 'delivering', name: '配送中', statuses: [5] },
  { key: 'finished', name: '已完成', statuses: [6, 11] },
  { key: 'canceled', name: '已取消', statuses: [7] },
  { key: 'aftersale', name: '售后', statuses: [8] }
]

// 各胶囊计数（角标）
const counts = computed(() => statusCounts(orders.value))
const withCount = computed(() =>
  capsules.map((c) => {
    if (!c.statuses) return { ...c, count: orders.value.length }
    return { ...c, count: c.statuses.reduce((s, st) => s + (counts.value[st] || 0), 0) }
  })
)

// 当前胶囊过滤后的订单
const filteredList = computed(() => {
  const c = capsules.find((x) => x.key === activeKey.value)
  if (!c || !c.statuses) return orders.value
  return orders.value.filter((o) => c.statuses.includes(Number(o.status)))
})

// 状态色条颜色（与方案 2.4 一致）
const statusColor = (s) => {
  const map = { 2: '#FF9A14', 9: '#FFC300', 4: '#FF9A14', 5: '#1989FA', 6: '#07C160', 11: '#07C160', 7: '#C8C9CC', 8: '#C8C9CC' }
  return map[Number(s)] || '#C8C9CC'
}

// 空状态文案（拟人化）
const emptyText = (key) => {
  const map = {
    pending: '还没有新订单，休息一下～',
    cooking: '没有备餐中的订单',
    pickup: '没有待取餐的订单',
    delivering: '没有配送中的订单',
    finished: '还没有完成的订单',
    canceled: '没有已取消的订单',
    aftersale: '没有售后订单，太棒了',
    all: '暂无订单'
  }
  return map[key] || '暂无订单'
}

const fmtTime = (t) => (t ? String(t).slice(0, 19).replace('T', ' ') : '')

// 各状态操作按钮
const actionBtns = (o) => {
  const s = Number(o.status)
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

// 加载全量订单
const load = async () => {
  try {
    orders.value = await fetchAllOrders(await stats.ensureShopId())
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    firstLoading.value = false
    refreshing.value = false
  }
}

const onRefresh = () => {
  refreshing.value = true
  load()
}

// 接单
const onAccept = async (o) => {
  actingNo.value = o.orderNo
  try {
    await shopAccept(o.orderNo)
    showToast('接单成功')
    await load()
    stats.refreshPending()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    actingNo.value = ''
  }
}

// 拒单
const onReject = async (o) => {
  try {
    await showConfirmDialog({ title: '拒单确认', message: '确认拒绝该订单吗？' })
  } catch (e) {
    return // 用户取消
  }
  actingNo.value = o.orderNo
  try {
    await shopReject(o.orderNo, '商家拒单')
    showToast('已拒单')
    await load()
    stats.refreshPending()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    actingNo.value = ''
  }
}

// 出餐完成
const onFinish = async (o) => {
  actingNo.value = o.orderNo
  try {
    await shopFinishCooking(o.orderNo)
    showToast('出餐完成，等待骑手取餐')
    await load()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    actingNo.value = ''
  }
}

let pollTimer = null

onMounted(async () => {
  await load()
  pollTimer = setInterval(load, 5000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: 70px;
}
.capsule-bar {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 10px 12px;
  background: #fff;
  position: sticky;
  top: 48px;
  z-index: 99;
  white-space: nowrap;
  scrollbar-width: none;
}
.capsule-bar::-webkit-scrollbar {
  display: none;
}
.capsule {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: 14px;
  background: #f2f3f5;
  color: var(--mt-text-2, #666);
  font-size: 13px;
  cursor: pointer;
}
.capsule.active {
  background: var(--mt-yellow, #ffc300);
  color: #333;
  font-weight: 600;
}
.capsule-count {
  margin-left: 3px;
  font-size: 11px;
}
.order-card {
  margin: 8px 12px;
  padding: 12px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  border-left: 4px solid transparent;
  cursor: pointer;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-no {
  font-size: 13px;
  font-weight: 600;
  color: var(--mt-text-2, #666);
}
.dish-lines {
  margin-top: 8px;
}
.dish-line {
  font-size: 14px;
  line-height: 21px;
  color: var(--mt-text, #222);
}
.flags {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.flag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  background: #f7f8fa;
  color: var(--mt-text-3, #999);
}
.flag-remark {
  background: #fff9e6;
  color: #b8860b;
}
.flag-urge {
  background: #ffece8;
  color: #ee0a24;
  font-weight: 600;
}
.card-foot {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-time {
  font-size: 12px;
  color: var(--mt-text-3, #999);
}
.order-price {
  font-size: 15px;
  font-weight: 700;
  color: var(--mt-price, #ff4d26);
}
.card-ops {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
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
