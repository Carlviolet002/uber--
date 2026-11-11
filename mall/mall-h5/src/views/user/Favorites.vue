<template>
  <!-- 收藏页：已收藏店铺列表（ShopCard 复用，红心取消即时移除） -->
  <div class="favorites-page">
    <van-nav-bar title="我的收藏" left-arrow fixed placeholder @click-left="router.back()" />
    <BaseEmpty
      v-if="!list.length"
      description="还没有收藏的店铺"
      action-text="去发现好店"
      image="star-o"
      @action="router.push('/user/home')"
    />
    <div v-else class="fav-list">
      <ShopCard
        v-for="f in list"
        :key="f.shopId"
        :shop="shopView(f)"
        :marketing="marketing"
        @click="router.push('/user/shop/' + f.shopId)"
      />
    </div>
  </div>
</template>

<script setup>
// 收藏页：快照渲染 + 营销标签按 shopId 补齐；取消收藏后 onActivated 时列表刷新
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import ShopCard from '../../components/user/ShopCard.vue'
import BaseEmpty from '../../components/user/BaseEmpty.vue'
import { getFavorites } from '../../mock/userMock'
import { getMarketingTags } from '../../mock/userMock'

const router = useRouter()

const list = ref([])
const marketing = ref({})

// 快照 → 店铺视图（补 categoryName 兜底）
const shopView = (f) => ({
  id: f.shopId,
  name: f.name,
  image: f.image,
  minPrice: f.minPrice,
  rating: f.rating,
  categoryName: f.categoryName || '美食'
})

const load = async () => {
  list.value = getFavorites()
  const results = await Promise.all(list.value.map(async (f) => [f.shopId, await getMarketingTags(f.shopId)]))
  const map = {}
  results.forEach(([id, data]) => {
    map[id] = data
  })
  marketing.value = map
}

onMounted(load)
// 返回本页时刷新（店铺详情页可能取消收藏）
onActivated(load)
</script>

<style scoped>
.favorites-page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg);
  padding-bottom: 20px;
}
.fav-list {
  padding: 8px;
}
</style>
