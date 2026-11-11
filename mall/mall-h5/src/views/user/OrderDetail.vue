<template>
  <!-- 订单详情页（PRD 结构）：黄渐变状态横幅+进度条 / 商家商品卡 / 金额明细 / 物流收货 / 交易快照 / 操作按钮组 -->
  <div class="odetail">
    <van-nav-bar class="nav">
      <template #left>
        <van-icon name="arrow-left" size="18" @click="router.back()" />
      </template>
      <template #title><span class="nav-title">订单详情</span></template>
      <template #right>
        <van-icon name="share-o" size="18" @click="onShare" />
      </template>
    </van-nav-bar>

    <template v-if="order.orderNo">
      <!-- 状态横幅 -->
      <div class="banner">
        <div class="banner-main">
          <div class="banner-icon">{{ bannerIcon }}</div>
          <div class="banner-text">
            <div class="banner-status">{{ bannerText }}</div>
            <div class="banner-sub">{{ bannerSub }}</div>
          </div>
        </div>
        <!-- 配送进度条：已下单→已接单→制作中→配送中→已送达 -->
        <div v-if="progressOn" class="progress">
          <div v-for="(p, i) in progressNodes" :key="p" class="node">
            <div class="dot" :class="{ done: i < step, current: i === step }"></div>
            <div v-if="i < progressNodes.length - 1" class="line" :class="{ done: i < step }"></div>
            <span class="node-label">{{ p }}</span>
          </div>
        </div>
        <div v-else class="progress-off">订单已关闭</div>
      </div>

      <!-- 商家 + 商品卡 -->
      <div class="card">
        <div class="merchant">
          <div class="merchant-logo">{{ shopEmoji }}</div>
          <div class="merchant-info">
            <div class="merchant-name">{{ shopNameText }}</div>
            <div class="merchant-cat">{{ isPickup ? '美食 · 用户自提' : '美食 · 商家自送' }}</div>
          </div>
          <span class="merchant-status" :style="{ color: statusColor, background: statusColor + '1A' }">{{ order.statusName }}</span>
        </div>
        <div class="goods">
          <div v-for="(it, i) in order.items || []" :key="i" class="goods-row">
            <div class="goods-thumb">{{ dishEmoji }}</div>
            <div class="goods-info">
              <div class="goods-name">{{ it.dishName || '菜品' }}</div>
              <div class="goods-spec">规格：默认</div>
              <div class="goods-num">x{{ it.num }}</div>
            </div>
            <div class="goods-price">¥{{ Number(it.unitPrice || it.price || 0).toFixed(2) }}</div>
          </div>
        </div>
        <div class="aftersale-tip">* 餐品如有问题，请在收货后 30 分钟内联系商家</div>
      </div>

      <!-- 金额明细卡 -->
      <div class="card amount">
        <div class="amount-row"><span class="amount-label">商品总额</span><span class="amount-value">¥{{ goodsTotal.toFixed(2) }}</span></div>
        <div class="amount-row"><span class="amount-label">配送费</span><span class="amount-value">¥{{ Number(order.deliveryFee || 0).toFixed(2) }}</span></div>
        <!-- B 批：神券抵扣行（mock 记录；取消/退款订单置灰） -->
        <div v-if="couponRec" class="amount-row coupon" :class="{ muted: [7, 8].includes(status) }">
          <span class="amount-label">神券抵扣<span class="coupon-sub">预计优惠（mock 记录）</span></span>
          <span class="amount-value">-¥{{ Number(couponRec.discount || 0).toFixed(2) }}</span>
        </div>
        <div v-if="discount > 0" class="amount-row discount">
          <span class="amount-label">配送费减免</span><span class="amount-value">-¥{{ discount.toFixed(2) }}</span>
        </div>
        <div v-if="discount > 0" class="amount-row save">
          <span class="amount-label">共减</span><span class="amount-value">¥{{ discount.toFixed(2) }}</span>
        </div>
        <div class="amount-row total">
          <span class="amount-label">实付款</span>
          <span class="amount-total">¥{{ Number(order.payAmount || order.total || 0).toFixed(2) }}</span>
        </div>
        <div class="order-no-row">
          <span class="order-no">{{ order.orderNo }}</span>
          <span class="copy-btn" @click="onCopy">复制</span>
        </div>
      </div>

      <!-- 物流与收货信息卡（自提单不显示地址与骑手） -->
      <div class="card logistic">
        <div class="logi-row">
          <span class="logi-icon">{{ isPickup ? '🏪' : '🚚' }}</span>
          <span class="logi-text">{{ isPickup ? '用户自提' : '商家自送' }}</span>
          <span class="logi-time">{{ deliverText }}</span>
        </div>
        <div class="logi-row" v-if="riderText && !isPickup">
          <span class="logi-icon">🛵</span>
          <span class="logi-text">{{ riderText }}</span>
        </div>
        <template v-if="!isPickup">
          <div class="divider"></div>
          <div class="addr-row">
            <span class="logi-icon">📍</span>
            <div class="addr-info">
              <div class="addr-name-row">
                <span class="addr-name">{{ receiverText }}</span>
                <span class="addr-protect">号码保护中</span>
              </div>
              <div class="addr-detail">{{ addressDetail }}</div>
              <div class="addr-tip">如遇缺货：缺货时与我电话沟通</div>
            </div>
          </div>
          <div class="fold" @click="folded = !folded">
            {{ folded ? '展开' : '收起' }}
            <van-icon :name="folded ? 'arrow-down' : 'arrow-up'" />
          </div>
        </template>
      </div>

      <!-- 交易快照卡 -->
      <div class="card snapshot" @click="showSnapshot">
        <span class="snap-icon">🔍</span>
        <div class="snap-info">
          <div class="snap-title">交易快照</div>
          <div class="snap-sub">发生交易争议时，可作为判断依据</div>
        </div>
        <van-icon name="arrow" class="snap-arrow" />
      </div>

      <!-- 我的评价：已评价时展示评分/内容 + 商家回复 + 平台回复 -->
      <div v-if="myReview" class="card my-review">
        <div class="my-review-title">我的评价</div>
        <div class="my-review-scores">
          综合 {{ myReview.shopScoreOverall }} 分 · 口味 {{ myReview.shopScoreTaste }} · 包装 {{ myReview.shopScorePack }}
        </div>
        <div v-if="myReview.content" class="my-review-content">{{ myReview.content }}</div>
        <div v-if="myReview.shopReply" class="my-review-reply"><b>商家回复：</b>{{ myReview.shopReply }}</div>
        <div v-if="myReview.platformReply" class="my-review-reply platform"><b>平台回复：</b>{{ myReview.platformReply }}</div>
      </div>

      <!-- 操作按钮组 -->
      <div v-if="ops.length" class="ops">
        <div
          v-for="b in ops"
          :key="b.text"
          class="op-btn"
          :class="[b.primary ? 'primary' : 'secondary', { disabled: b.disabled }]"
          @click="!b.disabled && b.onClick()"
        >
          {{ b.text }}
        </div>
      </div>
    </template>

    <!-- 评价弹窗（沿用：商家 3 项 + 骑手 2 项星级） -->
    <van-popup v-model:show="showReview" position="bottom" round class="review-pop" :style="{ padding: '16px 16px 24px' }">
      <div class="review-title">订单评价</div>
      <div class="rate-group">
        <div class="rate-group-title">商家评分</div>
        <div class="rate-row"><span class="rate-label">总体</span><van-rate v-model="reviewForm.shopScoreOverall" /></div>
        <div class="rate-row"><span class="rate-label">口味</span><van-rate v-model="reviewForm.shopScoreTaste" /></div>
        <div class="rate-row"><span class="rate-label">包装</span><van-rate v-model="reviewForm.shopScorePack" /></div>
      </div>
      <!-- 自提单不派骑手，隐藏骑手评分 -->
      <div class="rate-group" v-if="!isPickup">
        <div class="rate-group-title">骑手评分</div>
        <div class="rate-row"><span class="rate-label">服务</span><van-rate v-model="reviewForm.riderScoreService" /></div>
        <div class="rate-row"><span class="rate-label">时效</span><van-rate v-model="reviewForm.riderScoreSpeed" /></div>
      </div>
      <van-field v-model="reviewForm.content" type="textarea" rows="2" autosize maxlength="200" placeholder="说说这次体验吧（选填）" />
      <div class="anon-row">
        <span>匿名评价</span>
        <van-switch v-model="reviewForm.anonymous" size="22" />
      </div>
      <div class="review-ops">
        <van-button round block type="primary" class="btn-yellow" :loading="submittingReview" @click="onSubmitReview">提交评价</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
