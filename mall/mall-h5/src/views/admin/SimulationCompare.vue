<template>
  <!-- P3 策略对比：读取 mall-sim 离线仿真产物 sim-result.csv，展示 A/B/C 三策略指标 -->
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>派单策略仿真对比（A/B/C）</span>
          <div class="actions">
            <span v-if="generatedAt" class="muted">数据生成时间：{{ generatedAt }}</span>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="empty"
        type="info"
        :closable="false"
        title="尚未生成仿真数据"
        :description="message || '请先运行 mall-sim 离线仿真模块生成 sim-result.csv'"
        style="margin-bottom: 16px"
      />

      <template v-else>
        <!-- 三策略汇总对比 -->
        <el-table :data="summary" border stripe class="summary-table">
          <el-table-column prop="strategy" label="策略" width="100">
            <template #default="{ row }">
              <el-tag :type="strategyTag(row.strategy)">{{ row.strategy }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="runs" label="仿真组数" width="100" />
          <el-table-column label="平均配送时长(分)" min-width="140">
            <template #default="{ row }">{{ row.avgDeliveryMin.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="平均超时率" min-width="120">
            <template #default="{ row }">{{ (row.avgTimeoutRate * 100).toFixed(1) }}%</template>
          </el-table-column>
          <el-table-column label="平均空驶里程(km)" min-width="140">
            <template #default="{ row }">{{ row.avgEmptyKm.toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <el-divider>原始仿真明细</el-divider>

        <!-- 原始明细：不同骑手数 / lambda 组合 -->
        <el-table :data="rows" border stripe>
          <el-table-column label="策略" width="90">
            <template #default="{ row }">
              <el-tag :type="strategyTag(row.strategy)">{{ row.strategy }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="riderCount" label="骑手数" width="90" />
          <el-table-column prop="lambda" label="到达率λ" width="90" />
          <el-table-column prop="orderCount" label="订单数" width="90" />
          <el-table-column prop="avgDeliveryMin" label="平均配送(分)" width="120" />
          <el-table-column label="超时率" width="100">
            <template #default="{ row }">{{ (row.timeoutRate * 100).toFixed(1) }}%</template>
          </el-table-column>
          <el-table-column prop="emptyDistanceKm" label="空驶里程(km)" width="130" />
          <el-table-column prop="avgDecisionMs" label="决策耗时(ms)" width="120" />
        </el-table>
      </template>
    </el-card>
  </div>
</template>

<script setup>
// P3 策略对比：调用 /api/dispatch/admin/sim-compare（需 ADMIN 角色）
import { ref, computed, onMounted } from 'vue'
import api from '../../api'

const loading = ref(false)
const rows = ref([])
const empty = ref(false)
const message = ref('')
const generatedAt = ref('')

// 按策略汇总：平均配送时长 / 超时率 / 空驶里程
const summary = computed(() => {
  const groups = {}
  for (const r of rows.value) {
    if (!groups[r.strategy]) groups[r.strategy] = { strategy: r.strategy, runs: 0, sumDelivery: 0, sumTimeout: 0, sumEmpty: 0 }
    const g = groups[r.strategy]
    g.runs++
    g.sumDelivery += Number(r.avgDeliveryMin) || 0
    g.sumTimeout += Number(r.timeoutRate) || 0
    g.sumEmpty += Number(r.emptyDistanceKm) || 0
  }
  return Object.values(groups).map((g) => ({
    strategy: g.strategy,
    runs: g.runs,
    avgDeliveryMin: g.sumDelivery / g.runs,
    avgTimeoutRate: g.sumTimeout / g.runs,
    avgEmptyKm: g.sumEmpty / g.runs
  }))
})

const strategyTag = (s) => {
  if (s === 'A') return 'primary'
  if (s === 'B') return 'success'
  if (s === 'C') return 'warning'
  return 'info'
}

const load = async () => {
  loading.value = true
  try {
    const data = await api.simCompare()
    if (!data) {
      empty.value = true
      return
    }
    empty.value = !!data.empty
    message.value = data.message || ''
    generatedAt.value = data.generatedAt || ''
    rows.value = data.rows || []
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-card { margin: 16px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.actions { display: flex; align-items: center; gap: 12px; }
.summary-table { margin-bottom: 8px; }
.muted { color: #909399; font-size: 13px; }
</style>
