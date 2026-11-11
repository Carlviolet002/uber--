<template>
  <!-- 搜索页：历史 + 热搜榜 → 联想 → 结果（店铺分区 + 菜品分区） -->
  <div class="search-page">
    <!-- 顶部搜索栏 -->
    <div class="search-top">
      <van-icon name="arrow-left" class="search-back" @click="router.back()" />
      <div class="search-input-wrap">
        <van-icon name="search" class="search-input-icon" />
        <input
          ref="inputRef"
          v-model="kw"
          class="search-input"
          placeholder="搜索商家、菜品"
          @input="onInput"
          @keyup.enter="doSearch(kw)"
        />
        <van-icon v-if="kw" name="clear" class="search-clear" @click="onClear" />
      </div>
      <span class="search-cancel" @click="doSearch(kw)">搜索</span>
    </div>

    <!-- 未输入态：历史搜索 + 热搜榜 -->
    <div v-if="!kw && !resultMode" class="search-panel">
      <div v-if="history.length" class="panel-block">
        <div class="panel-head">
          <span class="panel-title">历史搜索</span>
          <van-icon name="delete-o" class="panel-clear" @click="onClearHistory" />
        </div>
        <div class="history-tags">
          <span v-for="h in history" :key="h" class="history-tag" @click="doSearch(h)">{{ h }}</span>
        </div>
      </div>
      <div class="panel-block">
        <div class="panel-head">
          <span class="panel-title">热搜榜</span>
          <span class="panel-sub">实时更新</span>
        </div>
        <div v-for="(h, i) in hotList" :key="h.kw" class="hot-item" @click="doSearch(h.kw)">
          <span class="hot-rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <span class="hot-kw">{{ h.kw }}</span>
          <span class="hot-val">{{ h.hot }}</span>
        </div>
      </div>
    </div>

    <!-- 输入态：联想列表 -->
    <div v-else-if="kw && !resultMode" class="search-panel">
      <div v-for="s in suggests" :key="s" class="suggest-item" @click="doSearch(s)">
        <van-icon name="search" class="suggest-icon" />
        <span class="suggest-text">{{ s }}</span>
        <van-icon name="arrow" class="suggest-arrow" />
      </div>
      <div v-if="suggestLoading" class="suggest-loading">
        <van-loading size="18" /> 联想加载中...
      </div>
    </div>

    <!-- 结果态 -->
    <div v-else class="search-result">
      <van-skeleton v-if="searching" title :row="3" class="result-skeleton" />
      <template v-else>
        <!-- 空结果引导 -->
        <BaseEmpty
          v-if="!shopResults.length && !dishResults.length"
          description="没有找到相关结果，换个关键词试试"
          image="search"
        />
        <template v-else>
          <!-- 店铺分区 -->
          <div v-if="shopResults.length" class="result-block">
            <div class="result-title">相关店铺</div>
            <ShopCard
              v-for="s in shopResults"
              :key="s.id"
              :shop="s"
              :marketing="marketing"
              @click="router.push('/user/shop/' + s.id)"
            />
          </div>
          <!-- 菜品分区 -->
          <div v-if="dishResults.length" class="result-block">
            <div class="result-title">相关菜品</div>
            <div v-for="d in dishResults" :key="d.id" class="dish-item" @click="router.push('/user/shop/' + d.shopId)">
              <div class="dish-thumb">
                <img v-if="d.image" :src="d.image" class="dish-img" :alt="d.name" />
                <span v-else class="dish-emoji">{{ d.emoji || '🍽️' }}</span>
              </div>
              <div class="dish-info">
                <div class="dish-name">{{ d.name }}</div>
                <div class="dish-shop">
                  {{ d.shopName }}<span v-if="d.soldOut" class="dish-soldout"> · 已售罄</span>
                </div>
              </div>
              <div class="dish-price">¥{{ d.price }}</div>
            </div>
          </div>
          <!-- 热门推荐（结果少时兜底） -->
          <div v-if="dishResults.length < 2" class="result-block">
            <div class="result-title">大家都在搜</div>
            <div class="history-tags">
              <span v-for="h in hotList.slice(0, 4)" :key="h.kw" class="history-tag" @click="doSearch(h.kw)">{{ h.kw }}</span>
            </div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
// 搜索页：历史/热搜/联想走 userMock；店铺结果走真实 shopList 过滤；菜品结果走 userMock 聚合
import { ref, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showDialog } from 'vant'
import api from '../../api'
import ShopCard from '../../components/user/ShopCard.vue'
import BaseEmpty from '../../components/user/BaseEmpty.vue'
import { useDebounce } from '../../composables/useDebounce'
import { useShopList } from '../../composables/useShopList'
import {
  getSearchHistory,
  addSearchHistory,
  clearSearchHistory,
  getHotSearch,
  getSuggest,
  getSearchDishes
} from '../../mock/userMock'

const router = useRouter()
const route = useRoute()
const { shops, marketing, loadShops } = useShopList()

const kw = ref((route.query.kw || '').toString())
const history = ref([])
const hotList = ref([])
const suggests = ref([])
const suggestLoading = ref(false)
const shopResults = ref([])
const dishResults = ref([])
const resultMode = ref(false)
const searching = ref(false)
const inputRef = ref(null)

// 联想输入：300ms 防抖
const { debounced: debouncedSuggest } = useDebounce(async (k) => {
  if (!k.trim() || resultMode.value) return
  suggestLoading.value = true
  suggests.value = await getSuggest(k)
  suggestLoading.value = false
})

