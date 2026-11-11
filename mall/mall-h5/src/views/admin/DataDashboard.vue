<template>
  <!-- P1-18 数据看板：GMV / 订单数 / 已支付数 / 送达率 / 超时率 / 今日订单数 -->
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>运营数据看板</span>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-card shadow="hover" class="stat">
            <div class="stat-label">GMV（成交总额）</div>
            <div class="stat-value money">¥{{ formatMoney(stats.gmv) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat">
            <div class="stat-label">订单总数</div>
            <div class="stat-value">{{ stats.orderCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat">
            <div class="stat-label">今日订单数</div>
            <div class="stat-value">{{ stats.todayOrderCount }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="8">
          <el-card shadow="hover" class="stat">
            <div class="stat-label">已支付订单数</div>
            <div class="stat-value">{{ stats.paidCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat">
            <div class="stat-label">已完成订单数</div>
            <div class="stat-value">{{ stats.completedCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat">
            <div class="stat-label">送达率</div>
            <div class="stat-value rate">{{ formatRate(stats.deliveredRate) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="8">
          <el-card shadow="hover" class="stat">
            <div class="stat-label">超时率</div>
            <div class="stat-value danger">{{ formatRate(stats.timeoutRate) }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
// P1-18 数据看板：调用 /api/order/admin/stats（需 ADMIN 角色，由网关注入 X-User-Roles）
import { ref, onMounted } from 'vue'
import api from '../../api'

const loading = ref(false)
const stats = ref({
  gmv: 0,
  orderCount: 0,
  paidCount: 0,
  todayOrderCount: 0,
  completedCount: 0,
  deliveredRate: 0,
  timeoutRate: 0
})

const load = async () => {
  loading.value = true
  try {
    const data = await api.adminStats()
    if (data) stats.value = data
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

const formatMoney = (v) => {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatRate = (v) => {
  const n = Number(v || 0) * 100
  return n.toFixed(1) + '%'
}

onMounted(load)
</script>

<style scoped>
.page-card { margin: 16px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat { text-align: center; }
.stat-label { color: #909399; font-size: 13px; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 600; color: #303133; }
.stat-value.money { color: #f56c6c; }
.stat-value.rate { color: #67c23a; }
.stat-value.danger { color: #e6a23c; }
</style>
