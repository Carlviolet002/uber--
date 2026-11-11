<template>
  <!-- 外卖首页：黄顶导航（时间/定位/搜索）+ 金刚区 + 神抢手 + 附近商家/特价外卖列表 -->
  <div class="home">
    <!-- 顶部纯黄导航 -->
    <div class="topbar">
      <div class="topbar-row1">
        <span class="time">{{ nowText }}</span>
        <div class="addr-cluster">
          <span class="locate-btn" :class="{ on: locationStore.ready }" @click="onLocate">
            <van-icon :name="locationStore.ready ? 'location' : 'location-o'" />
            {{ locationStore.ready ? '已定位' : '定位' }}
          </span>
          <div class="addr" @click="showAddr = true">
            <van-icon name="location-o" />
            <span class="addr-name">{{ address.name }}</span>
            <van-icon name="arrow-down" class="addr-arrow" />
          </div>
        </div>
      </div>
      <div class="search-row">
        <div class="search-box" @click="goSearch()">
          <van-icon name="search" class="search-icon" />
          <input v-model="keyword" class="search-input" readonly :placeholder="hotWord" @keyup.enter="goSearch(keyword)" />
        </div>
        <div class="search-btn" @click="goSearch(keyword)">搜索</div>
      </div>
    </div>

    <!-- 金刚区 2x5 -->
    <div v-if="kingkong.length" class="kingkong">
      <div v-for="k in kingkong" :key="k.id" class="kk-item" @click="onKingkong(k)">
        <div class="kk-icon" :style="{ background: 'linear-gradient(135deg,' + k.colors[0] + ',' + k.colors[1] + ')' }">
          {{ k.emoji }}
        </div>
        <div class="kk-name">{{ k.name }}</div>
      </div>
    </div>

    <!-- 神抢手：限时秒杀 -->
    <div class="flash">
      <div class="flash-head">
        <div class="flash-title">
          神抢手
          <span class="flash-tag">限时秒杀中</span>
          <span class="flash-count">{{ countdown }}</span>
        </div>
        <span class="flash-more" @click="flashShow = true">更多 ›</span>
      </div>
      <div class="flash-grid">
        <div
          v-for="f in flashItems"
          :key="f.id"
          class="flash-card"
          :style="{ background: 'linear-gradient(135deg,' + f.colors[0] + ',' + f.colors[1] + ')' }"
          @click="flashShow = true"
        >
          <div class="flash-emoji">{{ f.emoji }}</div>
          <div class="flash-info">
            <div class="flash-name">{{ f.name }}</div>
            <div class="flash-price-row">
              <span class="flash-price">¥{{ f.price }}</span>
              <span class="flash-origin">¥{{ f.originPrice }}</span>
              <span class="flash-guarantee">{{ f.tag }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 附近商家 / 特价外卖 -->
    <div class="shop-section">
      <div class="shop-tabs">
        <div class="shop-tab" :class="{ on: listTab === 'near' }" @click="switchList('near')">附近商家</div>
        <div class="shop-tab" :class="{ on: listTab === 'deal' }" @click="switchList('deal')">
          特价外卖
          <span class="deal-tag">10元点套餐</span>
        </div>
      </div>

      <!-- 特价商品横滑流（特价 tab 专属） -->
      <div v-if="listTab === 'deal'" class="deal-stream">
        <div v-for="p in dealProducts" :key="p.id" class="deal-product" @click="goShop(p.shopId)">
          <div class="deal-product__thumb" :style="{ background: 'linear-gradient(135deg,' + p.colors[0] + ',' + p.colors[1] + ')' }">
            {{ p.emoji }}
          </div>
          <div class="deal-product__name">{{ p.name }}</div>
          <div class="deal-product__price-row">
            <span class="deal-product__price">¥{{ p.price }}</span>
            <span class="deal-product__origin">¥{{ p.originPrice }}</span>
          </div>
          <div class="deal-product__sold">已售 {{ p.sold }} 份</div>
        </div>
      </div>

      <div class="cats">
        <div
          v-for="c in categories"
          :key="c"
          class="cat"
          :class="{ on: activeCat === c }"
          @click="activeCat = c"
        >
          {{ c }}
        </div>
      </div>

      <!-- 商家卡片列表（公共 ShopCard） -->
      <van-skeleton v-if="loading" title :row="3" class="shop-skeleton" />
      <BaseEmpty
        v-else-if="filteredShops.length === 0"
        description="暂无符合条件的店铺"
        action-text="清除筛选"
        @action="clearFilter"
      />
      <div v-else class="shop-list">
        <ShopCard
          v-for="s in filteredShops"
          :key="s.id"
          :shop="s"
          :marketing="marketing"
          :deal-tag="listTab === 'deal'"
          @click="goShop(s.id)"
        />
      </div>
    </div>

    <!-- 地址切换 action-sheet：真实收货地址 + 管理地址入口 -->
    <van-action-sheet
      v-model:show="showAddr"
      :actions="addrActions"
      cancel-text="取消"
      description="切换配送地址"
      @select="onSelectAddr"
    />

    <!-- 神抢手秒杀弹层 -->
    <FlashSalePopup v-model="flashShow" />
  </div>
</template>

<script setup>
// 外卖首页：店铺走真实接口，金刚区/神抢手/营销标签/分类/热词/特价走 userMock；地址走真实接口
import { ref, computed, onMounted, onBeforeUnmount, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import api from '../../api'
import ShopCard from '../../components/user/ShopCard.vue'
import BaseEmpty from '../../components/user/BaseEmpty.vue'
import FlashSalePopup from '../../components/user/FlashSalePopup.vue'
import { useShopList } from '../../composables/useShopList'
import { useLocationStore } from '../../stores/location'
import {
  getCurrentAddress,
  setCurrentAddress,
  getHotWords,
  getKingkong,
  getFlashSale,
  getCategories,
  getDealProducts
} from '../../mock/userMock'

defineOptions({ name: 'UserHome' })

const router = useRouter()
const route = useRoute()
const { shops, marketing, loading, loadShops, filterByCat } = useShopList()
const locationStore = useLocationStore()

const nowText = ref('')
const address = ref(getCurrentAddress())
const showAddr = ref(false)
const addrActions = ref([])
const keyword = ref((route.query.kw || '').toString())
const hotWord = ref('奶茶')
const kingkong = ref([])
const flashItems = ref([])
const categories = ref([])
const dealProducts = ref([])
const activeCat = ref('全部')
const listTab = ref('near')
const flashShow = ref(false)

// 神抢手倒计时：10 分钟周期
const remain = ref(600)
const countdown = computed(() => {
  const m = String(Math.floor(remain.value / 60)).padStart(2, '0')
  const s = String(remain.value % 60).padStart(2, '0')
  return m + ':' + s
})

let clockTimer = null
let wordTimer = null
let loaded = false

// 列表过滤：关键字 + 当前 tab（特价=哈希一半店铺）+ 横向分类
const filteredShops = computed(() => {
  let list = shops.value
  const kw = keyword.value.trim()
  if (kw) list = list.filter((s) => s.name && s.name.includes(kw))
  if (listTab.value === 'deal') {
    list = list.filter((s) => Number(s.id || 0) % 2 === 0)
  }
  return filterByCat(list, activeCat.value)
})

const switchList = (tab) => {
  listTab.value = tab
}

const goShop = (id) => router.push('/user/shop/' + id)

// 定位：获取经纬度后按距离重排店铺（失败则提示，沿用默认顺序）
const onLocate = async () => {
  const ok = await locationStore.locateNow()
  if (ok) {
    showToast('已获取定位，按距离推荐')
    loadShops()
  } else {
    showToast('定位失败，使用默认顺序')
  }
}

// 金刚区：10 个入口全部跳通用频道页；「更多」进搜索页
const onKingkong = (k) => {
  if (k.name === '更多') {
    router.push('/user/search')
  } else {
    router.push('/user/channel/' + encodeURIComponent(k.name))
  }
}

// 搜索：点击框跳搜索页；带关键词则拼 query
const goSearch = (kw) => {
  const k = (kw || '').toString().trim()
  router.push(k ? '/user/search?kw=' + encodeURIComponent(k) : '/user/search')
}

// 清空筛选：还原分类与关键字
const clearFilter = () => {
  activeCat.value = '全部'
  keyword.value = ''
}

const onSelectAddr = (action) => {
  if (action.option === '__manage__') {
    router.push('/user/addresses')
    return
  }
  setCurrentAddress(action.option)
  address.value = action.option
  showToast('已切换至 ' + action.option.name)
}

// 地址来源：真实收货地址接口，失败时回退 userMock 兜底
const loadAddresses = async () => {
  try {
    const list = (await api.addressList()) || []
    if (list.length) {
      addrActions.value = list.map((a) => ({
        name: a.receiverName + ' ' + a.detail,
        option: { id: a.id, name: a.receiverName, detail: a.detail }
      }))
      // 当前送餐地址若不在真实地址中，回退第一条
      const cur = getCurrentAddress()
      const match = list.find((a) => Number(a.id) === Number(cur.id))
      if (!match) {
        setCurrentAddress({ id: list[0].id, name: list[0].receiverName, detail: list[0].detail })
        address.value = getCurrentAddress()
      }
    }
  } catch (e) {
    // 接口失败：userMock 兜底
    const { getAddressOptions } = await import('../../mock/userMock')
    const opts = await getAddressOptions()
    addrActions.value = opts.map((a) => ({ name: a.name + ' · ' + a.detail, option: a }))
  }
  addrActions.value.push({ name: '📇 管理收货地址', option: '__manage__' })
}

onMounted(async () => {
  // 时钟与倒计时
  const tick = () => {
    const d = new Date()
    nowText.value =
      String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
  }
  tick()
  clockTimer = setInterval(tick, 1000)
  const tickCount = () => {
    remain.value = remain.value > 0 ? remain.value - 1 : 600
  }
  setInterval(tickCount, 1000)

  // 数据加载（仅首次）
  if (!loaded) {
    loaded = true
    if (route.query.tab === 'deal') {
      listTab.value = 'deal'
    }
    loadShops()
    getKingkong().then((d) => (kingkong.value = d))
    getFlashSale().then((d) => (flashItems.value = d))
    getCategories().then((d) => (categories.value = d))
    getDealProducts().then((d) => (dealProducts.value = d))
    loadAddresses()
    getHotWords().then((words) => {
      // 热词轮播：每 3 秒换一个占位词
      let i = 0
      hotWord.value = words[0] || '奶茶'
      wordTimer = setInterval(() => {
        i = (i + 1) % words.length
        hotWord.value = words[i]
      }, 3000)
    })
  }
})

// keep-alive 激活时轻刷新：地址联动（地址管理页可能修改）
onActivated(() => {
  if (loaded) {
    address.value = getCurrentAddress()
    loadAddresses()
  }
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (wordTimer) clearInterval(wordTimer)
})
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: var(--mt-bg);
}

/* ===== 顶部纯黄导航 ===== */
.topbar {
  background: var(--mt-gradient);
  padding: 18px 16px 20px;
  border-radius: 0 0 20px 20px;
}
.topbar-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.time {
  font-size: 15px;
  font-weight: 700;
  color: #333;
}
.addr {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #333;
  font-size: 15px;
  font-weight: 600;
  max-width: 60%;
}
.addr-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.addr-arrow {
  font-size: 11px;
}
.addr-cluster {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 70%;
}
.locate-btn {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 600;
  color: #333;
  background: rgba(255, 255, 255, 0.55);
  border-radius: 14px;
  padding: 4px 10px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}
.locate-btn.on {
  background: #fff;
  color: #b8860b;
}
.search-row {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 22px;
  padding: 9px 14px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
}
.search-icon {
  color: var(--mt-text-3);
  margin-right: 6px;
}
.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  color: var(--mt-text);
  background: transparent;
}
.search-input::placeholder {
  color: var(--mt-text-3);
}
.search-btn {
  background: #ffd100;
  color: #333;
  font-size: 14px;
  font-weight: 600;
  border-radius: 22px;
  padding: 9px 18px;
  cursor: pointer;
}

