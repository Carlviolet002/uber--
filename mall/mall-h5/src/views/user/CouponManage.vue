<template>
  <!-- 券管理页：可用 / 即将过期 / 已使用 三组，券卡点开详情层 -->
  <div class="coupon-manage">
    <van-nav-bar title="券管理" left-arrow fixed placeholder @click-left="router.back()" />
    <!-- 顶部统计条 -->
    <div class="stats-bar">
      <div class="stats-item">
        <div class="stats-num">{{ available.length }}</div>
        <div class="stats-label">可用</div>
      </div>
      <div class="stats-divider"></div>
      <div class="stats-item">
        <div class="stats-num warn">{{ expiring.length }}</div>
        <div class="stats-label">即将过期</div>
      </div>
      <div class="stats-divider"></div>
      <div class="stats-item">
        <div class="stats-num">{{ used.length }}</div>
        <div class="stats-label">已使用</div>
      </div>
    </div>

    <van-tabs v-model:active="activeTab" sticky color="#FFC300" title-active-color="#333">
      <van-tab title="可用">
        <BaseEmpty v-if="!available.length" description="暂无可用券" action-text="去领神券" image="coupon-o" @action="router.push('/user/coupons')" />
        <div v-else class="coupon-col">
          <CouponCard v-for="c in available" :key="c.id + '-' + tick" :coupon="c" @click="openDetail(c)" />
        </div>
      </van-tab>
      <van-tab title="即将过期">
        <BaseEmpty v-if="!expiring.length" description="暂无即将过期的券" image="coupon-o" />
        <div v-else class="coupon-col">
          <CouponCard v-for="c in expiring" :key="c.id + '-' + tick" :coupon="c" @click="openDetail(c)" />
        </div>
      </van-tab>
      <van-tab title="已使用">
        <BaseEmpty v-if="!used.length" description="暂无已使用的券" image="coupon-o" />
        <div v-else class="coupon-col">
          <CouponCard v-for="c in used" :key="c.id + '-' + tick" :coupon="c" @click="openDetail(c)" />
        </div>
      </van-tab>
    </van-tabs>

    <!-- 券详情弹层 -->
    <van-popup v-model:show="detailShow" position="bottom" round class="detail-popup">
      <template v-if="detail">
        <div class="detail-head">
          <div class="detail-amount">
            <span class="detail-yen">¥</span>
            <span class="detail-num">{{ detail.amount }}</span>
          </div>
          <div class="detail-meta">
            <div class="detail-desc">{{ detail.desc }}</div>
            <div class="detail-scope">{{ detail.shopName || detail.scope }}</div>
          </div>
        </div>
        <div class="detail-rows">
          <div class="detail-row"><span class="row-label">有效期</span><span class="row-value">至 {{ expireText }}</span></div>
          <div class="detail-row"><span class="row-label">使用门槛</span><span class="row-value">{{ detail.desc }}</span></div>
          <div class="detail-row"><span class="row-label">适用范围</span><span class="row-value">{{ detail.shopName || detail.scope }}</span></div>
          <div class="detail-row"><span class="row-label">使用说明</span><span class="row-value">每单限用一张，不可提现、不可转赠</span></div>
        </div>
        <div class="detail-btn" @click="detailShow = false">我知道了</div>
      </template>
    </van-popup>
  </div>
</template>

<script setup>
// 券管理页：B 批切 getCouponGroups（mock 统一分组）；「仅剩 X 小时」角标每分钟刷新（key 重挂载 + 定时重拉）
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import CouponCard from '../../components/user/CouponCard.vue'
import BaseEmpty from '../../components/user/BaseEmpty.vue'
import { getCouponGroups } from '../../mock/userMock'

const router = useRouter()

const activeTab = ref(0)
const groups = ref({ available: [], expiring: [], used: [] })
const detailShow = ref(false)
const detail = ref(null)
// 每分钟自增：作为券卡 key 的一部分强制重挂载，CouponCard 内「仅剩 X 小时」角标随之重算
const tick = ref(0)
let timer = null

const available = computed(() => groups.value.available)
const expiring = computed(() => groups.value.expiring)
const used = computed(() => groups.value.used)

const expireText = computed(() => {
  if (!detail.value || !detail.value.expireAt) return '领取后 7 天'
  const d = new Date(detail.value.expireAt)
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
})

const openDetail = (c) => {
  detail.value = c
  detailShow.value = true
}

const load = async () => {
  groups.value = await getCouponGroups()
}

onMounted(async () => {
  await load()
  // 每分钟重拉分组（过期状态实时迁移）+ 刷新角标
  timer = setInterval(() => {
    tick.value++
    load()
  }, 60000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.coupon-manage {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg);
  padding-bottom: 20px;
}
.stats-bar {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 8px;
  border-radius: var(--mt-radius);
  padding: 14px 0;
}
.stats-item {
  flex: 1;
  text-align: center;
}
.stats-num {
  font-size: 22px;
  font-weight: 800;
  color: #333;
}
.stats-num.warn {
  color: #ff9a14;
}
.stats-label {
  margin-top: 2px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.stats-divider {
  width: 1px;
  height: 28px;
  background: #f0f0f0;
}
.coupon-col {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
}
.coupon-col :deep(.coupon-card) {
  width: calc(50% - 5px);
}
/* ===== 详情弹层 ===== */
.detail-popup {
  border-radius: 20px 20px 0 0;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 16px 12px;
  border-bottom: 1px dashed #eee;
}
.detail-amount {
  display: flex;
  align-items: baseline;
  color: #ff5000;
}
.detail-yen {
  font-size: 14px;
  font-weight: 700;
}
.detail-num {
  font-size: 34px;
  font-weight: 800;
}
.detail-meta {
  flex: 1;
}
.detail-desc {
  font-size: 14px;
  font-weight: 700;
  color: #333;
}
.detail-scope {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mt-text-3);
}
.detail-rows {
  padding: 6px 16px;
}
.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
}
.row-label {
  color: var(--mt-text-3);
}
.row-value {
  color: #333;
  max-width: 60%;
  text-align: right;
}
.detail-btn {
  margin: 8px 16px 0;
  height: 44px;
  line-height: 44px;
  text-align: center;
  border-radius: 22px;
  background: var(--mt-gradient);
  color: #333;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
</style>
