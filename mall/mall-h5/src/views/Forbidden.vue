<template>
  <!-- 403 无权限页面：按角色返回对应首页 / 返回门户 -->
  <div class="forbidden">
    <div class="badge">
      <van-icon name="warning-o" />
    </div>
    <h1 class="code">403</h1>
    <p class="text">无权限访问该页面</p>
    <div class="ops">
      <el-button type="primary" class="btn-portal" round @click="goPortal">返回门户</el-button>
      <el-button class="btn-home" round @click="goHome">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
// 403 页：返回门户 / 根据当前角色返回对应端的首页
import { useRouter } from 'vue-router'

const router = useRouter()

// 返回门户页
const goPortal = () => {
  router.push('/portal')
}

// 按角色跳转首页
const goHome = () => {
  const role = JSON.parse(localStorage.getItem('userInfo') || '{}').role
  if (role === 'USER') {
    router.push('/user/home')
  } else if (role === 'SHOP') {
    router.push('/shop')
  } else if (role === 'RIDER') {
    router.push('/rider/tasks')
  } else if (role === 'ADMIN') {
    router.push('/admin')
  } else {
    router.push('/portal')
  }
}
</script>

<style scoped>
.forbidden {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--mt-bg);
}
.badge {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--mt-yellow-light);
  color: #333;
  font-size: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
.code {
  font-size: 72px;
  font-weight: 700;
  color: var(--mt-yellow);
  margin: 0;
  line-height: 1;
}
.text {
  color: var(--mt-text-2);
  margin: 12px 0 28px;
  font-size: 15px;
}
.ops {
  display: flex;
  gap: 12px;
}
.btn-portal {
  background: var(--mt-yellow);
  border-color: var(--mt-yellow);
  color: #333;
  font-weight: 600;
}
.btn-portal:hover {
  background: var(--mt-yellow-light);
  border-color: var(--mt-yellow-light);
  color: #333;
}
.btn-home {
  color: var(--mt-text-2);
  border-color: var(--mt-text-3);
}
.btn-home:hover {
  color: var(--mt-yellow);
  border-color: var(--mt-yellow);
  background: transparent;
}
</style>