// 订单详情：状态横幅/进度条/卡片结构按 PRD；操作逻辑沿用（支付/取消/催单/确认/评价 + 骑手位置轮询）
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showConfirmDialog, showDialog } from 'vant'
import api from '../../api'
import { orderDetail, pay, cancelOrder, addressList, riderPosition, urgeOrder, confirmOrder, submitReview, getReview } from '@/api'
import { getTradeSnapshot, getOrderCoupon, releaseCoupon } from '../../mock/userMock'

const router = useRouter()
const route = useRoute()
const orderNo = computed(() => route.params.no)

const detail = ref(null)
const paying = ref(false)
const riderText = ref('')
const userPos = ref(null)
let pollTimer = null

const urging = ref(false)
const confirming = ref(false)
const showReview = ref(false)
const submittingReview = ref(false)
const reviewForm = ref({
  shopScoreOverall: 5,
  shopScoreTaste: 5,
  shopScorePack: 5,
  riderScoreService: 5,
  riderScoreSpeed: 5,
  content: '',
  anonymous: false
})
const folded = ref(false)
const address = ref({})
const shopMap = ref({})
// B 批：订单券抵扣记录（mock；无记录为 null 不展示抵扣行）
const couponRec = ref(null)
// 我的评价（已评价时拉取，含商家回复与平台回复；未评价或被下架为 null）
const myReview = ref(null)