const onInput = () => {
  resultMode.value = false
  if (!kw.value.trim()) {
    suggests.value = []
    return
  }
  debouncedSuggest(kw.value)
}

const onClear = () => {
  kw.value = ''
  suggests.value = []
  resultMode.value = false
  shopResults.value = []
  dishResults.value = []
  nextTick(() => inputRef.value && inputRef.value.focus())
}

// 菜品搜索接真：对前 K 家店并发拉取 dishes，本地按名称匹配；无结果/全失败时回退 mock
const searchDishes = async (key) => {
  const list = shops.value || []
  if (!list.length) return await getSearchDishes(key)
  const K = 8
  const top = list.slice(0, K)
  const nameMap = {}
  list.forEach((s) => {
    nameMap[s.id] = s.name
  })
  const settled = await Promise.allSettled(top.map((s) => api.dishes(s.id)))
  const found = []
  for (const r of settled) {
    if (r.status !== 'fulfilled' || !Array.isArray(r.value)) continue
    for (const d of r.value) {
      if (d.name && d.name.includes(key)) {
        found.push({
          id: d.id,
          name: d.name,
          price: Number(d.price),
          image: d.image,
          shopId: d.shopId,
          shopName: nameMap[d.shopId] || '',
          soldOut: d.soldOut
        })
      }
    }
  }
  return found.length ? found : await getSearchDishes(key)
}

// 执行搜索：记录历史 + 双分区结果
const doSearch = async (k) => {
  const key = (k || kw.value).trim()
  if (!key) return
  kw.value = key
  resultMode.value = true
  searching.value = true
  addSearchHistory(key)
  history.value = getSearchHistory()
  shopResults.value = shops.value.filter(
    (s) => (s.name && s.name.includes(key)) || (s.categoryName && s.categoryName.includes(key))
  )
  dishResults.value = await searchDishes(key)
  searching.value = false
}

const onClearHistory = () => {
  showDialog({
    title: '清空搜索历史',
    message: '确定清空全部历史搜索记录吗？',
    showCancelButton: true,
    confirmButtonColor: '#FFC300'
  }).then(() => {
    clearSearchHistory()
    history.value = []
  })
}

onMounted(async () => {
  history.value = getSearchHistory()
  hotList.value = await getHotSearch()
  await loadShops()
  nextTick(() => inputRef.value && inputRef.value.focus())
  // 带 kw 进入时直接出结果
  if (kw.value.trim()) {
    doSearch(kw.value)
  }
})
</script>

<style scoped>
.search-page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg);
}
/* ===== 顶部搜索栏 ===== */
.search-top {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--mt-gradient);
  padding: 12px 12px 14px;
}
.search-back {
  font-size: 20px;
  color: #333;
  padding: 4px;
}
.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 20px;
  padding: 8px 12px;
  gap: 6px;
}
.search-input-icon {
  color: var(--mt-text-3);
}
.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  color: var(--mt-text);
  background: transparent;
}
.search-clear {
  color: var(--mt-text-3);
  font-size: 14px;
}
.search-cancel {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  cursor: pointer;
}
/* ===== 面板通用 ===== */
.search-panel {
  background: #fff;
  margin: 8px;
  border-radius: var(--mt-radius);
  padding: 14px 16px;
}
.panel-block {
  margin-bottom: 14px;
}
.panel-block:last-child {
  margin-bottom: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #333;
}
.panel-sub {
  font-size: 11px;
  color: var(--mt-text-3);
}
.panel-clear {
  color: var(--mt-text-3);
  font-size: 16px;
}
.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.history-tag {
  font-size: 12px;
  color: var(--mt-text-2);
  background: var(--mt-bg);
  border-radius: 14px;
  padding: 5px 12px;
  cursor: pointer;
}
/* ===== 热搜榜 ===== */
.hot-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  cursor: pointer;
}
.hot-rank {
  width: 18px;
  text-align: center;
  font-size: 13px;
  font-weight: 800;
  color: var(--mt-text-3);
  font-style: italic;
}
.hot-rank.top {
  color: #ff5000;
}
.hot-kw {
  flex: 1;
  font-size: 14px;
  color: #333;
}
.hot-val {
  font-size: 11px;
  color: var(--mt-text-3);
}
/* ===== 联想 ===== */
.suggest-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.suggest-item:last-child {
  border-bottom: none;
}
.suggest-icon {
  color: var(--mt-text-3);
}
.suggest-text {
  flex: 1;
  font-size: 14px;
  color: #333;
}
.suggest-arrow {
  color: #ccc;
  font-size: 12px;
}
.suggest-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  font-size: 12px;
  color: var(--mt-text-3);
}
/* ===== 结果 ===== */
.search-result {
  padding: 0 8px 20px;
}
.result-skeleton {
  margin-top: 12px;
  background: #fff;
  border-radius: var(--mt-radius);
  padding: 16px;
}
.result-block {
  margin-top: 10px;
}
.result-title {
  font-size: 14px;
  font-weight: 700;
  color: #333;
  padding: 8px 4px;
}
.dish-item {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: var(--mt-radius);
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
}
.dish-emoji {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #fff7e6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.dish-thumb {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  background: #fff7e6;
}
.dish-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.dish-soldout {
  color: var(--mt-text-3);
  font-size: 11px;
}
.dish-info {
  flex: 1;
  min-width: 0;
}
.dish-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}
.dish-shop {
  margin-top: 3px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.dish-price {
  font-size: 15px;
  font-weight: 800;
  color: #ff5000;
}
</style>
