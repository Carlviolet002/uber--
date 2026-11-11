<template>
  <!-- 通用频道页：金刚区 10 个入口统一落地（专属横幅 3 个 + 通用模板），商家列表复用 ShopCard -->
  <div class="channel-page">
    <van-nav-bar :title="channelName" left-arrow fixed placeholder @click-left="router.back()" />
    <!-- 专属横幅 -->
    <div v-if="config" class="channel-banner" :style="{ background: 'linear-gradient(135deg,' + config.banner.colors[0] + ',' + config.banner.colors[1] + ')' }">
      <div class="channel-banner__text">
        <div class="channel-banner__title">{{ config.banner.title }}</div>
        <div class="channel-banner__sub">{{ config.banner.sub }}</div>
      </div>
      <div class="channel-banner__emoji">{{ config.banner.emoji }}</div>
    </div>
    <!-- 频道专属分类条 -->
    <div v-if="config" class="channel-cats">
      <div
        v-for="c in config.cats"
        :key="c"
        class="channel-cat"
        :class="{ on: activeCat === c }"
        @click="activeCat = c"
      >
        {{ c }}
      </div>
    </div>
    <!-- 商家列表 -->
    <van-skeleton v-if="loading" title :row="3" class="channel-skeleton" />
    <BaseEmpty
      v-else-if="filtered.length === 0"
      description="该分类下暂无店铺"
      action-text="查看全部"
      @action="activeCat = '全部'"
    />
    <div v-else class="channel-list">
      <ShopCard
        v-for="s in filtered"
        :key="s.id"
        :shop="s"
        :marketing="marketing"
        @click="router.push('/user/shop/' + s.id)"
      />
    </div>
  </div>
</template>

<script setup>
// 通用频道页：channelName 走 computed + watch（组件复用铁律），配置/营销标签走 userMock
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import ShopCard from '../../components/user/ShopCard.vue'
import BaseEmpty from '../../components/user/BaseEmpty.vue'
import { useShopList } from '../../composables/useShopList'
import { getChannelConfig } from '../../mock/userMock'

const router = useRouter()
const route = useRoute()
const { shops, marketing, loading, loadShops, filterByCat } = useShopList()

const channelName = computed(() => route.params.name || '')
const config = ref(null)
const activeCat = ref('全部')

const filtered = computed(() => filterByCat(shops.value, activeCat.value))

// 频道切换：重载配置 + 分类复位
const load = async () => {
  activeCat.value = '全部'
  config.value = await getChannelConfig(channelName.value)
}

watch(channelName, () => {
  if (channelName.value) load()
})

onMounted(async () => {
  await loadShops()
  await load()
})
</script>

<style scoped>
.channel-page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg);
  padding-bottom: 20px;
}
.channel-banner {
  margin: 8px;
  border-radius: var(--mt-radius);
  padding: 20px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.06);
}
.channel-banner__title {
  font-size: 20px;
  font-weight: 800;
  color: #333;
}
.channel-banner__sub {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(51, 51, 51, 0.65);
}
.channel-banner__emoji {
  font-size: 52px;
}
.channel-cats {
  display: flex;
  overflow-x: auto;
  background: #fff;
  margin: 0 8px;
  border-radius: var(--mt-radius);
  padding: 12px 14px;
  gap: 12px;
  scrollbar-width: none;
}
.channel-cats::-webkit-scrollbar {
  display: none;
}
.channel-cat {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--mt-text-2);
  background: var(--mt-bg);
  border-radius: 14px;
  padding: 6px 14px;
  cursor: pointer;
}
.channel-cat.on {
  background: rgba(255, 195, 0, 0.18);
  color: #b8860b;
  font-weight: 600;
}
.channel-skeleton {
  margin: 12px 8px;
  background: #fff;
  border-radius: var(--mt-radius);
  padding: 16px;
}
.channel-list {
  padding: 10px 8px 0;
}
</style>
