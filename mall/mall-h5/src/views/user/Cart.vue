<template>
  <!-- 购物车页：展示购物车条目、调整数量、去结算 -->
  <div class="page">
    <van-nav-bar title="购物车" left-arrow @click-left="router.back()" />
    <van-empty v-if="cartStore.items.length === 0" description="购物车是空的，快去点餐吧">
      <van-button round type="primary" size="small" class="go-shopping" @click="router.push('/user/home')">
        去逛逛
      </van-button>
    </van-empty>
    <template v-else>
      <van-cell-group inset title="商品清单">
        <div v-for="(item, index) in cartStore.items" :key="item.dishId" class="cart-item">
          <div class="cart-info">
            <div class="cart-name">{{ item.name }}</div>
            <div class="cart-price">¥{{ Number(item.price).toFixed(2) }}</div>
          </div>
          <div class="cart-right">
            <van-stepper
              :model-value="item.num"
              min="0"
              integer
              @change="onChange(index, $event)"
            />
            <div class="cart-subtotal">小计 ¥{{ (Number(item.price) * item.num).toFixed(2) }}</div>
          </div>
        </div>
      </van-cell-group>
      <!-- tabbar 常驻后结算栏上移 50px，避免两层固定底栏相互遮挡 -->
      <van-submit-bar
        :price="cartStore.totalPrice * 100"
        button-text="去结算"
        :style="{ bottom: '50px' }"
        @submit="router.push('/user/checkout')"
      />
    </template>
  </div>
</template>

<script setup>
// 购物车页：数量调整（减到 0 移除），底部合计 + 去结算
import { useRouter } from 'vue-router'
import { useCartStore } from '../../stores/cart'

const router = useRouter()
const cartStore = useCartStore()

// 数量变化：0 则移除条目，否则更新数量
const onChange = (index, val) => {
  if (val <= 0) {
    cartStore.remove(index)
  } else {
    cartStore.setNum(cartStore.items[index].dishId, val)
  }
}
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 60px;
}
.cart-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
}
.cart-info {
  flex: 1;
  padding-right: 12px;
}
.cart-name {
  font-size: 15px;
}
.cart-price {
  margin-top: 4px;
  color: #ee0a24;
  font-weight: 600;
}
.cart-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.cart-subtotal {
  margin-top: 6px;
  color: #969799;
  font-size: 12px;
}
.go-shopping {
  margin-top: 8px;
}
</style>
