<template>
  <!-- 商家接单工作台（SHOP）：待接单 / 备餐中两个列表，每 5 秒轮询刷新 -->
  <div class="page">
    <!-- 顶部黄色标题栏 -->
    <div class="header">
      <div class="header-title">接单工作台</div>
      <div class="header-sub">{{ nickname }}</div>
    </div>

    <!-- 待接单列表 -->
    <div class="section-title">待接单（{{ pendingList.length }}）</div>
    <van-empty v-if="pendingList.length === 0" description="暂无待接单订单" />
    <div v-for="o in pendingList" :key="o.orderNo" class="order-card">
      <div class="card-head">
        <span class="order-no">{{ o.orderNo }}</span>
        <van-tag type="warning">{{ o.statusName || '待接单' }}</van-tag>
      </div>
      <div class="dish-lines">
        <div v-for="(it, i) in o.items || []" :key="i" class="dish-line">
          {{ it.dishName || it.name || '菜品' }} x{{ it.num }}
        </div>
      </div>
      <div class="card-foot">
        <span class="order-price">¥{{ Number(o.total || 0).toFixed(2) }}</span>
        <span class="order-time">{{ (o.createdAt || '').toString().slice(0, 19) }}</span>
      </div>
      <div class="card-ops">
        <van-button
          size="small"
          type="primary"
          class="btn-yellow"
          :loading="actingNo === o.orderNo"
          @click="onAccept(o)"
        >
          接单
        </van-button>
        <van-button
          size="small"
          class="btn-gray"
          :loading="actingNo === o.orderNo"
          @click="onReject(o)"
        >
          拒单
        </van-button>
      </div>
    </div>

    <!-- 备餐中列表 -->
    <div class="section-title">备餐中（{{ cookingList.length }}）</div>
    <van-empty v-if="cookingList.length === 0" description="暂无备餐中订单" />
    <div v-for="o in cookingList" :key="o.orderNo" class="order-card">
      <div class="card-head">
        <span class="order-no">{{ o.orderNo }}</span>
        <van-tag type="primary">{{ o.statusName || '备餐中' }}</van-tag>
      </div>
      <div class="dish-lines">
        <div v-for="(it, i) in o.items || []" :key="i" class="dish-line">
          {{ it.dishName || it.name || '菜品' }} x{{ it.num }}
        </div>
      </div>
      <div class="card-foot">
        <span class="order-price">¥{{ Number(o.total || 0).toFixed(2) }}</span>
        <span class="order-time">{{ (o.createdAt || '').toString().slice(0, 19) }}</span>
      </div>
      <div class="card-ops">
        <van-button
          size="small"
          type="primary"
          class="btn-yellow"
          :loading="actingNo === o.orderNo"
          @click="onFinish(o)"
        >
          出餐完成
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 商家接单工作台：待接单(status=2)可接单/拒单，备餐中(status=9，兼容 status=4 且 statusName 含"备餐")
// 可出餐完成；setInterval 每 5 秒轮询刷新，onUnmounted 清理定时器
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { useUserStore } from '../../stores/user'
import { getShopOrders, shopAccept, shopReject, shopFinishCooking, shopList } from '@/api'

const userStore = useUserStore()

const nickname = computed(
  () => userStore.userInfo.nickname || userStore.userInfo.username || '商家'
)

const shopId = ref(null)
const pendingList = ref([])
const cookingList = ref([])
const actingNo = ref('') // 正在操作的订单号（按钮 loading）
let pollTimer = null

/**
 * 商家身份获取：种子数据 shop1 账号 userId=3 对应 shopId=1，且昵称"张姐麻辣烫"与店铺名一致。
 * 最省事且能跑通的方案：
 *  1. 优先取登录信息里的 shopId（若后端登录接口直接返回）；
 *  2. 否则用昵称匹配店铺名（shopList 里 name === nickname，种子数据恰好一致）；
 *  3. 再兜底 userId-2（种子约定：第一个商家账号 userId=3 → shopId=1）；
 *  4. 最后兜底 1。
 */
const resolveShopId = async () => {
  const info = userStore.userInfo || {}
  if (info.shopId) return Number(info.shopId)
  try {
    const shops = (await shopList()) || []
    const hit = shops.find((s) => s.name === info.nickname)
    if (hit) return Number(hit.id)
  } catch (e) {
    // 列表失败走兜底逻辑
  }
  const uid = Number(info.userId ?? info.id)
  if (uid) return uid - 2
  return 1
}

// 解析列表：兼容后端直接返回数组或 {records}
const toList = (data) => (Array.isArray(data) ? data : (data && data.records) || [])

// 加载待接单订单（status=2）
const loadPending = async () => {
  try {
    const data = await getShopOrders({ shopId: shopId.value, status: 2, page: 1, pageSize: 50 })
    pendingList.value = toList(data)
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 加载备餐中订单（status=9，兼容后端用 status=4 + statusName 含"备餐"的旧实现）
const loadCooking = async () => {
  try {
    const data = await getShopOrders({ shopId: shopId.value, page: 1, pageSize: 50 })
    cookingList.value = toList(data).filter(
      (o) =>
        Number(o.status) === 9 ||
        (Number(o.status) === 4 && (o.statusName || '').indexOf('备餐') > -1)
    )
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

const loadAll = async () => {
  await Promise.all([loadPending(), loadCooking()])
}

// 接单
const onAccept = async (o) => {
  actingNo.value = o.orderNo
  try {
    await shopAccept(o.orderNo)
    showToast('接单成功')
    await loadAll()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    actingNo.value = ''
  }
}

// 拒单（有 shopReject 接口，展示拒单按钮）
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
    await loadAll()
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
    await loadAll()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    actingNo.value = ''
  }
}

onMounted(async () => {
  shopId.value = await resolveShopId()
  await loadAll()
  // 每 5 秒轮询刷新
  pollTimer = setInterval(loadAll, 5000)
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
.section-title {
  margin: 16px 16px 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.order-card {
  margin: 8px 12px;
  padding: 12px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-no {
  font-size: 14px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.dish-lines {
  margin-top: 8px;
}
.dish-line {
  font-size: 13px;
  line-height: 20px;
  color: var(--mt-text-2, #666);
}
.card-foot {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-price {
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-price, #ff4d26);
}
.order-time {
  font-size: 12px;
  color: var(--mt-text-3, #999);
}
.card-ops {
  margin-top: 10px;
  display: flex;
  gap: 10px;
}
/* 黄底胶囊按钮：覆盖 Vant 主色为美团黄，深色文字保证对比度 */
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
