<template>
  <!-- 店铺列表页（点外卖首页）：美团风黄色 Hero + 店铺卡片列表 -->
  <div class="page">
    <!-- 顶部 Hero：渐变背景 + 当前地址 + 搜索框 -->
    <div class="hero">
      <div class="hero-inner">
        <div class="hero-addr" @click="goAddresses">
          <van-icon name="location-o" />
          <span class="hero-addr-text">送至：{{ addressText }}</span>
          <van-icon name="arrow" class="hero-addr-arrow" />
        </div>
        <div class="hero-search">
          <van-icon name="search" class="hero-search-icon" />
          <input v-model="keyword" class="hero-search-input" placeholder="搜索店铺名称" />
        </div>
      </div>
    </div>

    <!-- 店铺卡片列表 -->
    <div class="shop-list">
      <div v-for="s in filteredShops" :key="s.id" class="shop-card" @click="goShop(s.id)">
        <div class="shop-thumb" :style="{ background: thumbColor(s.id) }">
          <img v-if="s.image" :src="s.image" class="shop-img" />
          <van-icon v-else name="shop-o" />
        </div>
        <div class="shop-info">
          <div class="shop-name-row">
            <span class="shop-name">{{ s.name }}</span>
            <span class="shop-status" :class="s.status === 1 ? 'open' : 'closed'">
              {{ s.status === 1 ? '营业中' : '休息中' }}
            </span>
          </div>
          <div class="shop-meta">
            <span class="shop-rating"><van-icon name="star" />{{ formatRating(s.rating) }}</span>
            <span>月售 {{ s.monthSales || 0 }}</span>
          </div>
          <div class="shop-fee">
            <span>起送 ¥{{ Number(s.minPrice || 0).toFixed(0) }}</span>
            <span>配送 ¥{{ Number(s.deliveryFee == null ? 5 : s.deliveryFee).toFixed(0) }}</span>
          </div>
          <div class="shop-addr">{{ s.address }}</div>
        </div>
        <div class="shop-right">
          <span class="shop-distance">{{ formatDistance(s.distanceMeters) }}</span>
        </div>
      </div>
    </div>

    <van-empty v-if="shops.length === 0" description="暂无店铺" />
    <van-empty v-else-if="filteredShops.length === 0" description="未找到相关店铺" />

    <UserTabbar />
  </div>
</template>

<script setup>
// 店铺列表页：加载全部店铺，前端按名称关键字过滤（数据接口不变）
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '../../api'
import UserTabbar from '../../components/UserTabbar.vue'

const router = useRouter()
const route = useRoute()
const shops = ref([])
// 门户页搜索跳转带 kw 参数时预填关键字
const keyword = ref((route.query.kw || '').toString())
const addressText = ref('请设置收货地址')

// 按店铺名称前端过滤
const filteredShops = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return shops.value
  return shops.value.filter((s) => s.name && s.name.includes(kw))
})

// 距离格式化：米 → 千米（保留 1 位小数）
const formatDistance = (m) => {
  if (m == null) return ''
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : Math.round(m) + 'm'
}

// 评分兜底展示
const formatRating = (r) => (r == null ? '4.8' : Number(r).toFixed(1))

// 左侧色块：按店铺 id 取固定色板，保证同店颜色稳定
const thumbColors = ['#FFC300', '#FF8A00', '#06AE56', '#4C8DFF', '#FF6B81', '#8B6BFF']
const thumbColor = (id) => thumbColors[Number(id || 0) % thumbColors.length]

// 进入店铺详情页
const goShop = (id) => {
  router.push('/user/shop/' + id)
}

// 跳地址管理
const goAddresses = () => {
  router.push('/user/addresses')
}

// 加载默认收货地址（仅用于地址行展示）
const loadAddress = async () => {
  try {
    const list = (await api.addressList()) || []
    const addr = list.find((a) => a.isDefault === 1) || list[0]
    if (addr && addr.detail) {
      addressText.value = addr.detail.length > 18 ? addr.detail.slice(0, 18) + '…' : addr.detail
    }
  } catch (e) {
    // 静默处理，地址行保持默认文案
  }
}

onMounted(async () => {
  try {
    shops.value = (await api.shopList()) || []
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
  loadAddress()
})
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg);
}

/* ===== 顶部 Hero ===== */
.hero {
  background: var(--mt-gradient);
  border-radius: 0 0 24px 24px;
  padding: 24px 16px 28px;
}
.hero-inner {
  max-width: 720px;
  margin: 0 auto;
}
.hero-addr {
  display: flex;
  align-items: center;
  color: #333;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
.hero-addr-text {
  margin-left: 6px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hero-addr-arrow {
  font-size: 12px;
  opacity: 0.7;
}
.hero-search {
  margin-top: 16px;
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: var(--mt-radius-btn);
  padding: 10px 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.hero-search-icon {
  font-size: 16px;
  color: var(--mt-text-3);
  margin-right: 8px;
}
.hero-search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  color: var(--mt-text);
  background: transparent;
}
.hero-search-input::placeholder {
  color: var(--mt-text-3);
}

/* ===== 店铺卡片 ===== */
.shop-list {
  padding: 12px;
}
.shop-card {
  display: flex;
  background: #fff;
  border-radius: var(--mt-radius);
  padding: 14px;
  margin-bottom: 12px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.shop-card:active {
  opacity: 0.85;
}
.shop-thumb {
  width: 68px;
  height: 68px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28px;
  flex-shrink: 0;
  overflow: hidden;
}
.shop-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.shop-info {
  flex: 1;
  min-width: 0;
  margin-left: 12px;
}
.shop-name-row {
  display: flex;
  align-items: center;
}
.shop-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--mt-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-status {
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
}
.shop-status.open {
  color: var(--mt-success);
  background: rgba(6, 174, 86, 0.1);
}
.shop-status.closed {
  color: var(--mt-text-3);
  background: rgba(153, 153, 153, 0.15);
}
.shop-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mt-text-3);
  display: flex;
  align-items: center;
  gap: 10px;
}
.shop-rating {
  color: var(--mt-yellow);
  font-weight: 600;
}
.shop-rating :deep(.van-icon) {
  font-size: 12px;
  vertical-align: -1px;
}
.shop-fee {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mt-text-2);
  display: flex;
  gap: 12px;
}
.shop-addr {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mt-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-right {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  margin-left: 8px;
}
.shop-distance {
  font-size: 12px;
  color: var(--mt-text-3);
}
</style>
