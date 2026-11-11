<template>
  <!-- 管理端布局：左侧菜单 + 顶部用户信息 + 右侧内容区 -->
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">校园即时配送平台</div>
      <el-menu router :default-active="route.path" class="menu">
        <el-menu-item index="/admin/dashboard">运营数据看板</el-menu-item>
        <el-menu-item index="/admin/users">用户管理</el-menu-item>
        <el-menu-item index="/admin/shops">商家管理</el-menu-item>
        <el-menu-item index="/admin/riders">骑手管理</el-menu-item>
        <el-menu-item index="/admin/orders">订单监控</el-menu-item>
        <el-menu-item index="/admin/reviews">评价管理</el-menu-item>
        <el-menu-item index="/admin/dispatch">派单管理</el-menu-item>
        <el-menu-item index="/admin/simulation">策略仿真对比</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="user-info">
          当前用户：{{ userStore.userInfo.nickname || userStore.userInfo.username || '管理员' }}
        </span>
        <el-button type="danger" link @click="onLogout">退出登录</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
// 管理端布局：el-menu router 模式联动子路由
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 退出登录
const onLogout = async () => {
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '提示', { type: 'warning' })
  } catch (e) {
    return // 用户取消
  }
  userStore.logout()
  router.push('/portal')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #001529;
  overflow-x: hidden;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #ffc300;
  font-size: 15px;
  font-weight: 600;
}
.menu {
  border-right: none;
  /* el-menu 自带白色底，会盖住 .aside 的深蓝；必须把菜单自身背景改成同色 */
  --el-menu-bg-color: #001529;
  background-color: #001529;
  /* 全局主色是黄，默认的 hover 背景会泛浅黄；改成深蓝浅一档 */
  --el-menu-hover-bg-color: #00213d;
}
.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.75);
}
.menu :deep(.el-menu-item.is-active) {
  color: #333;
  background: #ffc300;
}
.menu :deep(.el-menu-item:hover) {
  color: #fff;
  background: #00213d;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.user-info {
  color: #606266;
  font-size: 14px;
}
.main {
  background: #f0f2f5;
}
</style>
