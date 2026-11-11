<template>
  <!-- 我的页：黄头部 + 资产列表 + 常用功能 + 退出登录 -->
  <div class="me">
    <!-- 纯黄头部 -->
    <div class="header">
      <div class="avatar">🐰</div>
      <div class="nick">{{ nickname }}</div>
      <div class="phone-row">
        <span class="phone">{{ maskedPhone }}</span>
        <span class="phone-edit" @click="showPhoneEdit = true">修改</span>
      </div>
    </div>

    <!-- 资产列表 -->
    <div class="card assets">
      <van-skeleton v-if="assetsLoading" title :row="3" class="assets-skeleton" />
      <template v-else>
        <div v-for="a in assetRows" :key="a.name" class="asset-row" @click="a.onClick">
          <span class="asset-icon">{{ a.icon }}</span>
          <span class="asset-name">{{ a.name }}</span>
          <span class="asset-value">{{ a.value }}</span>
          <van-icon name="arrow" class="asset-arrow" />
        </div>
      </template>
    </div>

    <!-- 常用功能 -->
    <div class="card funcs">
      <div v-for="f in funcRows" :key="f.name" class="func-row" @click="f.onClick">
        <span class="func-icon">{{ f.icon }}</span>
        <span class="func-name">{{ f.name }}</span>
        <van-icon name="arrow" class="func-arrow" />
      </div>
    </div>

    <!-- 退出登录 -->
    <div class="logout" @click="onLogout">退出账号</div>

    <!-- 修改手机号弹窗 -->
    <van-popup v-model:show="showPhoneEdit" position="bottom" round class="phone-pop">
      <div class="phone-pop-title">修改手机号</div>
      <van-field v-model="phoneInput" type="tel" maxlength="11" placeholder="请输入 11 位手机号" />
      <van-button round block type="primary" class="btn-yellow" :loading="savingPhone" @click="onSavePhone">
        保存
      </van-button>
    </van-popup>
  </div>
</template>

<script setup>
// 我的页：昵称/手机号走真实 user 服务（updateMe），资产数值走 userMock
import { ref, computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showDialog } from 'vant'
import api from '../../api'
import { useUserStore } from '../../stores/user'
import { getMyAssets } from '../../mock/userMock'

defineOptions({ name: 'UserMe' })

const router = useRouter()
const userStore = useUserStore()

const assets = ref({ redCount: 16, voucherCount: 2, subsidy: '20', wallet: '32.5' })
const assetsLoading = ref(true)
const showPhoneEdit = ref(false)
const phoneInput = ref('')
const savingPhone = ref(false)

const nickname = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || 'LkB889885562')
const maskedPhone = computed(() => {
  const p = String(userStore.userInfo.phone || '13000001285')
  return p.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
})

const assetRows = computed(() => [
  { icon: '🧧', name: '美团红包', value: assets.value.redCount + '张', onClick: () => router.push('/user/coupons') },
  { icon: '🎟️', name: '商家代金券', value: assets.value.voucherCount + '张', onClick: () => router.push('/user/coupons/manage') },
  { icon: '💰', name: '津贴', value: assets.value.subsidy + '元', onClick: () => showToast('津贴为平台活动福利（演示数据）') },
  { icon: '👛', name: '我的钱包', value: '¥' + assets.value.wallet, onClick: () => showDialog({ title: '我的钱包', message: '余额 ¥' + assets.value.wallet + '\n（演示数据）' }) },
  { icon: '📍', name: '我的地址', value: '', onClick: () => router.push('/user/addresses') },
  { icon: '⭐', name: '我的收藏', value: '', onClick: () => router.push('/user/favorites') }
])

const funcRows = [
  { icon: '💬', name: '我的评价', onClick: () => router.push('/user/orders') },
  { icon: '🎧', name: '客服中心', onClick: () => router.push('/user/service') },
  { icon: '📄', name: '协议和说明', onClick: () => showDialog({ title: '协议和说明', message: '本平台为校园即时配送平台（毕设演示）。\n用户下单即表示同意平台配送条款。' }) },
  { icon: '📱', name: '修改手机号', onClick: () => { phoneInput.value = userStore.userInfo.phone || ''; showPhoneEdit.value = true } },
  { icon: '🏪', name: '商家入驻', onClick: () => showToast('商家入驻申请提交成功，请等待审核（演示）') },
  { icon: '🔒', name: '隐私政策', onClick: () => showDialog({ title: '隐私政策', message: '我们仅收集配送必需的姓名、电话、地址信息，不会用于其他用途。' }) }
]

const onSavePhone = async () => {
  if (!/^1\d{10}$/.test(phoneInput.value)) {
    showToast('请输入正确的 11 位手机号')
    return
  }
  savingPhone.value = true
  try {
    const updated = await api.updateMe({ phone: phoneInput.value })
    // 更新本地登录态缓存
    userStore.userInfo = { ...userStore.userInfo, phone: updated.phone || phoneInput.value }
    localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
    showPhoneEdit.value = false
    showToast('手机号已修改')
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingPhone.value = false
  }
}

const onLogout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
  } catch (e) {
    return
  }
  userStore.logout()
  router.push('/portal')
}

// keep-alive：首次挂载与每次激活都刷新资产（券/红包数量联动）
onActivated(async () => {
  assetsLoading.value = true
  assets.value = await getMyAssets()
  assetsLoading.value = false
})
</script>

<style scoped>
.me {
  min-height: 100vh;
  background: var(--mt-bg);
}

/* ===== 黄头部 ===== */
.header {
  background: var(--mt-gradient);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 28px 16px 36px;
  border-radius: 0 0 20px 20px;
}
.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #fff;
  font-size: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}
.nick {
  margin-top: 10px;
  font-size: 18px;
  font-weight: 700;
  color: #333;
}
.phone-row {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.phone {
  font-size: 13px;
  color: var(--mt-text-2);
}
.phone-edit {
  font-size: 11px;
  color: #333;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 10px;
  padding: 2px 10px;
  cursor: pointer;
}

/* ===== 卡片 ===== */
.card {
  background: #fff;
  margin: 10px 8px 0;
  border-radius: var(--mt-radius);
  padding: 4px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.assets-skeleton {
  padding: 10px 0;
}
.asset-row,
.func-row {
  display: flex;
  align-items: center;
  padding: 13px 0;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.asset-row:last-child,
.func-row:last-child {
  border-bottom: none;
}
.asset-icon,
.func-icon {
  font-size: 18px;
  margin-right: 10px;
}
.asset-name,
.func-name {
  flex: 1;
  font-size: 14px;
  color: #333;
}
.asset-value {
  font-size: 13px;
  color: #ff5000;
  font-weight: 600;
}
.asset-arrow,
.func-arrow {
  font-size: 12px;
  color: var(--mt-text-3);
  margin-left: 6px;
}

/* ===== 退出 ===== */
.logout {
  margin: 16px 8px;
  background: #fff;
  border-radius: var(--mt-radius);
  padding: 14px;
  text-align: center;
  font-size: 15px;
  color: #ee0a24;
  cursor: pointer;
}

/* ===== 修改手机号弹窗 ===== */
.phone-pop {
  padding: 20px 16px 24px;
}
.phone-pop-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  text-align: center;
  margin-bottom: 12px;
}
.btn-yellow {
  --van-button-primary-background: var(--mt-yellow, #ffc300);
  --van-button-primary-border-color: var(--mt-yellow, #ffc300);
  --van-button-primary-color: #333;
  --van-button-radius: var(--mt-radius-btn, 24px);
  margin-top: 16px;
}
</style>
