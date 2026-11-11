<template>
  <!-- 店铺设置（P0 轻量版）：店铺信息卡 + 营业开关 + 公告展示 + 入口菜单 -->
  <div class="page">
    <!-- 店铺信息卡 -->
    <div class="card">
      <div class="card-title">店铺信息</div>
      <div class="info-row"><span class="info-label">店铺名称</span><span>{{ shop.name || '-' }}</span></div>
      <div class="info-row"><span class="info-label">店铺地址</span><span>{{ shop.address || '-' }}</span></div>
      <div class="info-row"><span class="info-label">联系电话</span><span>{{ shop.phone || '-' }}</span></div>
    </div>

    <!-- 营业状态 -->
    <div class="card">
      <div class="card-title">营业状态</div>
      <div class="switch-row">
        <div>
          <div class="switch-name">店铺营业中</div>
          <div class="switch-sub">关闭后用户将无法下单</div>
        </div>
        <van-switch
          :model-value="shop.status === 1"
          size="24px"
          active-color="#FFC300"
          :loading="switching"
          @update:model-value="onToggleStatus"
        />
      </div>
    </div>

    <!-- 店铺公告（P0 只读展示，P1 升级为可编辑表单） -->
    <div class="card">
      <div class="card-title">店铺公告</div>
      <div class="notice">{{ editable.notice }}</div>
    </div>

    <!-- 入口菜单 -->
    <div class="menu-card">
      <div class="menu-item" @click="router.push('/shop/dishes')">
        <span class="menu-icon">🍜</span><span class="menu-name">商品管理</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
      <div class="menu-item" @click="router.push('/shop/reviews')">
        <span class="menu-icon">⭐</span><span class="menu-name">评价管理</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
      <div class="menu-item" @click="router.push('/shop/customers')">
        <span class="menu-icon">👥</span><span class="menu-name">顾客管理</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
      <div class="menu-item" @click="router.push('/shop/promote')">
        <span class="menu-icon">📣</span><span class="menu-name">推广中心</span>
        <van-icon name="arrow" color="#c8c9cc" />
      </div>
    </div>
  </div>
</template>

<script setup>
// 店铺设置轻量版：营业开关接真实接口，公告为 shopMock 演示数据（P1 升级完整可编辑表单）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useShopStatsStore } from '../../stores/shopStats'
import { shopDetail, shopStatus } from '@/api'
import { getShopEditable } from '../../mock/shopMock'

const router = useRouter()
const stats = useShopStatsStore()

const shop = ref({})
const editable = ref({ notice: '' })
const switching = ref(false)

// 营业状态开关（真实生效）
const onToggleStatus = async (val) => {
  switching.value = true
  try {
    await shopStatus(stats.shopId, val ? 1 : 0)
    shop.value.status = val ? 1 : 0
    stats.shopInfo = { ...stats.shopInfo, status: shop.value.status }
    showToast(val ? '已开始营业' : '已暂停营业')
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    switching.value = false
  }
}

onMounted(async () => {
  const shopId = await stats.ensureShopId()
  try {
    shop.value = (await shopDetail(shopId)) || {}
    editable.value = await getShopEditable()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: 70px;
}
.card {
  margin: 8px 12px;
  padding: 14px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-text, #222);
  margin-bottom: 10px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 5px 0;
  color: var(--mt-text, #222);
}
.info-label {
  color: var(--mt-text-3, #999);
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.switch-name {
  font-size: 14px;
  color: var(--mt-text, #222);
}
.switch-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--mt-text-3, #999);
}
.notice {
  font-size: 13px;
  color: var(--mt-text-2, #666);
  background: #fff9e6;
  border-radius: 10px;
  padding: 10px;
  line-height: 20px;
}
.menu-card {
  margin: 10px 12px 0;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  padding: 13px 14px;
  border-bottom: 1px solid #f7f8fa;
  cursor: pointer;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-icon {
  width: 24px;
  margin-right: 10px;
  text-align: center;
}
.menu-name {
  flex: 1;
  font-size: 14px;
  color: var(--mt-text, #222);
}
</style>