/* ===== 金刚区 ===== */
.kingkong {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px 8px;
  background: #fff;
  margin: -6px 8px 0;
  border-radius: var(--mt-radius);
  padding: 16px 8px;
  position: relative;
  z-index: 1;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.kk-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.kk-icon {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: inset 0 -4px 6px rgba(0, 0, 0, 0.12), 0 3px 6px rgba(0, 0, 0, 0.12);
}
.kk-name {
  font-size: 11px;
  color: #333;
}

/* ===== 神抢手 ===== */
.flash {
  margin: 10px 8px 0;
  background: #fff;
  border-radius: var(--mt-radius);
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.flash-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.flash-title {
  font-size: 16px;
  font-weight: 700;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}
.flash-tag {
  font-size: 10px;
  color: #fff;
  background: #ff5000;
  border-radius: 10px;
  padding: 2px 8px;
  font-weight: 600;
}
.flash-count {
  font-size: 12px;
  color: #ff5000;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.flash-more {
  font-size: 12px;
  color: var(--mt-text-3);
  cursor: pointer;
}
.flash-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.flash-card {
  border-radius: 12px;
  padding: 12px;
  position: relative;
  cursor: pointer;
  min-height: 92px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}
.flash-emoji {
  position: absolute;
  right: 10px;
  top: 10px;
  font-size: 34px;
  opacity: 0.95;
}
.flash-info {
  position: relative;
  z-index: 1;
  max-width: 70%;
}
.flash-name {
  font-size: 12px;
  font-weight: 600;
  color: #333;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.flash-price-row {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 4px;
  flex-wrap: wrap;
}
.flash-price {
  color: #ff5000;
  font-size: 17px;
  font-weight: 700;
}
.flash-origin {
  color: rgba(51, 51, 51, 0.45);
  font-size: 11px;
  text-decoration: line-through;
}
.flash-guarantee {
  font-size: 9px;
  color: #ff5000;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 4px;
  padding: 1px 5px;
}

/* ===== 商家区 ===== */
.shop-section {
  margin-top: 10px;
}
.shop-tabs {
  display: flex;
  background: #fff;
  padding: 12px 16px 0;
  gap: 24px;
}
.shop-tab {
  font-size: 16px;
  font-weight: 700;
  color: #333;
  padding-bottom: 10px;
  border-bottom: 3px solid transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}
.shop-tab.on {
  border-bottom-color: var(--mt-yellow);
}
.deal-tag {
  font-size: 10px;
  color: #ff5000;
  border: 1px solid #ff5000;
  border-radius: 4px;
  padding: 0 4px;
  font-weight: 600;
}

/* ===== 特价商品横滑流 ===== */
.deal-stream {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  background: #fff;
  padding: 10px 16px;
  scrollbar-width: none;
}
.deal-stream::-webkit-scrollbar {
  display: none;
}
.deal-product {
  flex-shrink: 0;
  width: 110px;
  cursor: pointer;
}
.deal-product__thumb {
  width: 110px;
  height: 110px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 42px;
}
.deal-product__name {
  margin-top: 6px;
  font-size: 11px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.deal-product__price-row {
  margin-top: 2px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.deal-product__price {
  font-size: 15px;
  font-weight: 800;
  color: #ff5000;
}
.deal-product__origin {
  font-size: 10px;
  color: var(--mt-text-3);
  text-decoration: line-through;
}
.deal-product__sold {
  font-size: 10px;
  color: var(--mt-text-3);
}

.cats {
  display: flex;
  overflow-x: auto;
  background: #fff;
  padding: 10px 16px;
  gap: 12px;
  scrollbar-width: none;
}
.cats::-webkit-scrollbar {
  display: none;
}
.cat {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--mt-text-2);
  background: var(--mt-bg);
  border-radius: 14px;
  padding: 5px 12px;
  cursor: pointer;
}
.cat.on {
  background: rgba(255, 195, 0, 0.18);
  color: #b8860b;
  font-weight: 600;
}
.shop-skeleton {
  margin: 12px;
  background: #fff;
  border-radius: var(--mt-radius);
  padding: 16px;
}
.shop-list {
  padding: 0 12px;
}
</style>
