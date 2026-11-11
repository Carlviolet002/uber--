<template>
  <!-- 神抢手秒杀弹层：横滑商品选择 + 详情/库存/倒计时 + 立即抢购（成功加购后可去结算） -->
  <van-popup v-model:show="show" position="bottom" round :style="{ maxWidth: '750px', margin: '0 auto' }" class="flash-popup">
    <div class="flash-popup__head">
      <span class="flash-popup__title">神抢手 · 限时秒杀</span>
      <span class="flash-popup__countdown">{{ countdownText }}</span>
      <van-icon name="cross" class="flash-popup__close" @click="show = false" />
    </div>

    <!-- 横滑商品选择 -->
    <div class="flash-popup__tabs">
      <div
        v-for="f in items"
        :key="f.id"
        class="flash-tab"
        :class="{ on: activeId === f.id }"
        @click="selectItem(f)"
      >
        <span class="flash-tab__price">¥{{ f.price }}</span>
        <span class="flash-tab__name">{{ f.name }}</span>
      </div>
    </div>

    <!-- 当前商品详情 -->
    <div v-if="detail" class="flash-popup__body">
      <div class="flash-detail__hero" :style="{ background: 'linear-gradient(135deg,' + detail.colors[0] + ',' + detail.colors[1] + ')' }">
        <span class="flash-detail__emoji">{{ detail.emoji }}</span>
        <span v-if="detail.grabbed" class="flash-detail__got">已抢到</span>
      </div>
      <div class="flash-detail__info">
        <div class="flash-detail__name">{{ detail.name }}</div>
        <div class="flash-detail__price-row">
          <span class="flash-detail__price">¥{{ detail.price }}</span>
          <span class="flash-detail__origin">¥{{ detail.originPrice }}</span>
          <span class="flash-detail__tag">{{ detail.tag }}</span>
        </div>
        <!-- 库存进度条 -->
        <div class="flash-detail__stock-row">
          <div class="flash-detail__bar">
            <div class="flash-detail__bar-inner" :style="{ width: stockPercent + '%' }"></div>
          </div>
          <span class="flash-detail__stock-text">已抢 {{ detail.sold }} 份 / 剩 {{ detail.stock }} 份</span>
        </div>
      </div>

      <div class="flash-popup__actions">
        <van-button
          v-if="!detail.grabbed"
          round
          block
          color="linear-gradient(135deg, #ff6b6b, #ff5000)"
          :loading="grabbing"
          @click="onGrab"
        >
          立即抢购 ¥{{ detail.price }}
        </van-button>
        <van-button v-else round block color="linear-gradient(135deg, #ffc300, #ffb300)" @click="onCheckout">
          已放入购物车 · 去结算
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
// 秒杀弹层：倒计时 600s 周期（打开即重置）；抢购成功把等价菜品真实加购（对应店铺第一个在售菜品）
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import { getFlashSale, getFlashDetail, grabFlash } from '../../mock/userMock'
import { useLock } from '../../composables/useLock'
import { useCartStore } from '../../stores/cart'
import { useRouter } from 'vue-router'
import api from '../../api'

const router = useRouter()
const cartStore = useCartStore()
const { locked: grabbing, run } = useLock()

const show = defineModel({ type: Boolean, default: false })

const items = ref([])
const detail = ref(null)
const activeId = ref(0)
const remain = ref(600)
let timer = null

const countdownText = computed(() => {
  const m = String(Math.floor(remain.value / 60)).padStart(2, '0')
  const s = String(remain.value % 60).padStart(2, '0')
  return m + ':' + s
})

const stockPercent = computed(() => {
  if (!detail.value) return 0
  const total = detail.value.stock + detail.value.sold
  if (total <= 0) return 0
  return Math.min(100, Math.round((detail.value.sold / total) * 100))
})

const selectItem = async (f) => {
  activeId.value = f.id
  detail.value = await getFlashDetail(f.id)
}