const order = computed(() => (detail.value && detail.value.order) || {})
const status = computed(() => Number(order.value.status || 0))
const reviewed = computed(() => {
  const d = detail.value
  if (!d) return false
  const v = d.reviewed != null ? d.reviewed : d.order && d.order.reviewed
  return Number(v || 0) === 1
})

// ===== 状态横幅 =====
const BANNER = {
  1: { icon: '🕐', text: '订单待支付', sub: '请尽快完成支付，超时订单将自动取消' },
  2: { icon: '📋', text: '等待商家接单', sub: '商家正在确认您的订单' },
  3: { icon: '📋', text: '商家已接单', sub: '正在为您匹配附近骑手' },
  9: { icon: '🍳', text: '商家备餐中', sub: '骑手已到店，餐品制作完成后即刻配送' },
  4: { icon: '🛵', text: '骑手正在飞速赶往商家', sub: '请耐心等待骑手取餐' },
  5: { icon: '🛵', text: '骑手配送中', sub: '请保持电话畅通' },
  11: { icon: '📦', text: '外卖已送达', sub: '请确认收货，感谢您的耐心等待' },
  6: { icon: '✅', text: '订单已完成', sub: '感谢您的惠顾，欢迎再次光临' },
  7: { icon: '❌', text: '订单已取消', sub: '期待您的下次光临' },
  8: { icon: '💰', text: '订单已退款', sub: '退款将原路退回，请留意到账通知' }
}
const isPickup = computed(() => order.value.deliveryType === 'PICKUP')
// 自提单横幅：去掉「匹配骑手 / 骑手已到店 / 外卖已送达」等配送口径
const PICKUP_BANNER = {
  2: { icon: '📋', text: '等待商家接单', sub: '商家确认后开始备餐' },
  3: { icon: '📋', text: '商家已接单', sub: '即将开始备餐，请留意取餐时间' },
  9: { icon: '🍳', text: '商家备餐中', sub: '商家正在备餐，出餐后请到店取餐' },
  11: { icon: '🍱', text: '餐已备好，请到店自提', sub: '确认收货后订单即完成' }
}
const bannerOf = (s) => (isPickup.value && PICKUP_BANNER[s]) || BANNER[s] || BANNER[1]
const bannerIcon = computed(() => bannerOf(status.value).icon)
const bannerText = computed(() => bannerOf(status.value).text)
const bannerSub = computed(() => bannerOf(status.value).sub)

