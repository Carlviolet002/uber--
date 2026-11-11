<template>
  <!-- 收藏心形按钮：红心切换 + 缩放动效，点击冒泡阻止避免触发卡片跳转 -->
  <span
    class="fav-btn"
    :class="{ on: faved }"
    @click.stop="onToggle"
  >
    <van-icon :name="faved ? 'like' : 'like-o'" />
  </span>
</template>

<script setup>
// 收藏心形：userMock.toggleFavorite 持久化；faved 用 v-model 双向同步
import { ref, watch, computed } from 'vue'
import { showToast } from 'vant'
import { toggleFavorite, isFavorited } from '../../mock/userMock'

const props = defineProps({
  shopId: { type: [Number, String], required: true },
  shop: { type: Object, default: null }
})

const emit = defineEmits(['update:faved', 'change'])

const faved = ref(false)
const animating = ref(false)

watch(
  () => props.shopId,
  (id) => {
    faved.value = isFavorited(id)
  },
  { immediate: true }
)

const onToggle = () => {
  const shop = props.shop || { id: props.shopId, name: '' }
  const res = toggleFavorite(shop)
  faved.value = res.faved
  emit('update:faved', res.faved)
  emit('change', res.faved)
  animating.value = true
  setTimeout(() => (animating.value = false), 300)
  showToast(res.faved ? '已收藏' : '已取消收藏')
}

// 供父组件在外部同步（如店铺详情页头部心形）
const isOn = computed(() => faved.value)
defineExpose({ isOn })
</script>

<style scoped>
.fav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.15s ease;
}
.fav-btn .van-icon {
  font-size: 16px;
  color: #999;
}
.fav-btn.on .van-icon {
  color: #ff4d26;
}
.fav-btn:active {
  transform: scale(0.8);
}
</style>
