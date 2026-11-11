<template>
  <!-- 门户页：美团风格入口，登录 + 四端身份选择 -->
  <div class="portal">
    <!-- 顶部 Hero：黄色渐变 + 圆角下沿 + 装饰搜索框 -->
    <div class="hero">
      <div class="hero-inner">
        <div class="hero-logo">骑手外卖 MALL</div>
        <div class="hero-sub">点外卖 · 找骑手 · 一站式校园配送</div>
        <div class="hero-search" @click="focusSearch">
          <van-icon name="search" class="hero-search-icon" />
          <input
            v-model="searchKw"
            class="hero-search-input"
            placeholder="搜索门店/菜品"
            @keyup.enter="onSearch"
          />
        </div>
      </div>
    </div>

    <div class="portal-body">
      <!-- 未登录：登录/注册卡（负 margin 上移压住 Hero 下沿，登录注册均收敛到门户） -->
      <div v-if="!userStore.isLogin" class="login-card">
        <div class="login-tabs">
          <div class="login-tab" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</div>
          <div class="login-tab" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</div>
        </div>

        <!-- 登录 -->
        <van-form v-if="mode === 'login'" @submit="onLogin">
          <van-field
            v-model="form.username"
            name="username"
            placeholder="请输入用户名"
            left-icon="contact"
            :rules="[{ required: true, message: '请填写用户名' }]"
          />
          <van-field
            v-model="form.password"
            type="password"
            name="password"
            placeholder="请输入密码"
            left-icon="lock"
            :rules="[{ required: true, message: '请填写密码' }]"
          />
          <van-button round block native-type="submit" class="login-btn" :loading="loading">
            登 录
          </van-button>
        </van-form>

        <!-- 注册（内嵌门户，注册后自动登录并跳对应首页） -->
        <van-form v-else @submit="onRegister">
          <van-field
            v-model="regForm.username"
            name="username"
            placeholder="请输入用户名"
            left-icon="contact"
            :rules="[{ required: true, message: '请填写用户名' }]"
          />
          <van-field
            v-model="regForm.password"
            type="password"
            name="password"
            placeholder="请输入密码（至少 6 位）"
            left-icon="lock"
            :rules="[{ required: true, message: '请填写密码' }, { pattern: /^.{6,}$/, message: '密码至少 6 位' }]"
          />
          <van-field
            v-model="regForm.nickname"
            name="nickname"
            placeholder="请输入昵称"
            left-icon="smile-o"
            :rules="[{ required: true, message: '请填写昵称' }]"
          />
          <van-field
            v-model="regForm.phone"
            name="phone"
            placeholder="请输入手机号"
            left-icon="phone-o"
            :rules="[{ required: true, message: '请填写手机号' }, { pattern: /^1\d{10}$/, message: '手机号格式不正确' }]"
          />
          <van-field name="roles" label="注册身份">
            <template #input>
              <van-checkbox-group v-model="regForm.roles" direction="horizontal">
                <van-checkbox name="USER" shape="square">用户</van-checkbox>
                <van-checkbox name="SHOP" shape="square">商家</van-checkbox>
                <van-checkbox name="RIDER" shape="square">骑手</van-checkbox>
              </van-checkbox-group>
            </template>
          </van-field>
          <van-button round block native-type="submit" class="login-btn" :loading="loading">
            注 册
          </van-button>
        </van-form>
      </div>

      <!-- 已登录：欢迎区 + 退出 -->
      <div v-else class="welcome-card">
        <div class="welcome-avatar">
          <van-icon name="user-o" />
        </div>
        <div class="welcome-info">
          <div class="welcome-line">
            欢迎回来，{{ userStore.userInfo.nickname || userStore.userInfo.username || '用户' }}
            <span class="welcome-role">（{{ userStore.roleName }}）</span>
          </div>
          <div class="welcome-tip">选择下方身份卡片，进入对应工作台</div>
        </div>
        <van-button size="small" plain round class="logout-btn" @click="onLogout">退出登录</van-button>
      </div>

      <!-- 四端入口 -->
      <div class="entries">
        <div class="entries-title">选择您的身份</div>
        <div class="entry-grid">
          <div
            v-for="e in entries"
            :key="e.role"
            class="entry-card"
            :class="{ active: isActive(e.role), dim: isDim(e.role) }"
            @click="goEntry(e)"
          >
            <div class="entry-chip" :class="{ 'chip-active': isActive(e.role) }">
              {{ isActive(e.role) ? '可进入' : e.roleName }}
            </div>
            <div class="entry-icon"><van-icon :name="e.icon" /></div>
            <div class="entry-name">{{ e.name }}</div>
            <div class="entry-desc">{{ e.desc }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 门户页：搜索跳转 / 登录（成功后按 homeByRoles 或 redirect 参数跳转）/ 四端入口
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const searchKw = ref('')
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const mode = ref('login') // 'login' | 'register'：登录注册同卡切换
const regForm = reactive({ username: '', password: '', nickname: '', phone: '', roles: ['USER'] })

// 四端入口配置：角色 / 端名 / 一句话描述 / 图标 / 首页路径
const entries = [
  { role: 'USER', roleName: '用户', name: '点餐', desc: '逛店铺 · 点美食 · 一键下单', icon: 'shop-o', home: '/user/home' },
  { role: 'SHOP', roleName: '商家', name: '经营', desc: '菜品管理 · 接单 · 出餐', icon: 'bag-o', home: '/shop' },
  { role: 'RIDER', roleName: '骑手', name: '配送', desc: '抢单 · 取餐 · 送达', icon: 'logistics', home: '/rider/tasks' },
  { role: 'ADMIN', roleName: '管理员', name: '管理', desc: '平台数据 · 商家骑手 · 派单', icon: 'setting-o', home: '/admin' }
]

// 当前账号是否拥有该入口角色（位掩码）
const isActive = (role) => userStore.isLogin && userStore.hasRole(role)
// 登录后无该角色（且拥有其他角色）的卡片置灰
const isDim = (role) => userStore.isLogin && !userStore.hasRole(role)

// 聚焦装饰搜索框
const focusSearch = (e) => {
  if (e && e.target && e.target.tagName === 'INPUT') return
  const input = document.querySelector('.hero-search-input')
  if (input) input.focus()
}

// 搜索：已登录且 USER 角色 → 跳店铺列表并带关键字；否则提示
const onSearch = () => {
  if (!userStore.isLogin) {
    showToast('请先登录')
    return
  }
  if (!userStore.hasRole('USER')) {
    showToast('当前角色无权限')
    return
  }
  const kw = searchKw.value.trim()
  router.push('/user/home' + (kw ? '?kw=' + encodeURIComponent(kw) : ''))
}

// 登录：成功后提示并跳转（优先 redirect 参数，其次按角色 homeByRoles）
const onLogin = async () => {
  loading.value = true
  try {
    await userStore.loginAction({ ...form })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    if (redirect && typeof redirect === 'string' && redirect.startsWith('/')) {
      router.push(redirect)
    } else {
      router.push(userStore.homeByRoles())
    }
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

// 注册：提交后自动登录并跳对应首页；自动登录失败则回退到登录 tab
const onRegister = async () => {
  loading.value = true
  try {
    await api.register(regForm)
    try {
      await userStore.loginAction({ username: regForm.username, password: regForm.password })
      ElMessage.success('注册成功，已自动登录')
      router.push(userStore.homeByRoles())
    } catch (loginErr) {
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
    }
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

// 退出登录：停留在本页
const onLogout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
}

// 四端入口点击：未登录提示；拥有该角色则跳首页；否则提示无权限
const goEntry = (e) => {
  if (!userStore.isLogin) {
    showToast('请先登录')
    return
  }
  if (userStore.hasRole(e.role)) {
    router.push(e.home)
  } else {
    showToast('当前角色无权限')
  }
}
</script>

<style scoped>
.portal {
  min-height: 100vh;
  background: var(--mt-bg);
  padding-bottom: 32px;
}

/* ===== Hero ===== */
.hero {
  background: var(--mt-gradient);
  border-radius: 0 0 24px 24px;
  padding: 48px 20px 64px;
}
.hero-inner {
  max-width: 960px;
  margin: 0 auto;
  text-align: center;
}
.hero-logo {
  font-size: 30px;
  font-weight: 700;
  color: #333;
  letter-spacing: 2px;
}
.hero-sub {
  margin-top: 8px;
  font-size: 14px;
  color: rgba(51, 51, 51, 0.75);
}
.hero-search {
  margin: 24px auto 0;
  max-width: 560px;
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: var(--mt-radius-btn);
  padding: 10px 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  cursor: text;
}
.hero-search-icon {
  font-size: 16px;
  color: var(--mt-text-3);
  margin-right: 8px;
}
.hero-search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  color: var(--mt-text);
  background: transparent;
}
.hero-search-input::placeholder {
  color: var(--mt-text-3);
}

/* ===== 主体（桌面居中，移动全宽） ===== */
.portal-body {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 12px;
}

/* ===== 登录卡：负 margin 压住 Hero 下沿 ===== */
.login-card {
  position: relative;
  z-index: 2;
  background: #fff;
  border-radius: var(--mt-radius);
  margin: -40px auto 0;
  max-width: 560px;
  padding: 20px 16px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
}
.login-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--mt-text);
  text-align: center;
  margin-bottom: 12px;
}
.login-card :deep(.van-field) {
  background: var(--mt-bg);
  border-radius: var(--mt-radius-btn);
  margin-bottom: 12px;
  padding: 8px 14px;
}
.login-btn {
  margin-top: 8px;
  background: var(--mt-yellow);
  border: none;
  color: #333;
  font-weight: 600;
}
.login-tabs {
  display: flex;
  margin-bottom: 16px;
  background: var(--mt-bg);
  border-radius: var(--mt-radius-btn);
  padding: 4px;
}
.login-tab {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 15px;
  color: var(--mt-text-3);
  border-radius: var(--mt-radius-btn);
  cursor: pointer;
  transition: all 0.15s ease;
}
.login-tab.active {
  background: #fff;
  color: var(--mt-text);
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}

/* ===== 已登录欢迎卡 ===== */
.welcome-card {
  position: relative;
  z-index: 2;
  background: #fff;
  border-radius: var(--mt-radius);
  margin: -40px auto 0;
  max-width: 560px;
  padding: 18px 16px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
}
.welcome-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--mt-yellow);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.welcome-info {
  flex: 1;
  margin-left: 12px;
  min-width: 0;
}
.welcome-line {
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.welcome-role {
  color: var(--mt-text-2);
  font-weight: 400;
}
.welcome-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mt-text-3);
}
.logout-btn {
  flex-shrink: 0;
  color: var(--mt-text-2);
}

/* ===== 四端入口 ===== */
.entries {
  margin-top: 20px;
}
.entries-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--mt-text);
  text-align: center;
  margin-bottom: 12px;
}
.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.entry-card {
  position: relative;
  background: #fff;
  border-radius: var(--mt-radius);
  border: 2px solid transparent;
  padding: 20px 12px 16px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}
.entry-card:active {
  transform: scale(0.97);
}
.entry-card.active {
  border-color: var(--mt-yellow);
  box-shadow: 0 4px 14px rgba(255, 195, 0, 0.35);
}
.entry-card.dim {
  opacity: 0.5;
}
.entry-chip {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 10px;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 10px;
  background: var(--mt-bg);
  color: var(--mt-text-3);
}
.entry-chip.chip-active {
  background: var(--mt-yellow);
  color: #333;
  font-weight: 600;
}
.entry-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--mt-yellow);
  color: #fff;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 10px;
}
.entry-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--mt-text);
}
.entry-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mt-text-3);
  line-height: 1.5;
}

/* ===== 桌面端 4 列 ===== */
@media (min-width: 768px) {
  .entry-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
