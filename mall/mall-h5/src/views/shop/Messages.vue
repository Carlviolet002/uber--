<template>
  <!-- 消息中心：顾客消息（催单/未回复评价聚合）+ 平台消息（shopMock 假数据）双子 Tab -->
  <div class="page">
    <van-tabs v-model:active="active" sticky offset-top="48">
      <!-- 顾客消息 -->
      <van-tab title="顾客消息" :badge="customerMsgs.length || ''">
        <van-empty v-if="customerMsgs.length === 0" description="暂无顾客消息，去接单吧～" />
        <div v-for="m in customerMsgs" :key="m.key" class="msg-card" @click="onCustomerMsg(m)">
          <div class="msg-icon" :style="{ background: m.bg }">{{ m.icon }}</div>
          <div class="msg-body">
            <div class="msg-title">{{ m.title }}</div>
            <div class="msg-summary">{{ m.summary }}</div>
            <div class="msg-time">{{ m.time }}</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </van-tab>
      <!-- 平台消息 -->
      <van-tab title="平台消息" :badge="unreadCount || ''">
        <van-empty v-if="platformMsgs.length === 0" description="暂无平台消息" />
        <div
          v-for="m in platformMsgs"
          :key="m.id"
          class="msg-card"
          @click="onPlatformMsg(m)"
        >
          <div class="msg-icon" style="background: #FFF3CC">📢</div>
          <div class="msg-body">
            <div class="msg-title">
              {{ m.title }}
              <span v-if="!m.read" class="unread-dot"></span>
            </div>
            <div class="msg-summary">{{ m.summary }}</div>
            <div class="msg-time">{{ m.time }}</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup>
// 消息中心：顾客消息由真实接口聚合（催单订单 + 未回复评价）
// 平台消息：real 模式走站内消息接口（D6-C），mock 模式接口 404 静默降级到 shopMock 演示数据
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useShopStatsStore } from '../../stores/shopStats'
import { fetchAllOrders } from './composables/useShopStats'
import { getPlatformMessages, markMessageRead } from '../../mock/shopMock'
import { getShopReviews, messages as apiMessages, messageRead } from '@/api'

const router = useRouter()
const stats = useShopStatsStore()

const active = ref(0)
const platformMsgs = ref([])
/** 平台消息数据源：true=真实接口，false=shopMock 降级 */
const realMode = ref(true)

// 顾客消息：催单订单 + 未回复评价
const customerMsgs = ref([])

const unreadCount = computed(() => platformMsgs.value.filter((m) => !m.read).length)

// 点击顾客消息：跳订单详情或评价管理
const onCustomerMsg = (m) => {
  if (m.type === 'urge' && m.orderNo) {
    router.push('/shop/order/' + m.orderNo)
  } else {
    router.push('/shop/reviews')
  }
}

// 点击平台消息：标记已读 + 轻提示
const onPlatformMsg = async (m) => {
  if (!m.read) {
    m.read = true
    if (realMode.value) {
      try {
        await messageRead(m.id)
      } catch (e) {
        // 真实接口异常时回退本地标记，保证演示不中断
        markMessageRead(m.id)
      }
    } else {
      markMessageRead(m.id)
    }
    stats.refreshUnread()
  }
  showToast(m.title + '：已读')
}

/** 平台消息加载：真实接口优先，失败静默降级 shopMock */
async function loadPlatformMessages() {
  try {
    const data = await apiMessages({ page: 1, pageSize: 50 })
    const list = Array.isArray(data) ? data : (data && data.records) || []
    platformMsgs.value = list.map((m) => ({
      id: m.id,
      type: m.type,
      title: m.title,
      summary: m.content,
      time: m.createTime ? String(m.createTime).slice(0, 16).replace('T', ' ') : '',
      read: m.isRead === 1,
      bizNo: m.bizNo
    }))
    realMode.value = true
  } catch (e) {
    // mock 模式无此接口（404），静默降级到 shopMock 演示数据（D5 降级策略局部试点）
    realMode.value = false
    platformMsgs.value = await getPlatformMessages()
  }
}

onMounted(async () => {
  try {
    const shopId = await stats.ensureShopId()
    // 平台消息（real 优先，shopMock 降级）
    await loadPlatformMessages()
    // 顾客消息聚合
    const [orders, reviews] = await Promise.all([fetchAllOrders(shopId), getShopReviews(shopId)])
    const msgs = []
    ;(orders || []).forEach((o) => {
      if (Number(o.urgeCount) > 0) {
        msgs.push({
          key: 'urge-' + o.orderNo,
          type: 'urge',
          orderNo: o.orderNo,
          icon: '⏰',
          bg: '#FFE8E0',
          title: '顾客催单',
          summary: `订单 ${o.orderNo} 已催单 ${o.urgeCount} 次，请尽快处理。`,
          time: o.urgeLastTime ? String(o.urgeLastTime).slice(0, 19).replace('T', ' ') : ''
        })
      }
    })
    const list = Array.isArray(reviews) ? reviews : (reviews && reviews.records) || []
    ;(list || []).forEach((r) => {
      if (!r.shopReply) {
        msgs.push({
          key: 'review-' + r.id,
          type: 'review',
          icon: '⭐',
          bg: '#FFF3CC',
          title: '收到新评价',
          summary: (r.content || '顾客留下了评价') + '（等待回复）',
          time: r.createTime ? String(r.createTime).slice(0, 19).replace('T', ' ') : ''
        })
      }
    })
    customerMsgs.value = msgs
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
  padding-bottom: 70px;
}
.msg-card {
  display: flex;
  align-items: center;
  margin: 8px 12px;
  padding: 12px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  cursor: pointer;
}
.msg-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.msg-body {
  flex: 1;
  margin: 0 10px;
  min-width: 0;
}
.msg-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.msg-summary {
  margin-top: 3px;
  font-size: 12px;
  color: var(--mt-text-3, #999);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg-time {
  margin-top: 3px;
  font-size: 11px;
  color: #c8c9cc;
}
.unread-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ee0a24;
  margin-left: 6px;
  vertical-align: middle;
}
</style>