const onGrab = () => {
  run(async () => {
    if (!detail.value) return
    if (remain.value <= 0) {
      showToast('秒杀已结束')
      return
    }
    const res = grabFlash(detail.value.id)
    if (!res.ok) {
      showToast(res.msg)
      return
    }
    // 真实加购：取对应店铺第一个在售菜品作为秒杀等价菜品（保证结算流程真实可走）
    try {
      const dishList = (await api.dishes(detail.value.shopId)) || []
      const dish = dishList.find((d) => !d.soldOut) || dishList[0]
      if (dish) {
        cartStore.add({
          dishId: dish.id,
          name: dish.name,
          price: dish.price,
          image: dish.image,
          num: 1,
          shopId: detail.value.shopId
        })
      }
    } catch (e) {
      // 加购失败不影响抢购结果提示
    }
    showSuccessToast(res.msg)
    detail.value = await getFlashDetail(detail.value.id)
  })
}

const onCheckout = () => {
  show.value = false
  if (detail.value) {
    router.push('/user/shop/' + detail.value.shopId)
  }
}

watch(show, (v) => {
  if (v) {
    remain.value = 600
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      remain.value = remain.value > 0 ? remain.value - 1 : 0
    }, 1000)
    if (!items.value.length) {
      getFlashSale().then(async (d) => {
        items.value = d
        if (d.length) await selectItem(d[0])
      })
    } else if (!detail.value && items.value.length) {
      selectItem(items.value[0])
    }
  } else if (timer) {
    clearInterval(timer)
    timer = null
  }
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.flash-popup {
  border-radius: 20px 20px 0 0;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
}
.flash-popup__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 8px;
}
.flash-popup__title {
  font-size: 17px;
  font-weight: 700;
  color: #333;
}
.flash-popup__countdown {
  font-size: 12px;
  font-weight: 700;
  color: #ff5000;
  font-variant-numeric: tabular-nums;
}
.flash-popup__close {
  margin-left: auto;
  color: var(--mt-text-3);
  font-size: 18px;
}
.flash-popup__tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 4px 16px 10px;
  scrollbar-width: none;
}
.flash-popup__tabs::-webkit-scrollbar {
  display: none;
}
.flash-tab {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--mt-bg);
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 6px 10px;
  cursor: pointer;
}
.flash-tab.on {
  border-color: #ff5000;
  background: #fff7f2;
}
.flash-tab__price {
  font-size: 14px;
  font-weight: 800;
  color: #ff5000;
}
.flash-tab__name {
  font-size: 10px;
  color: var(--mt-text-2);
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.flash-popup__body {
  padding: 0 16px;
}
.flash-detail__hero {
  height: 130px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.flash-detail__emoji {
  font-size: 64px;
}
.flash-detail__got {
  position: absolute;
  right: 10px;
  top: 10px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 10px;
  padding: 2px 8px;
}
.flash-detail__info {
  margin-top: 10px;
}
.flash-detail__name {
  font-size: 15px;
  font-weight: 700;
  color: #333;
  line-height: 1.4;
}
.flash-detail__price-row {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.flash-detail__price {
  font-size: 24px;
  font-weight: 800;
  color: #ff5000;
}
.flash-detail__origin {
  font-size: 12px;
  color: var(--mt-text-3);
  text-decoration: line-through;
}
.flash-detail__tag {
  font-size: 10px;
  color: #ff5000;
  border: 1px solid #ff5000;
  border-radius: 4px;
  padding: 0 5px;
}
.flash-detail__stock-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.flash-detail__bar {
  flex: 1;
  height: 8px;
  background: #ffe8e8;
  border-radius: 4px;
  overflow: hidden;
}
.flash-detail__bar-inner {
  height: 100%;
  background: linear-gradient(90deg, #ff6b6b, #ff5000);
  border-radius: 4px;
  transition: width 0.3s ease;
}
.flash-detail__stock-text {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--mt-text-3);
}
.flash-popup__actions {
  margin-top: 16px;
}
</style>
