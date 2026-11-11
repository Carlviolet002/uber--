<template>
  <!-- 神券频道：累计已省横幅 + 我的神券 + 天天膨胀 + 神券包推广 -->
  <div class="coupons">
    <!-- 粉色渐变横幅：累计已省 -->
    <div class="banner">
      <div class="banner-mascot">🐰</div>
      <div class="banner-text">
        <div class="banner-name">{{ shortNick }}</div>
        <div class="banner-saved">累计已省 <span class="banner-num">{{ assets.savedTotal }}</span> 元</div>
      </div>
      <div class="banner-tip">神券越多 省的越多</div>
    </div>

    <!-- 领券中心（B 批：可领券池 6 张，领取后按钮灰态，领完显示空态） -->
    <div class="section">
      <div class="section-head">
        <span class="section-title">领券中心</span>
        <span class="section-sub">每日上新 · 先到先得</span>
      </div>
      <van-skeleton v-if="claimLoading" title :row="2" class="coupon-skeleton" />
      <div v-else-if="allClaimed" class="center-empty">🎉 今日券已领完，明天再来～</div>
      <div v-else class="coupon-scroll">
        <CouponCard
          v-for="c in claimable"
          :key="c.id"
          :coupon="c"
          mode="claim"
          @claim="onClaim"
        />
      </div>
    </div>

    <!-- 我的神券 -->
    <div class="section">
      <div class="section-head">
        <span class="section-title">我的神券</span>
        <span class="section-more" @click="router.push('/user/coupons/manage')">可用 {{ availableCount }} 张 ›</span>
      </div>
      <van-skeleton v-if="loading" title :row="2" class="coupon-skeleton" />
      <div v-else class="coupon-scroll">
        <CouponCard v-for="c in myAvailable" :key="c.id" :coupon="c" @click="onCouponClick(c)" />
      </div>
    </div>

    <!-- 天天膨胀 -->
    <div class="inflate" @click="onInflate">
      <span class="inflate-emoji">🧧</span>
      <span class="inflate-text">天天膨一下，随机掉落 18 元外卖神券</span>
      <span class="inflate-arrow">›</span>
    </div>

    <!-- 神券包推广 -->
    <div class="section">
      <div class="section-head">
        <span class="section-title">神券包</span>
        <span class="section-sub">已售 {{ bundle.sold }} 份</span>
      </div>
      <div class="bundle">
        <div class="bundle-left">
          <div class="bundle-desc">{{ bundle.desc }}</div>
          <div class="bundle-price-row">
            <span class="bundle-price">¥{{ bundle.price }}</span>
            <span class="bundle-origin">¥{{ bundle.originPrice }}</span>
          </div>
        </div>
        <div class="bundle-btn" :class="{ bought: bundle.bought }" @click="onBuyBundle">
          {{ bundle.bought ? '已购买' : '购买' }}
        </div>
      </div>
    </div>

    <!-- 使用说明 -->
    <div class="section">
      <van-collapse v-model="expanded">
        <van-collapse-item title="神券使用说明" name="1">
          <div class="faq-text">
            1. 神券仅在有效期内在指定商家/品类使用，不可提现、不可转赠。<br />
            2. 下单时符合门槛自动抵扣，每单限用一张。<br />
            3. 发生退款时，未过期神券自动退回账户。<br />
            4. 神券包购买后不支持退款，券自动到账。
          </div>
        </van-collapse-item>
      </van-collapse>
    </div>
  </div>
</template>

