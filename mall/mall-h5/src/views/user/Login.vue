<template>
  <!-- 用户登录页：普通用户 / 商家 / 骑手 / 管理员共用账号体系，登录后按角色分流 -->
  <div class="page">
    <van-nav-bar title="用户登录" />
    <div class="banner">
      <div class="banner-title">校园即时配送平台</div>
      <div class="banner-sub">点外卖 · 找骑手 · 一站式配送</div>
    </div>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="请输入用户名"
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
    <div class="link">
      <router-link to="/portal">没有账号？去注册</router-link>
    </div>
  </div>
</template>

<script setup>
// 用户登录页：登录成功后按 role 分流到对应端
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

// 提交登录表单
const onSubmit = async () => {
  loading.value = true
  try {
    const data = await userStore.loginAction(form)
    showToast('登录成功')
    // 按角色跳转：USER 用户端 / SHOP 提示走管理端 / RIDER 骑手端 / ADMIN 管理端
    if (data.role === 'USER') {
      router.push('/user/home')
    } else if (data.role === 'SHOP') {
      router.push('/shop/home')
    } else if (data.role === 'RIDER') {
      router.push('/rider/login')
    } else if (data.role === 'ADMIN') {
      router.push('/admin')
    } else {
      router.push('/user/home')
    }
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
  background: linear-gradient(135deg, #1989fa, #39a9ff);
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
.link {
  text-align: center;
  padding: 12px 0 32px;
}
.link a {
  color: #1989fa;
  font-size: 14px;
  text-decoration: none;
}
</style>
