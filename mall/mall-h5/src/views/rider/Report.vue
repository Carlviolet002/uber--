<template>
  <!-- 骑手异常上报（P1，前端 mock 提交；保持与工作台一致的黄色风格） -->
  <div class="page">
    <div class="header">
      <div class="header-title">异常上报</div>
      <div class="header-sub">配送中遇到问题可在此反馈</div>
    </div>

    <van-form @submit="onSubmit">
      <van-cell-group inset title="异常类型">
        <van-radio-group v-model="type" direction="horizontal" class="radios">
          <van-radio v-for="t in types" :key="t" :name="t" icon-size="16px">{{ t }}</van-radio>
        </van-radio-group>
      </van-cell-group>

      <van-cell-group inset title="关联订单（可选）">
        <van-field v-model="orderNo" placeholder="输入订单号，可留空" clearable />
      </van-cell-group>

      <van-cell-group inset title="问题描述">
        <van-field
          v-model="desc"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请描述遇到的具体情况"
        />
      </van-cell-group>

      <div class="submit">
        <van-button block round type="primary" class="btn-yellow" native-type="submit" :loading="submitting">
          提交反馈
        </van-button>
      </div>
    </van-form>

    <div class="tip">说明：异常上报为演示功能（未接入后端工单系统），提交后仅本地提示。</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'

const router = useRouter()
const types = ['商家出餐慢', '用户地址/联系异常', '车辆故障', '恶劣天气', '其他']
const type = ref('商家出餐慢')
const orderNo = ref('')
const desc = ref('')
const submitting = ref(false)

const onSubmit = () => {
  submitting.value = true
  // 前端 mock：仅做表单校验与提示（Q1：前端 mock，零后端改动）
  setTimeout(() => {
    submitting.value = false
    showToast('已提交，客服会尽快处理')
    router.back()
  }, 400)
}
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
.header-sub {
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.9;
}
.radios {
  padding: 12px 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
}
.submit {
  padding: 20px 16px 0;
}
.btn-yellow {
  --van-button-primary-background: var(--mt-yellow, #ffc300);
  --van-button-primary-border-color: var(--mt-yellow, #ffc300);
  --van-button-primary-color: #333;
  --van-button-radius: var(--mt-radius-btn, 24px);
}
.tip {
  margin: 16px;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}
</style>
