// 商家列表通用逻辑：真实店铺数据 + userMock 营销标签，供首页/频道页/收藏页/搜索页复用
import { ref } from 'vue'
import api from '../api'
import { getMarketingTags, shopInCategory } from '../mock/userMock'
import { useLocationStore } from '../stores/location'

export function useShopList() {
  const shops = ref([])
  const marketing = ref({})
  const loading = ref(true)
  const locationStore = useLocationStore()

  const loadShops = async () => {
    loading.value = true
    try {
      // 已定位则把经纬度传给后端，由后端回算距离并按距离排序
      const params = locationStore.hasCoords()
        ? { lng: locationStore.lng, lat: locationStore.lat }
        : {}
      shops.value = (await api.shopList(params)) || []
    } catch (e) {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
    loadMarketing()
  }

  // 营销标签并行加载（mock 延时 300-500ms/店，Promise.all 并发）
  const loadMarketing = async () => {
    const list = shops.value
    const results = await Promise.all(list.map(async (s) => [s.id, await getMarketingTags(s.id)]))
    const map = {}
    results.forEach(([id, data]) => {
      map[id] = data
    })
    marketing.value = map
  }

  // 分类过滤：activeCat 为 '全部' 或空时不过滤
  const filterByCat = (list, activeCat) => {
    if (!activeCat || activeCat === '全部') return list
    return list.filter((s) => shopInCategory(s.id, activeCat))
  }

  return { shops, marketing, loading, loadShops, filterByCat }
}
