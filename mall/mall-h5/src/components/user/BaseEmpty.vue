<template>
  <!-- 统一空态组件：文案 + 可选行动按钮（黄色胶囊），全项目空列表走这里 -->
  <div class="base-empty">
    <van-empty :description="description" :image="image" />
    <van-button
      v-if="actionText"
      round
      class="base-empty__btn"
      color="linear-gradient(135deg, #ffc300, #ffb300)"
      @click="emit('action')"
    >
      {{ actionText }}
    </van-button>
  </div>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' },
  actionText: { type: String, default: '' },
  image: { type: String, default: 'search' }
})

const emit = defineEmits(['action'])
</script>

<style scoped>
.base-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
}
.base-empty__btn {
  margin-top: 4px;
  padding: 0 28px;
  color: #333;
  font-weight: 600;
  border: none;
}
</style>
