<template>
  <!-- 骑手工作台：黄色标题栏 + 上线开关 + 我的任务 / 抢单大厅 / 推荐单 三个 Tab -->
  <div class="page">
    <!-- 顶部黄色标题栏 -->
    <div class="header">
      <div class="header-title">骑手工作台</div>
      <div class="header-sub">{{ nickname }}</div>
    </div>
    <!-- 上线开关（保留原有逻辑：上线后每 5 秒上报一次位置并刷新任务） -->
    <van-cell-group inset class="online-cell">
      <van-cell title="上线接单" label="上线后每 5 秒上报一次位置">
        <template #right-icon>
          <van-switch v-model="online" size="24" @change="onSwitch" />
        </template>
      </van-cell>
    </van-cell-group>
    <!-- 离线提示：未上线时不轮询抢单大厅/推荐单，给出明确说明 -->
    <van-notice-bar
      v-if="!online"
      left-icon="info-o"
      text="当前离线，请先上线后再查看抢单大厅与推荐单"
    />
    <!-- 三个 Tab -->
    <van-tabs
      v-model:active="activeTab"
      sticky
      color="var(--mt-yellow, #FFC300)"
      title-active-color="#222"
      class="tabs"
    >
      <!-- Tab1 我的任务 -->
      <van-tab title="我的任务" name="mine">
        <van-empty v-if="tasks.length === 0" description="暂无任务" />
        <div
          v-for="t in tasks"
          :key="t.orderNo"
          class="task-card"
          @click="router.push('/rider/task/' + t.orderNo)"
        >
          <div class="task-head">
            <span class="task-no">订单 {{ t.orderNo }}</span>
            <van-tag plain type="primary">{{ statusNameOf(t) }}</van-tag>
          </div>
          <div class="task-info">
            商家ID: {{ t.shopId }}　下单: {{ (t.createdAt || '').toString().slice(0, 19) }}
          </div>
          <!-- 取餐按钮仅在状态 4 可用，送达按钮状态 5 可用 -->
          <div v-if="Number(t.status) === 4 || Number(t.status) === 5" class="task-ops">
            <van-button
              v-if="Number(t.status) === 4"
              size="small"
              type="primary"
              class="btn-yellow"
              :loading="actingNo === t.orderNo"
              @click.stop="doTaskAction(t, 'picked')"
            >
              取 餐
            </van-button>
            <van-button
              v-if="Number(t.status) === 5"
              size="small"
              type="success"
              class="btn-green"
              :loading="actingNo === t.orderNo"
              @click.stop="doTaskAction(t, 'delivered')"
            >
              送 达
            </van-button>
          </div>
        </div>
      </van-tab>
      <!-- Tab2 抢单大厅（每 10 秒轮询） -->
      <van-tab title="抢单大厅" name="pool">
        <van-empty v-if="pool.length === 0" description="暂无可抢订单" />
        <div v-for="o in pool" :key="o.orderNo" class="task-card">
          <div class="task-head">
            <span class="task-no">{{ o.orderNo }}</span>
            <van-tag type="warning">可抢</van-tag>
          </div>
          <div class="task-info">
            {{ shopNameOf(o) }}
            <span class="task-gap">距您 {{ formatDistance(o.distanceMeters ?? o.distance) }}</span>
            <span class="task-gap">预计 {{ expectMinutesOf(o) }} 分钟</span>
          </div>
          <div class="task-ops">
            <van-button
              size="small"
              type="primary"
              class="btn-yellow"
              :loading="grabbingNo === o.orderNo"
              @click="onGrab(o)"
            >
              抢 单
            </van-button>
          </div>
        </div>
      </van-tab>
      <!-- Tab3 推荐单：我的任务 + 顺路单候选 -->
      <van-tab title="推荐单" name="recommend">
        <van-empty
          v-if="recMyTasks.length === 0 && recCandidates.length === 0"
          description="暂无推荐"
        />
        <template v-else>
          <div v-if="recMyTasks.length" class="section-title">我的任务</div>
          <div v-for="t in recMyTasks" :key="t.orderNo" class="task-card">
            <div class="task-head">
              <span class="task-no">{{ t.orderNo }}</span>
              <van-tag plain type="primary">{{ statusNameOf(t) }}</van-tag>
            </div>
            <div class="task-info">
              {{ shopNameOf(t) }} · {{ (t.createdAt || '').toString().slice(0, 19) }}
            </div>
          </div>
          <div v-if="recCandidates.length" class="section-title">顺路单推荐</div>
          <div v-for="c in recCandidates" :key="c.orderNo" class="task-card">
            <div class="task-head">
              <span class="task-no">{{ c.orderNo }}</span>
              <van-tag type="warning">顺路单</van-tag>
            </div>
            <div class="task-info">
              {{ shopNameOf(c) }}
              <span class="task-gap">距您 {{ formatDistance(c.distanceMeters ?? c.distance) }}</span>
              <span class="task-gap">推荐指数 {{ recommendScoreOf(c) }}</span>
            </div>
            <div class="task-ops">
              <van-button
                size="small"
                type="primary"
                class="btn-yellow"
                :loading="grabbingNo === c.orderNo"
                @click="onGrab(c)"
              >
                抢 单
              </van-button>
            </div>
          </div>
        </template>
      </van-tab>
    </van-tabs>

    <!-- P1 骑手服务入口 -->
    <van-cell-group inset class="rider-tools">
      <van-cell title="收入统计" is-link @click="router.push('/rider/income')" />
      <van-cell title="异常上报" is-link @click="router.push('/rider/report')" />
    </van-cell-group>
  </div>
