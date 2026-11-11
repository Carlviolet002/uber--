<template>
  <!-- 管理员登录页：复用用户登录接口，校验角色必须为 ADMIN -->
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>
        <div class="login-title">校园即时配送平台 · 管理端</div>
      </template>
      <el-form :model="form" label-width="70px" @keyup.enter="onSubmit">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
// 管理员登录页：登录成功后校验角色为 ADMIN，否则提示并拒绝进入
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

// 提交登录
const onSubmit = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await userStore.loginAction(form)
    if (data.role !== 'ADMIN') {
      ElMessage.error('该账号不是管理员账号')
      userStore.logout()
      return
    }
    ElMessage.success('登录成功')
    router.push('/admin/orders')
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}
.login-card {
  width: 380px;
}
.login-title {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}
</style>
