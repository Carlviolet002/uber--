<template>
  <!-- 骑手登录页：调用派单模块的骑手登录接口 -->
  <div class="page">
    <van-nav-bar title="骑手登录" />
    <div class="banner">
      <div class="banner-title">骑手工作台</div>
      <div class="banner-sub">接单配送，使命必达</div>
    </div>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="请输入骑手账号"
          :rules="[{ required: true, message: '请填写用户名' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          :rules="[{ required: true, message: '请填写密码' }]"
        />
      </van-cell-group>
      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">登 录</van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
// 骑手登录页：登录成功后保存登录态并跳转骑手工作台
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import api from '../../api'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

// 提交骑手登录
const onSubmit = async () => {
  loading.value = true
  try {
    const data = await api.riderLogin(form)
    if (data.role !== 'RIDER') {
      showToast('该账号不是骑手账号')
      return
    }
    // 与用户端共用登录态存储
    userStore.setLogin({ token: data.token, userInfo: data })
    showToast('登录成功')
    router.push('/rider/tasks')
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f7f8fa;
}
.banner {
  padding: 32px 16px;
  background: linear-gradient(135deg, #07c160, #10d070);
  color: #fff;
  margin-bottom: 16px;
}
.banner-title {
  font-size: 22px;
  font-weight: 600;
}
.banner-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.9;
}
.submit {
  margin: 24px 16px;
}
</style>