</template>

<script setup>
// 骑手工作台：保留原有 5 秒位置上报 + 任务刷新逻辑；新增抢单大厅（10 秒轮询）与推荐单 Tab。
// 状态数字对照：1 待支付 2 待接单 3 派单中 9 备餐中 4 待取餐 5 配送中 11 已送达待确认 6 已完成 7 已取消 8 已退款
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '../../stores/user'
import {
  riderOrders,
  riderOnline,
  riderOffline,
  riderLocation,
  riderPicked,
  riderDelivered,
  riderPool,
  grabOrder,
  riderRecommend,
  riderStatus
} from '@/api'

const router = useRouter()
const userStore = useUserStore()

const nickname = computed(
  () => userStore.userInfo.nickname || userStore.userInfo.username || '骑手'
)

const activeTab = ref('mine')
const online = ref(false)
const tasks = ref([])
const pool = ref([])
const recMyTasks = ref([])
const recCandidates = ref([])
const actingNo = ref('') // 正在取餐/送达的订单号
const grabbingNo = ref('') // 正在抢单的订单号
let offlineHintShown = false // 离线提示防抖：10 秒轮询只提示一次
let timer5 = null // 5 秒：位置上报 + 任务刷新（保留原有逻辑）
let timer10 = null // 10 秒：抢单大厅 + 推荐单轮询

// 状态名映射：9 备餐中(待取餐) / 4 待取餐 / 11 已送达待确认，其余沿用后端 statusName
const statusNameOf = (o) => {
  const s = Number(o.status)
  const map = { 9: '备餐中(待取餐)', 4: '待取餐', 11: '已送达待确认' }
  return map[s] || o.statusName || '状态 ' + s
}

// 距离格式化：米 → 千米（保留 1 位小数）
const formatDistance = (m) => {
  if (m == null || m === '') return '--'
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : Math.round(m) + 'm'
}

// 店铺名（兼容 shopName 字段或嵌套 shop 对象）
const shopNameOf = (o) => o.shopName || (o.shop && o.shop.name) || '商家' + (o.shopId ?? '')

// 期望分钟（兼容多种字段名）
const expectMinutesOf = (o) => o.expectMinutes ?? o.expectedMinutes ?? o.etaMinutes ?? '--'

// 推荐指数（兼容多种字段名）
const recommendScoreOf = (c) =>
  c.score ?? c.recommendScore ?? c.recommendIndex ?? c.scoreValue ?? '--'

// 加载我的任务（保留原有逻辑）
const loadTasks = async () => {
  try {
    tasks.value = (await riderOrders()) || []
  } catch (e) {
    // 轮询失败静默处理
  }
}

// 离线提示防抖：避免 10 秒轮询重复弹窗
const hintOffline = () => {
  if (offlineHintShown) return
  offlineHintShown = true
  showToast('骑手不在线，请先上线后再查看抢单大厅与推荐单')
}

// 加载抢单大厅（兼容返回数组或 {records}）
const loadPool = async () => {
  try {
    const data = await riderPool()
    pool.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 离线时给出一次明确提示，其余轮询失败静默
    const msg = (e && e.message) || ''
    if (msg.includes('不在线') || msg.includes('无权限')) hintOffline()
  }
}