// 进度条：仅正常流转订单显示（取消/退款不显示进度）
// 配送单：已下单→已接单→制作中→配送中→已送达
// 自提单：已下单→已接单→备餐中→待自提→已完成（自提不派骑手，正常流转无 4/5 状态）
const progressOn = computed(() => ![7, 8].includes(status.value))
const progressNodes = computed(() =>
  isPickup.value
    ? ['已下单', '已接单', '备餐中', '待自提', '已完成']
    : ['已下单', '已接单', '制作中', '配送中', '已送达']
)
const deliveryStepMap = { 1: 0, 2: 1, 3: 1, 9: 2, 4: 2, 5: 3, 11: 4, 6: 4 }
const pickupStepMap = { 1: 0, 2: 1, 3: 1, 9: 2, 4: 2, 5: 2, 11: 3, 6: 4 }
const step = computed(() => {
  const s = status.value
  const map = isPickup.value ? pickupStepMap : deliveryStepMap
  return map[s] != null ? map[s] : 0
})

const statusColor = computed(() => {
  const map = { 1: '#FF9A14', 2: '#1989FA', 3: '#1989FA', 9: '#FFC300', 4: '#FFC300', 5: '#1989FA', 11: '#FFC300', 6: '#07C160', 7: '#C8C9CC', 8: '#C8C9CC' }
  return map[status.value] || '#C8C9CC'
})

// ===== 商家与商品 =====
const shopEmoji = '🏪'
const dishEmoji = '🍜'
const shopNameText = computed(() => shopMap.value[order.value.shopId] || '商家 #' + (order.value.shopId || ''))

// ===== 金额 =====
const goodsTotal = computed(() =>
  (order.value.items || []).reduce((sum, i) => sum + Number(i.unitPrice || i.price || 0) * Number(i.num || 0), 0)
)
const discount = computed(() => {
  const payAmount = Number(order.value.payAmount || order.value.total || 0)
  const d = goodsTotal.value + Number(order.value.deliveryFee || 0) - payAmount
  return d > 0 ? d : 0
})

// ===== 物流与收货 =====
// 送达时间：从状态日志找 toStatus=11 的时间；配送中显示期望送达时间
const deliverText = computed(() => {
  const t = order.value.expectTime
  // 自提：展示用户选择的「期望自提」时间，与商家端订单卡一致
  if (isPickup.value) {
    if ([11, 6].includes(status.value)) return '已备好，待自提'
    return t ? '期望自提 ' + t : '出餐后请到店取餐'
  }
  if ([11, 6].includes(status.value)) {
    const log = (detail.value && detail.value.logs || []).find((l) => Number(l.toStatus) === 11)
    if (log) return '已送达 ' + (log.time || '').toString().slice(11, 19)
    return '已送达'
  }
  if (status.value === 5) return '预计送达 ' + (t || '12:30')
  return t ? '期望送达 ' + t : '商家自送，准时送达'
})
const receiverText = computed(() => {
  const a = address.value
  if (!a || !a.name) return '用户'
  return a.name + ' ' + String(a.phone || '').replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
})
const addressDetail = computed(() => (address.value && address.value.detail) || '郑州大学主校区（默认地址）')

// ===== 操作按钮组（PRD：主按钮红 #FF5000，次按钮浅灰） =====
const ops = computed(() => {
  const s = status.value
  const list = []
  if (s === 1) {
    list.push({ text: '去支付', primary: true, onClick: onPay })
    list.push({ text: '取消订单', primary: false, onClick: onCancel })
  } else if (s === 2) {
    // 「随时确认收货」：已支付后的任意非终态均可确认完成
    list.push({ text: '确认收货', primary: true, onClick: onConfirmReceive })
    list.push({ text: '取消订单', primary: false, onClick: onCancel })
    list.push({ text: '联系客服', primary: false, onClick: onService })
  } else if ([3, 9, 4, 5].includes(s)) {
    list.push({ text: '确认收货', primary: true, onClick: onConfirmReceive })
    list.push({ text: '催单', primary: false, onClick: onUrge })
    list.push({ text: '联系客服', primary: false, onClick: onService })
  } else if (s === 11) {
    list.push({ text: '确认收货', primary: true, onClick: onConfirmReceive })
    list.push({ text: '催单', primary: false, onClick: onUrge })
  } else if (s === 6) {
    // 评价后按钮保留但置灰禁用，文字改「已评价」（不再直接消失）
    list.push({
      text: reviewed.value ? '已评价' : '去评价',
      primary: !reviewed.value,
      disabled: reviewed.value,
      onClick: () => (showReview.value = true)
    })
    list.push({ text: '再来一单', primary: reviewed.value, onClick: onAgain })
  } else if ([7, 8].includes(s)) {
    list.push({ text: '再来一单', primary: true, onClick: onAgain })
    list.push({ text: '联系客服', primary: false, onClick: onService })
  }
  return list
})