<script setup>
// 神券频道：纯前端 mock（userMock），领取/购买状态 localStorage 持久化；B 批新增领券中心区
import { ref, computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '../../stores/user'
import CouponCard from '../../components/user/CouponCard.vue'
import { useLock } from '../../composables/useLock'
import {
  getMyCoupons,
  getMyCouponCount,
  getMyAssets,
  getCouponBundle,
  claimCoupon,
  buyCouponBundle,
  getClaimableCoupons,
  claimFromCenter
} from '../../mock/userMock'

defineOptions({ name: 'UserCoupons' })

const router = useRouter()
const userStore = useUserStore()

const coupons = ref([])
const assets = ref({ savedTotal: 58 })
const bundle = ref({ bought: false, sold: 0 })
const expanded = ref([])
const loading = ref(true)
const claimable = ref([])
const claimLoading = ref(true)
const availableCount = ref(0)
const { run: runClaim } = useLock()

// 领券中心全部领完 → 空态
const allClaimed = computed(() => claimable.value.length > 0 && claimable.value.every((c) => c.claimed))

// 我的神券横滑只展示可用券（used/expired 在券管理页查看）
const myAvailable = computed(() => coupons.value.filter((c) => c.status === 'available'))

// 昵称打码显示：取前 8 位 + ...
const shortNick = (() => {
  const nick = userStore.userInfo.nickname || userStore.userInfo.username || 'LkB889885562'
  return nick.length > 8 ? nick.slice(0, 8) + '...' : nick
})()

const load = async () => {
  coupons.value = await getMyCoupons()
  assets.value = await getMyAssets()
  bundle.value = await getCouponBundle()
  claimable.value = await getClaimableCoupons()
  availableCount.value = getMyCouponCount()
  loading.value = false
  claimLoading.value = false
}

const onClaim = (c) => {
  if (c.claimed) {
    showToast('该券已领取')
    return
  }
  runClaim(async () => {
    if (claimFromCenter(c.id)) {
      showToast('领取成功')
      await load()
    } else {
      showToast('该券已领取')
    }
  })
}

const onCouponClick = (c) => {
  if (c.type === 'shop') {
    showToast('「' + (c.shopName || '') + '」店铺券，下单满' + c.desc.replace('满', '') + '自动抵扣')
  } else {
    showToast('¥' + c.amount + ' ' + c.desc + '红包，下单自动抵扣')
  }
}

const onInflate = () => {
  if (claimCoupon(101)) {
    showToast('恭喜获得 ¥18 外卖神券！')
    load()
  } else {
    showToast('今天已经膨胀过啦，明天再来')
  }
}

const onBuyBundle = () => {
  if (buyCouponBundle()) {
    showToast('购买成功，5元×20张神券已到账')
    load()
  } else {
    showToast('神券包已购买')
  }
}

// keep-alive：首次挂载与每次激活都刷新（领取/购买状态保持最新）
onActivated(load)
</script>

<style scoped>
.coupons {
  min-height: 100vh;
  background: var(--mt-bg);
  padding-bottom: 16px;
}

/* ===== 粉渐变横幅 ===== */
.banner {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #ffe3ec, #ffd0dd);
  padding: 20px 16px;
  gap: 12px;
  border-radius: 0 0 20px 20px;
}
.banner-mascot {
  font-size: 44px;
  background: #fff;
  border-radius: 50%;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.06);
}
.banner-text {
  flex: 1;
}
.banner-name {
  font-size: 14px;
  font-weight: 700;
  color: #333;
}
.banner-saved {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mt-text-2);
}
.banner-num {
  color: #ff5000;
  font-size: 18px;
  font-weight: 700;
}
.banner-tip {
  font-size: 11px;
  color: #ff6f91;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 12px;
  padding: 4px 10px;
  flex-shrink: 0;
}

/* ===== 通用 section ===== */
.section {
  background: #fff;
  margin: 10px 8px 0;
  border-radius: var(--mt-radius);
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #333;
}
.section-more {
  font-size: 12px;
  color: #ff5000;
  font-weight: 600;
}
.section-sub {
  font-size: 12px;
  color: var(--mt-text-3);
}

/* ===== 券列表横向滚动 ===== */
.coupon-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: none;
}
.coupon-scroll::-webkit-scrollbar {
  display: none;
}
.coupon-skeleton {
  padding: 4px 0;
}
.center-empty {
  padding: 18px 0;
  text-align: center;
  font-size: 13px;
  color: var(--mt-text-3);
}

/* ===== 天天膨胀 ===== */
.inflate {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 8px 0;
  background: var(--mt-gradient);
  border-radius: var(--mt-radius);
  padding: 14px 16px;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(255, 195, 0, 0.35);
}
.inflate-emoji {
  font-size: 26px;
}
.inflate-text {
  flex: 1;
  font-size: 14px;
  font-weight: 700;
  color: #333;
}
.inflate-arrow {
  font-size: 18px;
  color: rgba(51, 51, 51, 0.6);
}

/* ===== 神券包 ===== */
.bundle {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bundle-desc {
  font-size: 14px;
  font-weight: 700;
  color: #333;
}
.bundle-price-row {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.bundle-price {
  font-size: 22px;
  font-weight: 800;
  color: #ff5000;
}
.bundle-origin {
  font-size: 13px;
  color: var(--mt-text-3);
  text-decoration: line-through;
}
.bundle-btn {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b6b, #ff5000);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(255, 80, 0, 0.35);
}
.bundle-btn.bought {
  background: #c8c9cc;
  box-shadow: none;
}

/* ===== 使用说明 ===== */
.faq-text {
  font-size: 12px;
  color: var(--mt-text-2);
  line-height: 1.8;
}
</style>
