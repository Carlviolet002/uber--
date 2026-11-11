<template>
  <!-- 商家卡片（公共组件）：首页/频道/收藏/搜索四处复用，样式与 P0 首页卡片一致 -->
  <div class="shop-card" @click="emit('click', shop)">
    <div class="shop-thumb" :style="{ background: thumbColor(shop.id) }">
      <img v-if="shop.image" :src="shop.image" class="shop-img" />
      <van-icon v-else name="shop-o" class="shop-thumb-icon" />
      <span v-if="dealTag" class="shop-deal-tag">10元点套餐</span>
    </div>
    <div class="shop-info">
      <div class="shop-name-row">
        <span class="shop-name">{{ shop.name }}</span>
        <span class="shop-cat">{{ shop.categoryName || '美食' }}</span>
      </div>
      <div class="shop-fee">
        <span>起送 ¥{{ Number(shop.minPrice || 0).toFixed(0) }}</span>
        <span>
          配送
          <em v-if="mkt && mkt.fee.delivery === 0" class="free">免配送费</em>
          <em v-else>¥{{ mkt ? mkt.fee.delivery : Number(shop.deliveryFee == null ? 5 : shop.deliveryFee).toFixed(0) }}</em>
        </span>
        <span class="shop-dist">{{ realDistance || (mkt ? mkt.fee.distanceKm + 'km ' + mkt.fee.minutes + '分钟' : '') }}</span>
      </div>
      <div class="shop-rating-row">
        <span class="shop-rating"><van-icon name="star" />{{ formatRating(shop.rating) }}分</span>
        <span v-if="hasTag('rating')" class="shop-rating-tag">大众点评高分店铺</span>
      </div>
      <div v-if="hasTag('coupon')" class="shop-coupon-tag">神券 满28减13</div>
      <div v-if="mkt" class="shop-deal">
        <span class="deal-emoji">{{ mkt.deal.emoji }}</span>
        <span class="deal-name">{{ mkt.deal.name }}</span>
        <span class="deal-save">{{ mkt.deal.save }}</span>
      </div>
    </div>
    <FavoriteBtn v-if="showFavorite" :shop-id="shop.id" :shop="shop" class="shop-fav" />
  </div>
</template>

<script setup>
// 商家卡片公共组件：营销数据由父组件传入（marketing[shopId]），缩略图渐变按 shopId 稳定
import { computed } from 'vue'
import FavoriteBtn from './FavoriteBtn.vue'

const props = defineProps({
  shop: { type: Object, required: true },
  marketing: { type: Object, default: null },
  dealTag: { type: Boolean, default: false },
  showFavorite: { type: Boolean, default: true }
})

const emit = defineEmits(['click'])

const mkt = computed(() => (props.marketing ? props.marketing[props.shop.id] || null : null))

// 真实距离优先：后端传经纬度时回算 distanceMeters，无则回退 mock 距离
const realDistance = computed(() => {
  const d = props.shop.distanceMeters
  if (d == null) return null
  return d < 1000 ? Math.round(d) + 'm' : (d / 1000).toFixed(1) + 'km'
})

const thumbColors = ['#FFC300', '#FF8A00', '#06AE56', '#4C8DFF', '#FF6B81', '#8B6BFF']
const thumbColor = (id) => thumbColors[Number(id || 0) % thumbColors.length]
const formatRating = (r) => (r == null ? '4.8' : Number(r).toFixed(1))
const hasTag = (type) => mkt.value && mkt.value.tags.some((t) => t.type === type)
</script>

<script>
// 组件名注册：keep-alive include 匹配用
export default { name: 'ShopCard' }
</script>

<style scoped>
.shop-card {
  display: flex;
  background: var(--mt-card-bg);
  border-radius: var(--mt-radius);
  padding: 14px;
  margin-bottom: 12px;
  cursor: pointer;
  box-shadow: var(--mt-shadow);
  position: relative;
}
.shop-card:active {
  opacity: 0.85;
}
.shop-thumb {
  width: 100px;
  height: 100px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 40px;
  flex-shrink: 0;
  overflow: hidden;
  position: relative;
}
.shop-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.shop-thumb-icon {
  opacity: 0.9;
}
/* 特价 tab：店铺图左上角红色角标 */
.shop-deal-tag {
  position: absolute;
  left: 0;
  top: 0;
  font-size: 9px;
  color: #fff;
  background: #ff5000;
  border-radius: 12px 0 8px 0;
  padding: 2px 6px;
  font-weight: 600;
}
.shop-info {
  flex: 1;
  min-width: 0;
  margin-left: 12px;
}
.shop-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.shop-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-cat {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--mt-text-3);
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  padding: 1px 5px;
}
.shop-fee {
  margin-top: 7px;
  font-size: 11px;
  color: var(--mt-text-3);
  display: flex;
  gap: 10px;
  align-items: center;
}
.shop-fee em {
  font-style: normal;
}
.shop-fee .free {
  color: var(--mt-success);
  font-weight: 600;
}
.shop-dist {
  margin-left: auto;
}
.shop-rating-row {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.shop-rating {
  font-size: 12px;
  color: #ff9a14;
  font-weight: 700;
}
.shop-rating :deep(.van-icon) {
  font-size: 11px;
}
.shop-rating-tag {
  font-size: 10px;
  color: #b8860b;
  background: rgba(255, 195, 0, 0.15);
  border-radius: 4px;
  padding: 1px 5px;
}
.shop-coupon-tag {
  margin-top: 6px;
  display: inline-block;
  font-size: 10px;
  color: #ff5000;
  background: rgba(255, 80, 0, 0.08);
  border-radius: 4px;
  padding: 1px 6px;
}
.shop-deal {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--mt-text-2);
}
.deal-emoji {
  font-size: 15px;
}
.deal-save {
  color: #ff5000;
  font-weight: 600;
}
/* 收藏心形：右上角悬浮 */
.shop-fav {
  position: absolute;
  right: 12px;
  top: 12px;
  z-index: 2;
}
</style>