const onAgain = () => router.push('/user/shop/' + order.value.shopId)
// P1：联系客服统一跳转客服中心页（热线/在线客服/FAQ）
const onService = () => router.push('/user/service')

const onCopy = async () => {
  try {
    await navigator.clipboard.writeText(order.value.orderNo)
    showToast('复制成功')
  } catch (e) {
    showToast('复制失败，请手动复制')
  }
}

// 分享：复制订单信息（店铺 + 商品 + 金额），便于演示分享场景
const onShare = async () => {
  const items = (order.value.items || []).map((i) => i.dishName + ' x' + i.num).join('、')
  const text = '【外卖订单】' + shopNameText.value + '：' + items + '，实付 ¥' + Number(order.value.payAmount || order.value.total || 0).toFixed(2)
  try {
    await navigator.clipboard.writeText(text)
    showToast('订单信息已复制，可分享给好友')
  } catch (e) {
    showToast('复制失败，请手动复制')
  }
}

const showSnapshot = async () => {
  const snap = await getTradeSnapshot(orderNo.value)
  const text = snap.rows.map((r) => r.label + '：' + r.value).join('\n')
  showDialog({ title: snap.title + '（' + snap.subTitle + '）', message: text })
}

// ===== 既有动作逻辑（保留） =====
const calcDistance = (lng1, lat1, lng2, lat2) => {
  const x = Math.abs(lng1 - lng2) * 111320 * Math.cos((lat1 * Math.PI) / 180)
  const y = Math.abs(lat1 - lat2) * 110540
  return Math.sqrt(x * x + y * y)
}

// 已评价时拉取自己的评价（含商家/平台回复）；未评价或被下架返回 null
const loadMyReview = async () => {
  if (!reviewed.value) {
    myReview.value = null
    return
  }
  try {
    myReview.value = await getReview(orderNo.value)
  } catch (e) {
    myReview.value = null
  }
}

const load = async () => {
  try {
    detail.value = await orderDetail(orderNo.value)
    // B 批：读取该订单的券抵扣记录（无记录返回 null）
    couponRec.value = getOrderCoupon(orderNo.value)
    await loadMyReview()
    startPollingIfNeeded()
  } catch (e) {
    // 拦截器已提示
  }
}

const startPollingIfNeeded = () => {
  stopPolling()
  const o = detail.value && detail.value.order
  if (o && [4, 5, 6].includes(Number(o.status)) && o.riderId) {
    pollRider()
    pollTimer = setInterval(pollRider, 3000)
  }
}

