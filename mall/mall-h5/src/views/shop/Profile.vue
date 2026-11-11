<template>
  <!-- 我的店铺（SHOP）：店铺信息卡 + 退出登录 -->
  <div class="page">
    <!-- 顶部黄色标题栏 -->
    <div class="header">
      <div class="header-title">我的店铺</div>
    </div>

    <!-- 店铺信息卡 -->
    <div class="shop-card">
      <div class="shop-name-row">
        <span class="shop-name">{{ shop.name || '加载中…' }}</span>
        <van-tag :type="shop.status === 1 ? 'success' : 'default'">
          {{ shop.status === 1 ? '营业中' : '休息中' }}
        </van-tag>
      </div>
      <div class="shop-line">地址：{{ shop.address || '-' }}</div>
      <div class="shop-line">电话：{{ shop.phone || '-' }}</div>
    </div>

    <!-- 退出登录 -->
    <div class="logout-wrap">
      <van-button round block plain type="danger" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
// 我的店铺：加载本店信息（shopDetail），退出登录后回门户页
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '../../stores/user'
import { shopDetail, shopList } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const shop = ref({})

/**
 * 商家身份获取：与 Workbench 相同方案。
 * 种子数据 shop1 账号 userId=3、shopId=1，昵称"张姐麻辣烫"与店铺名一致：
 * 优先登录信息 shopId → 昵称匹配店铺名 → userId-2 兜底 → 1。
 */
const resolveShopId = async () => {
  const info = userStore.userInfo || {}
  if (info.shopId) return Number(info.shopId)
  try {
    const shops = (await shopList()) || []
    const hit = shops.find((s) => s.name === info.nickname)
    if (hit) return Number(hit.id)
  } catch (e) {
    // 列表失败走兜底逻辑
  }
  const uid = Number(info.userId ?? info.id)
  if (uid) return uid - 2
  return 1
}

// 退出登录：清空登录态并回门户
const onLogout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确认退出登录吗？' })
  } catch (e) {
    return // 用户取消
  }
  userStore.logout()
  showToast('已退出登录')
  router.push('/portal')
}

onMounted(async () => {
  const shopId = await resolveShopId()
  try {
    shop.value = (await shopDetail(shopId)) || {}
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
})
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
  padding-bottom: 24px;
}
.header {
  padding: 24px 16px;
  background: var(--mt-gradient, linear-gradient(135deg, #ffc300, #ffb300));
  color: #fff;
}
.header-title {
  font-size: 22px;
  font-weight: 600;
}
.shop-card {
  margin: 12px;
  padding: 16px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.shop-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.shop-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.shop-line {
  margin-top: 10px;
  font-size: 14px;
  color: var(--mt-text-2, #666);
}
.logout-wrap {
  margin: 24px 16px;
}
</style>
