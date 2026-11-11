<template>
  <!-- 确认订单页：选择收货地址 + 商品清单 + 提交订单 -->
  <div class="page">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />
    <!-- 配送方式：外卖配送 / 到店自提（自提免配送费且不派骑手） -->
    <van-cell-group inset title="配送方式">
      <van-radio-group v-model="deliveryType">
        <van-cell
          title="外卖配送"
          label="骑手配送 · 配送费 ¥5.00"
          clickable
          @click="deliveryType = 'DELIVERY'"
        >
          <template #right-icon><van-radio name="DELIVERY" /></template>
        </van-cell>
        <van-cell
          title="到店自提"
          label="到店取餐 · 免配送费 · 不派骑手"
          clickable
          @click="deliveryType = 'PICKUP'"
        >
          <template #right-icon><van-radio name="PICKUP" /></template>
        </van-cell>
      </van-radio-group>
    </van-cell-group>
    <!-- 收货地址（自提时隐藏） -->
    <van-cell-group v-if="!isPickup" inset title="收货地址">
      <van-cell
        is-link
        :title="currentAddress ? currentAddress.receiverName + ' ' + currentAddress.receiverPhone : '请选择收货地址'"
        :label="currentAddress ? currentAddress.detail : ''"
        @click="showAddressSheet = true"
      />
    </van-cell-group>
    <!-- 商品清单 -->
    <van-cell-group inset title="商品清单">
      <van-cell
        v-for="item in cartStore.items"
        :key="item.dishId"
        :title="item.name"
        :label="'x' + item.num"
        :value="'¥' + (Number(item.price) * item.num).toFixed(2)"
      />
    </van-cell-group>
    <!-- 备注 / 餐具 / 期望时间 -->
    <van-cell-group inset title="订单要求">
      <van-field
        v-model="remark"
        label="备注"
        placeholder="口味、偏好等要求（选填）"
        maxlength="50"
      />
      <van-field label="餐具份数">
        <template #input>
          <van-stepper v-model="tableware" min="0" max="10" integer theme="round" />
        </template>
      </van-field>
      <div class="expect-wrap">
        <div class="expect-label">{{ isPickup ? '期望自提' : '期望送达' }}</div>
        <div class="expect-chips">
          <span
            v-for="t in expectOptions"
            :key="t"
            class="expect-chip"
            :class="{ active: expectTime === t }"
            @click="expectTime = t"
          >
            {{ t }}
          </span>
        </div>
      </div>
    </van-cell-group>
    <!-- 优惠券（B 批：mock 选券抵扣，可选用券按门槛过滤） -->
    <van-cell-group inset title="优惠券">
      <van-skeleton v-if="couponLoading" title :row="2" class="coupon-skeleton" />
      <template v-else>
        <div v-if="usableCoupons.length === 0" class="coupon-empty">暂无满足门槛的优惠券</div>
        <div v-else class="coupon-select-scroll">
          <div
            class="coupon-none"
            :class="{ on: selectedCouponId === null }"
            @click="selectedCouponId = null"
          >
            <span class="coupon-none__title">不使用优惠券</span>
            <span v-if="selectedCouponId === null" class="coupon-none__check">✓</span>
          </div>
          <CouponCard
            v-for="c in usableCoupons"
            :key="c.id"
            :coupon="c"
            mode="select"
            :selected="selectedCouponId === c.id"
            @click="selectedCouponId = c.id"
          />
        </div>
      </template>
    </van-cell-group>
    <!-- 费用明细 -->
    <van-cell-group inset>
      <van-cell title="商品金额" :value="'¥' + cartStore.totalPrice.toFixed(2)" />
      <van-cell title="配送费" :value="isPickup ? '¥0.00' : '¥5.00'" />
      <van-cell v-if="discount > 0" title="神券抵扣" :value="'-¥' + discount.toFixed(2)" class="discount-cell" label="预计优惠 · mock" />
      <van-cell title="合计" :value="'¥' + payAmount.toFixed(2)" class="total-cell" />
    </van-cell-group>
    <div class="submit">
      <van-button
        round
        block
        type="primary"
        :loading="submitting"
        :disabled="(!isPickup && !currentAddress) || cartStore.items.length === 0"
        @click="submitOrder"
      >
        提交订单
      </van-button>
      <van-button round block plain type="primary" class="view-orders" @click="router.push('/user/orders')">
        查看我的订单
      </van-button>
    </div>
    <!-- 地址选择弹层 -->
    <van-action-sheet
      v-model:show="showAddressSheet"
      title="选择收货地址"
      :actions="addressActions"
      cancel-text="取消"
      @select="onSelectAddress"
    />
  </div>
</template>

<script setup>
// 确认订单页：选择地址（默认地址优先）、选券抵扣（B 批 mock 计价）、提交订单（幂等键防重复下单）
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import api from '../../api'
import { useCartStore } from '../../stores/cart'
import CouponCard from '../../components/user/CouponCard.vue'
import { getCouponGroups, computeDiscount, writeOrderCoupon } from '../../mock/userMock'

const router = useRouter()
const cartStore = useCartStore()

const addresses = ref([])
const currentAddress = ref(null)
const showAddressSheet = ref(false)
const submitting = ref(false)

// 订单要求：备注 / 餐具 / 期望送达(自提)时间
const remark = ref('')
const tableware = ref(0)
const expectTime = ref('尽快送出')
// 自提时首项改「尽快取餐」，标签与提交值随配送方式区分语义
const expectOptions = computed(() => [
  isPickup.value ? '尽快取餐' : '尽快送出',
  '11:30', '12:00', '12:30', '13:00'
])