const pollRider = async () => {
  try {
    const riderId = detail.value.order.riderId
    const pos = await riderPosition(riderId)
    if (pos && pos.lng != null && pos.lat != null && userPos.value) {
      const d = calcDistance(pos.lng, pos.lat, userPos.value.lng, userPos.value.lat)
      riderText.value = d >= 1000 ? '骑手距您 ' + (d / 1000).toFixed(1) + 'km' : '骑手距您 ' + Math.round(d) + 'm'
    }
  } catch (e) {
    // 轮询失败静默
  }
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const onPay = async () => {
  paying.value = true
  try {
    await pay(orderNo.value)
    showToast('支付成功')
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    paying.value = false
  }
}

const onCancel = async () => {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确认取消该订单吗？' })
  } catch (e) {
    return
  }
  try {
    await cancelOrder(orderNo.value, '用户主动取消')
    // B 批：取消订单退券（有抵扣记录才退）
    if (releaseCoupon(orderNo.value)) {
      showToast('订单已取消，优惠券已退回')
    } else {
      showToast('订单已取消')
    }
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

const onUrge = async () => {
  urging.value = true
  try {
    await urgeOrder(orderNo.value)
    showToast('已催单，商家与骑手将尽快处理')
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    urging.value = false
  }
}

const onConfirmReceive = async () => {
  try {
    await showConfirmDialog({
      title: '确认收货',
      message: isPickup.value
        ? '确认已到店取餐吗？提前确认将直接完结订单'
        : '确认已收到外卖吗？提前确认将直接完结订单'
    })
  } catch (e) {
    return
  }
  confirming.value = true
  try {
    await confirmOrder(orderNo.value)
    showToast('已确认收货')
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    confirming.value = false
  }
}

const onSubmitReview = async () => {
  const f = reviewForm.value
  // 自提单不派骑手，无需评价骑手两项
  const needRider = !isPickup.value
  if (!f.shopScoreOverall || !f.shopScoreTaste || !f.shopScorePack ||
      (needRider && (!f.riderScoreService || !f.riderScoreSpeed))) {
    showToast('请完成所有评分')
    return
  }
  submittingReview.value = true
  try {
    await submitReview(orderNo.value, {
      shopScoreOverall: f.shopScoreOverall,
      shopScoreTaste: f.shopScoreTaste,
      shopScorePack: f.shopScorePack,
      riderScoreService: isPickup.value ? null : f.riderScoreService,
      riderScoreSpeed: isPickup.value ? null : f.riderScoreSpeed,
      content: f.content,
      images: '',
      anonymous: f.anonymous ? 1 : 0
    })
    showToast('评价成功')
    showReview.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submittingReview.value = false
  }
}

const loadUserPos = async () => {
  try {
    const list = (await addressList()) || []
    const addr = list.find((a) => a.isDefault === 1) || list[0]
    if (addr) {
      address.value = addr
      if (addr.lng != null && addr.lat != null) {
        userPos.value = { lng: Number(addr.lng), lat: Number(addr.lat) }
      }
    }
  } catch (e) {
    // 静默处理
  }
}

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

watch(orderNo, () => {
  if (orderNo.value) load()
})

onMounted(async () => {
  await loadUserPos()
  await loadShopMap()
  await load()
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.odetail {
  min-height: 100vh;
  background: var(--mt-bg);
  /* 底部 4 Tab 常驻，给底部操作按钮组（去支付/确认收货/去评价）多留 50px 让位 */
  padding-bottom: 74px;
}
.nav {
  background: #fff;
}
.nav :deep(.van-nav-bar__title) {
  font-size: 17px;
  color: #333;
  font-weight: 600;
}

/* ===== 状态横幅 ===== */
.banner {
  background: var(--mt-gradient);
  padding: 22px 16px 18px;
}
.banner-main {
  display: flex;
  align-items: center;
  gap: 12px;
}
.banner-icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
  border: 2px solid var(--mt-yellow);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}
.banner-text {
  flex: 1;
}
.banner-status {
  font-size: 18px;
  font-weight: 700;
  color: #333;
}
.banner-sub {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(51, 51, 51, 0.65);
}
/* 进度条 */
.progress {
  margin-top: 18px;
  display: flex;
  align-items: flex-start;
}
.node {
  flex: 1;
  display: flex;
  align-items: center;
  position: relative;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  flex-shrink: 0;
  z-index: 1;
}
.dot.done,
.dot.current {
  background: #fff;
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.35);
}
.line {
  flex: 1;
  height: 2px;
  background: rgba(255, 255, 255, 0.55);
}
.line.done {
  background: #fff;
}
.node-label {
  position: absolute;
  top: 14px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 9px;
  color: rgba(51, 51, 51, 0.7);
  white-space: nowrap;
}
.progress-off {
  margin-top: 14px;
  font-size: 12px;
  color: rgba(51, 51, 51, 0.5);
}

/* ===== 卡片 ===== */
.card {
  background: #fff;
  border-radius: var(--mt-radius);
  margin: 12px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
/* 商家行 */
.merchant {
  display: flex;
  align-items: center;
}
.merchant-logo {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(135deg, #ffe082, #ffc300);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.merchant-info {
  flex: 1;
  margin-left: 10px;
}
.merchant-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.merchant-cat {
  margin-top: 2px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.merchant-status {
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
  padding: 3px 10px;
  flex-shrink: 0;
}
/* 商品行 */
.goods {
  margin-top: 12px;
}
.goods-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
}
.goods-thumb {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: #fff7e0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}
.goods-info {
  flex: 1;
  margin-left: 10px;
  min-width: 0;
}
.goods-name {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.goods-spec {
  margin-top: 2px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.goods-num {
  margin-top: 2px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.goods-price {
  font-size: 14px;
  font-weight: 700;
  color: #ff5000;
  flex-shrink: 0;
}
.aftersale-tip {
  margin-top: 8px;
  font-size: 11px;
  color: #ff9a14;
}

/* ===== 金额明细 ===== */
.amount-row {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
}
.amount-label {
  font-size: 12px;
  color: var(--mt-text-2);
}
.amount-value {
  font-size: 12px;
  color: #333;
}
.amount-row.discount .amount-value,
.amount-row.save .amount-value {
  color: #ff5000;
}
/* B 批：神券抵扣行 */
.amount-row.coupon .amount-value {
  color: #b8860b;
  font-weight: 600;
}
.coupon-sub {
  margin-left: 6px;
  font-size: 10px;
  color: var(--mt-text-3);
  font-weight: 400;
}
.amount-row.muted {
  opacity: 0.5;
}
.amount-row.total {
  border-top: 1px solid #f5f5f5;
  margin-top: 4px;
  padding-top: 10px;
}
.amount-total {
  font-size: 16px;
  font-weight: 700;
  color: #ff5000;
}
.order-no-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.order-no {
  font-size: 11px;
  color: var(--mt-text-3);
}
.copy-btn {
  font-size: 11px;
  color: #b8860b;
  font-weight: 600;
  cursor: pointer;
}

/* ===== 物流收货 ===== */
.logi-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
}
.logi-icon {
  font-size: 15px;
  margin-right: 8px;
  flex-shrink: 0;
}
.logi-text {
  flex: 1;
  font-size: 13px;
  color: #333;
}
.logi-time {
  font-size: 11px;
  color: var(--mt-text-3);
}
.divider {
  height: 1px;
  background: #f5f5f5;
  margin: 8px 0;
}
.addr-row {
  display: flex;
}
.addr-info {
  flex: 1;
  min-width: 0;
}
.addr-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.addr-name {
  font-size: 13px;
  color: #333;
  font-weight: 600;
}
.addr-protect {
  font-size: 10px;
  color: var(--mt-success);
  border: 1px solid var(--mt-success);
  border-radius: 4px;
  padding: 0 4px;
}
.addr-detail {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mt-text-2);
  line-height: 1.5;
}
.addr-tip {
  margin-top: 4px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.fold {
  margin-top: 8px;
  text-align: center;
  font-size: 11px;
  color: var(--mt-text-3);
  cursor: pointer;
}

/* ===== 交易快照 ===== */
.snapshot {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.snap-icon {
  font-size: 20px;
  margin-right: 10px;
}
.snap-info {
  flex: 1;
}
.snap-title {
  font-size: 14px;
  color: #333;
  font-weight: 600;
}
.snap-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.snap-arrow {
  font-size: 12px;
  color: var(--mt-text-3);
}

/* ===== 操作按钮组 ===== */
.ops {
  margin: 16px 12px 0;
  display: flex;
  gap: 12px;
}
.op-btn {
  flex: 1;
  height: 44px;
  line-height: 44px;
  text-align: center;
  border-radius: 22px;
  font-size: 14px;
  font-weight: 500;
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

/* ===== 我的评价卡 ===== */
.my-review-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}
.my-review-scores {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mt-text-3);
}
.my-review-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--mt-text-2);
}
.my-review-reply {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f7f8fa;
  border-radius: 8px;
  font-size: 12px;
  color: var(--mt-text-2);
}
.my-review-reply.platform {
  background: #ecf5ff;
  color: #185fa5;
}

/* ===== 评价弹窗 ===== */
.review-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  text-align: center;
  margin-bottom: 8px;
}
.rate-group {
  margin-bottom: 12px;
}
.rate-group-title {
  font-size: 13px;
  color: var(--mt-text-3);
  margin-bottom: 4px;
}
.rate-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}
.rate-label {
  font-size: 14px;
  color: #333;
}
.anon-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  font-size: 14px;
  color: #333;
}
.review-ops {
  margin-top: 8px;
}
.btn-yellow {
  --van-button-primary-background: var(--mt-yellow, #ffc300);
  --van-button-primary-border-color: var(--mt-yellow, #ffc300);
  --van-button-primary-color: #333;
  --van-button-radius: var(--mt-radius-btn, 24px);
}
</style>