// 加载推荐单：{ myTasks: [], candidates: [] }
const loadRecommend = async () => {
  try {
    const data = (await riderRecommend()) || {}
    recMyTasks.value = data.myTasks || []
    recCandidates.value = data.candidates || []
  } catch (e) {
    // 离线时给出一次明确提示，其余轮询失败静默
    const msg = (e && e.message) || ''
    if (msg.includes('不在线') || msg.includes('无权限')) hintOffline()
  }
}

// 模拟位置上报：真实环境应接入浏览器 Geolocation / 手机 GPS（保留原有逻辑）
const reportLocation = () => {
  riderLocation(
    113.66 + (Math.random() - 0.5) * 0.01,
    34.76 + (Math.random() - 0.5) * 0.01
  ).catch(() => {})
}

// 启动 5 秒定时器：位置上报 + 任务刷新（保留原有逻辑）
const startTimer5 = () => {
  if (timer5) return
  timer5 = setInterval(() => {
    reportLocation()
    loadTasks()
  }, 5000)
}

// 停止 5 秒定时器
const stopTimer5 = () => {
  if (timer5) {
    clearInterval(timer5)
    timer5 = null
  }
}

// 启动 10 秒定时器：抢单大厅 + 推荐单轮询
const startTimer10 = () => {
  if (timer10) return
  timer10 = setInterval(() => {
    loadPool()
    loadRecommend()
  }, 10000)
}

// 停止 10 秒定时器
const stopTimer10 = () => {
  if (timer10) {
    clearInterval(timer10)
    timer10 = null
  }
}

// 上线/下线切换：同步定时器与列表，失败回滚开关状态
const onSwitch = async (val) => {
  try {
    if (val) {
      await riderOnline()
      showToast('已上线')
      offlineHintShown = false
      loadTasks()
      loadPool()
      loadRecommend()
      startTimer5()
      startTimer10()
    } else {
      await riderOffline()
      showToast('已下线')
      stopTimer5()
      stopTimer10()
      pool.value = []
      recMyTasks.value = []
      recCandidates.value = []
    }
  } catch (e) {
    online.value = !val // 失败回滚开关状态
    // 错误提示已由响应拦截器统一处理
  }
}

// 取餐 / 送达
const doTaskAction = async (t, type) => {
  actingNo.value = t.orderNo
  try {
    if (type === 'picked') {
      await riderPicked(t.orderNo)
      showToast('已取餐，开始配送')
    } else {
      await riderDelivered(t.orderNo)
      showToast('订单已送达')
    }
    await loadTasks()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    actingNo.value = ''
  }
}

// 抢单：成功后刷新各列表并切到「我的任务」
const onGrab = async (o) => {
  grabbingNo.value = o.orderNo
  try {
    await grabOrder(o.orderNo)
    showToast('抢单成功')
    await Promise.all([loadTasks(), loadPool(), loadRecommend()])
    activeTab.value = 'mine'
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    grabbingNo.value = ''
  }
}

onMounted(async () => {
  // 先回读后端真实上线状态，避免刷新页面后开关与实际状态相反
  let isOnline = false
  try {
    const s = await riderStatus()
    isOnline = Number(s && s.status) === 1
  } catch (e) {
    // 查询失败时按离线处理，不阻断页面
  }
  online.value = isOnline
  loadTasks()
  if (isOnline) {
    loadPool()
    loadRecommend()
    startTimer5() // 在线才上报位置，避免离线骑手被写回 GEO
    startTimer10()
  }
})

onBeforeUnmount(() => {
  stopTimer5()
  stopTimer10()
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
.online-cell {
  margin-top: 12px;
}
.rider-tools {
  margin-top: 12px;
}
.tabs :deep(.van-tabs__nav) {
  background: #fff;
}
.section-title {
  margin: 16px 16px 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.task-card {
  margin: 8px 12px;
  padding: 12px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  cursor: pointer;
}
.task-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-no {
  font-size: 14px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.task-info {
  margin-top: 6px;
  font-size: 13px;
  color: var(--mt-text-2, #666);
}
.task-gap {
  margin-left: 10px;
}
.task-ops {
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
.btn-green {
  --van-button-radius: var(--mt-radius-btn, 24px);
}
</style>