// 配送方式：DELIVERY 外卖配送（默认）/ PICKUP 到店自提
const deliveryType = ref('DELIVERY')
const isPickup = computed(() => deliveryType.value === 'PICKUP')
const deliveryFee = computed(() => (isPickup.value ? 0 : 5))
// 切换配送方式时重置期望时间为对应默认值，避免「尽快送出」出现在自提单上
watch(isPickup, (v) => {
  expectTime.value = v ? '尽快取餐' : '尽快送出'
})

// 选券抵扣（B 批：纯前端 mock 计价，实付以后端计价为准）
const couponLoading = ref(true)
const usableCoupons = ref([])
const selectedCouponId = ref(null)

const selectedCoupon = computed(() => usableCoupons.value.find((c) => c.id === selectedCouponId.value) || null)
const discount = computed(() =>
  selectedCoupon.value ? computeDiscount(selectedCoupon.value, cartStore.totalPrice) : 0
)
const payAmount = computed(() =>
  Math.max(0, Number((cartStore.totalPrice + deliveryFee.value - discount.value).toFixed(2)))
)

// 合计金额 = 商品总价 + 配送费（自提 0 元）- 神券抵扣（最低 0）
const totalAmount = computed(() => payAmount.value)

// 地址弹层选项
const addressActions = computed(() =>
  addresses.value.map((a, i) => ({
    name: a.receiverName + ' ' + a.receiverPhone + ' ' + a.detail,
    value: i
  }))
)

// 选择地址
const onSelectAddress = (action) => {
  currentAddress.value = addresses.value[action.value]
  showAddressSheet.value = false
}

// 可选用券：available 中满足门槛（computeDiscount > 0）的券
const loadCoupons = async () => {
  const groups = await getCouponGroups()
  usableCoupons.value = groups.available.filter((c) => computeDiscount(c, cartStore.totalPrice) > 0)
  couponLoading.value = false
  // 已选券失效（刷新后不再满足门槛）则清空选择
  if (selectedCouponId.value !== null && !usableCoupons.value.some((c) => c.id === selectedCouponId.value)) {
    selectedCouponId.value = null
  }
}

// 提交订单
const submitOrder = async () => {
  if (!isPickup.value && !currentAddress.value) {
    showToast('请选择收货地址')
    return
  }
  if (cartStore.items.length === 0) {
    showToast('购物车为空')
    return
  }
  submitting.value = true
  try {
    const data = await api.createOrder({
      shopId: cartStore.shopId,
      addressId: isPickup.value ? null : currentAddress.value.id,
      deliveryType: deliveryType.value,
      items: cartStore.items.map((i) => ({ dishId: i.dishId, num: i.num })),
      // 订单要求：备注 / 餐具 / 期望送达(自提)时间（商家端订单卡按 deliveryType 展示对应标签）
      remark: remark.value.trim() || null,
      tableware: tableware.value,
      expectTime: expectTime.value,
      // 幂等键：防止用户重复点击产生重复订单
      idempotentKey: 'web-' + Date.now()
    })
    // B 批：下单成功后写券抵扣记录（券 status→used），再跳订单详情
    if (selectedCoupon.value && discount.value > 0) {
      writeOrderCoupon(data.orderNo, selectedCoupon.value.id, discount.value)
    }
    showToast('下单成功')
    cartStore.clear()
    router.replace('/user/order/' + data.orderNo)
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    addresses.value = (await api.addressList()) || []
    // 默认地址优先，否则取第一条
    currentAddress.value =
      addresses.value.find((a) => a.isDefault === 1) || addresses.value[0] || null
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
  await loadCoupons()
})
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f7f8fa;
  /* 底部 4 Tab 常驻，给「提交订单」按钮多留 50px 让位 */
  padding-bottom: 74px;
}
.submit {
  margin: 24px 16px;
}
.view-orders {
  margin-top: 12px;
}
.total-cell :deep(.van-cell__value) {
  color: #ee0a24;
  font-weight: 600;
}
/* ===== 优惠券选券（B 批） ===== */
.coupon-skeleton {
  padding: 4px 16px;
}
.coupon-empty {
  padding: 14px 16px;
  font-size: 13px;
  color: var(--mt-text-3);
}
.coupon-select-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 10px 16px 14px;
  scrollbar-width: none;
}
.coupon-select-scroll::-webkit-scrollbar {
  display: none;
}
.coupon-none {
  flex-shrink: 0;
  width: 90px;
  min-height: 92px;
  border-radius: 12px;
  border: 1px dashed #c8c9cc;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: border-color 0.15s ease;
}
.coupon-none.on {
  border: 2px solid var(--mt-yellow);
}
.coupon-none__title {
  font-size: 12px;
  color: #333;
  font-weight: 600;
}
.coupon-none__check {
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
.discount-cell :deep(.van-cell__value) {
  color: #b8860b;
  font-weight: 700;
}
.expect-wrap {
  display: flex;
  align-items: center;
  padding: 10px 16px;
}
.expect-label {
  font-size: 14px;
  color: #646566;
  margin-right: 10px;
  flex-shrink: 0;
}
.expect-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.expect-chip {
  padding: 4px 12px;
  border-radius: 14px;
  background: #f2f3f5;
  color: #646566;
  font-size: 12px;
  cursor: pointer;
}
.expect-chip.active {
  background: #ffc300;
  color: #333;
  font-weight: 600;
}
</style>
