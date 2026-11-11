<template>
  <!-- 店铺详情页：按分类分组展示菜品，stepper 加购 -->
  <div class="page">
    <van-nav-bar :title="shop.name || '店铺详情'" left-arrow @click-left="router.back()">
      <template #right>
        <FavoriteBtn :shop-id="shopId" :shop="shop" />
      </template>
    </van-nav-bar>
    <!-- 神券领取条 -->
    <div class="coupon-bar" @click="onClaimCoupon">
      <span class="coupon-emoji">🎟️</span>
      <span class="coupon-text">神券 ¥13 满28减13</span>
      <span class="coupon-btn">{{ couponClaimed ? '已领取' : '立即领取' }}</span>
    </div>
    <!-- 店铺信息 -->
    <div class="shop-info">
      <div class="shop-name">
        {{ shop.name }}
        <van-tag :type="shop.status === 1 ? 'success' : 'default'" class="shop-tag">
          {{ shop.status === 1 ? '营业中' : '休息中' }}
        </van-tag>
      </div>
      <div class="shop-address">{{ shop.address }}</div>
    </div>
    <!-- 菜品分组 -->
    <van-cell-group v-for="c in grouped" :key="c.id" :title="c.name" class="dish-group">
      <div v-for="d in c.dishes" :key="d.id" class="dish-row">
        <div class="dish-info">
          <div class="dish-name">{{ d.name }}</div>
          <div class="dish-price">¥{{ Number(d.price).toFixed(2) }}</div>
        </div>
        <van-stepper
          :model-value="qtyMap[d.id] || 0"
          min="0"
          integer
          @change="onChange(d, $event)"
        />
      </div>
    </van-cell-group>
    <!-- 底部占位，避免被 action-bar 遮挡 -->
    <div class="bottom-placeholder"></div>
    <!-- 底部购物车栏 -->
    <!-- tabbar 常驻后购物车栏上移 50px，避免与底部 4 Tab 重叠遮挡去结算/购物车 -->
    <van-action-bar :style="{ bottom: '50px' }">
      <van-action-bar-icon icon="cart-o" :badge="cartStore.totalCount" text="购物车" @click="router.push('/user/cart')" />
      <van-action-bar-button
        type="danger"
        :text="'去结算 ¥' + cartStore.totalPrice.toFixed(2)"
        :disabled="cartStore.totalCount === 0"
        @click="router.push('/user/cart')"
      />
    </van-action-bar>
  </div>
</template>

<script setup>
// 店铺详情页：加载店铺信息 / 分类 / 菜品，按 categoryId 分组，stepper 加购（不同店铺清空重来）
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import api from '../../api'
import { useCartStore } from '../../stores/cart'
import { claimCoupon, getMyCoupons } from '../../mock/userMock'
import FavoriteBtn from '../../components/user/FavoriteBtn.vue'

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()

const shopId = computed(() => route.params.id)
const shop = ref({})
const categories = ref([])
const dishes = ref([])
const couponClaimed = ref(false)

// 领取店铺神券（id=104），状态与神券频道共享
const onClaimCoupon = () => {
  if (couponClaimed.value) {
    showToast('该神券已领取，下单自动抵扣')
    return
  }
  if (claimCoupon(104)) {
    couponClaimed.value = true
    showToast('神券已领取，可在「神券」频道查看')
  } else {
    couponClaimed.value = true
    showToast('神券已领取过啦')
  }
}

// 购物车中每个菜品的当前数量映射：{dishId: num}
const qtyMap = computed(() => {
  const m = {}
  cartStore.items.forEach((i) => {
    m[i.dishId] = i.num
  })
  return m
})

// 按分类分组菜品（仅展示上架 status===1 的菜品；无分类的归入「其他」）
const grouped = computed(() => {
  const groups = categories.value.map((c) => ({
    ...c,
    dishes: dishes.value.filter((d) => d.categoryId === c.id)
  }))
  const categorized = new Set()
  groups.forEach((g) => g.dishes.forEach((d) => categorized.add(d.id)))
  const others = dishes.value.filter((d) => !categorized.has(d.id))
  if (others.length) {
    groups.push({ id: -1, name: '其他', dishes: others })
  }
  return groups
})

// stepper 数量变化：加购 / 减购 / 移除
const onChange = (dish, val) => {
  const exist = cartStore.items.find((i) => i.dishId === dish.id)
  if (val <= 0) {
    const idx = cartStore.items.findIndex((i) => i.dishId === dish.id)
    if (idx > -1) cartStore.remove(idx)
    return
  }
  if (exist) {
    // 已存在则直接更新数量
    cartStore.setNum(dish.id, val)
  } else {
    // 新加购（add 内部会校验店铺，不同店铺自动清空重来）
    cartStore.add({
      dishId: dish.id,
      shopId: Number(shopId.value),
      name: dish.name,
      price: dish.price,
      image: dish.image || '',
      num: val
    })
  }
}

const loadShop = async () => {
  shop.value = {}
  dishes.value = []
  categories.value = []
  try {
    shop.value = (await api.shopDetail(shopId.value)) || {}
    categories.value = (await api.categories(shopId.value)) || []
    dishes.value = ((await api.dishes(shopId.value)) || []).filter((d) => d.status === 1)
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
  // 券领取状态与神券频道同步
  const myCoupons = await getMyCoupons()
  couponClaimed.value = myCoupons.some((c) => c.id === 104)
}

watch(shopId, () => {
  if (shopId.value) loadShop()
})

onMounted(loadShop)
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f7f8fa;
}
.shop-info {
  background: #fff;
  padding: 16px;
  margin-bottom: 8px;
}
/* 神券领取条 */
.coupon-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--mt-gradient);
  margin: 8px 12px;
  border-radius: 12px;
  padding: 10px 14px;
  cursor: pointer;
}
.coupon-emoji {
  font-size: 18px;
}
.coupon-text {
  flex: 1;
  font-size: 13px;
  font-weight: 700;
  color: #333;
}
.coupon-btn {
  font-size: 12px;
  font-weight: 600;
  color: #333;
  background: #fff;
  border-radius: 14px;
  padding: 4px 12px;
}
.shop-name {
  font-size: 18px;
  font-weight: 600;
}
.shop-tag {
  margin-left: 8px;
  vertical-align: middle;
}
.shop-address {
  margin-top: 6px;
  color: #969799;
  font-size: 13px;
}
.dish-group {
  margin-bottom: 8px;
}
.dish-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
}
.dish-info {
  flex: 1;
  padding-right: 12px;
}
.dish-name {
  font-size: 15px;
}
.dish-price {
  margin-top: 4px;
  color: #ee0a24;
  font-weight: 600;
}
/* 购物车栏上移 50px 后，占位同步加 50px，避免菜品列表末尾被两层底栏遮挡 */
.bottom-placeholder {
  height: 110px;
}
</style>
