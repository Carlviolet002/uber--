<template>
  <!-- 骑手任务详情页：查看订单详情并执行接单/取餐/送达操作 -->
  <div class="page">
    <van-nav-bar title="任务详情" left-arrow @click-left="router.back()" />
    <template v-if="detail">
      <!-- 订单信息 -->
      <van-cell-group inset>
        <van-cell title="订单编号" :value="detail.order.orderNo" />
        <van-cell title="商家ID" :value="detail.order.shopId" />
        <van-cell title="订单状态" :value="statusText" />
        <van-cell title="订单金额" :value="'¥' + Number(detail.order.total || 0).toFixed(2)" />
        <van-cell title="配送费" :value="'¥' + Number(detail.order.deliveryFee || 0).toFixed(2)" />
        <van-cell title="下单时间" :value="(detail.order.createdAt || '').toString().slice(0, 19)" />
      </van-cell-group>
      <!-- 商品明细 -->
      <van-cell-group inset title="商品明细">
        <van-cell
          v-for="(it, i) in detail.items"
          :key="i"
          :title="it.dishName || it.name || '菜品' + (it.dishId || '')"
          :label="'x' + it.num"
          :value="'¥' + Number(it.price || 0).toFixed(2)"
        />
      </van-cell-group>
      <!-- 操作按钮 -->
      <div v-if="detail.order.status === 4 || detail.order.status === 5" class="ops">
        <van-button
          v-if="detail.order.status === 4"
          round
          block
          type="primary"
          :loading="acting"
          @click="doAction('accept')"
        >
          确认接单
        </van-button>
        <van-button
          v-if="detail.order.status === 4"
          round
          block
          type="warning"
          class="op-gap"
          :loading="acting"
          @click="doAction('picked')"
        >
          到店取餐
        </van-button>
        <van-button
          v-if="detail.order.status === 5"
          round
          block
          type="success"
          :loading="acting"
          @click="doAction('delivered')"
        >
          确认送达
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
// 骑手任务详情页：status==4 可接单/取餐，status==5 可确认送达
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import api from '../../api'

const router = useRouter()
const route = useRoute()
const orderNo = route.params.no

const detail = ref(null)
const acting = ref(false)

// 状态名适配：后端新增 9 备餐中 / 11 已送达待确认，其余沿用后端返回的 statusName
const statusText = computed(() => {
  if (!detail.value) return ''
  const s = Number(detail.value.order.status)
  const map = { 9: '备餐中(待取餐)', 11: '已送达待确认' }
  return map[s] || detail.value.order.statusName
})

// 加载订单详情（复用通用订单详情接口）
const load = async () => {
  try {
    detail.value = await api.orderDetail(orderNo)
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 执行骑手操作：accept 接单 / picked 取餐 / delivered 送达
const doAction = async (type) => {
  acting.value = true
  try {
    if (type === 'accept') {
      await api.riderAccept(orderNo)
      showToast('接单成功')
    } else if (type === 'picked') {
      await api.riderPicked(orderNo)
      showToast('已取餐，开始配送')
    } else if (type === 'delivered') {
      await api.riderDelivered(orderNo)
      showToast('订单已送达')
    }
    await load()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 24px;
}
.ops {
  margin: 24px 16px;
}
.op-gap {
  margin-top: 12px;
}
</style>
