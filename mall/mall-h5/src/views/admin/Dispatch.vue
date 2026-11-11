<template>
  <!-- 派单管理页：策略配置 + 手动触发派单 + 派单记录 / 批次统计 -->
  <div>
    <el-card>
      <template #header>派单管理</template>
      <!-- 策略配置与手动触发 -->
      <div class="dispatch-toolbar">
        <div class="strategy-box">
          <span class="label">当前策略：</span>
          <el-tag type="primary">{{ currentStrategyLabel }}</el-tag>
          <el-select
            v-model="strategy"
            placeholder="切换策略"
            style="width: 180px; margin-left: 12px"
            @change="onStrategyChange"
          >
            <el-option label="A 最近贪心" value="A" />
            <el-option label="B GEO检索" value="B" />
            <el-option label="C 匈牙利批次" value="C" />
          </el-select>
        </div>
        <el-button type="danger" :loading="triggering" @click="onTrigger">手动触发派单</el-button>
      </div>
    </el-card>
    <!-- 记录与批次 -->
    <el-card class="tabs-card">
      <el-tabs v-model="activeTab">
        <!-- 派单记录 -->
        <el-tab-pane label="派单记录" name="records">
          <div class="filter-row">
            <el-select v-model="recordsStrategy" placeholder="按策略筛选" clearable style="width: 180px" @change="onRecordsFilter">
              <el-option label="A 最近贪心" value="A" />
              <el-option label="B GEO检索" value="B" />
              <el-option label="C 匈牙利批次" value="C" />
            </el-select>
          </div>
          <el-table :data="records" border stripe>
            <el-table-column prop="batchNo" label="批次号" width="180" />
            <el-table-column prop="orderNo" label="订单号" width="200" />
            <el-table-column prop="strategy" label="策略" width="80" />
            <el-table-column prop="riderId" label="骑手ID" width="90" />
            <el-table-column prop="candidateCount" label="候选骑手数" width="110" />
            <el-table-column prop="decisionTimeMs" label="决策耗时(ms)" width="120" />
            <el-table-column prop="createTime" label="创建时间">
              <template #default="{ row }">
                {{ (row.createTime || '').toString().slice(0, 19) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="recordsTotal"
              :current-page="recordsPage"
              :page-size="pageSize"
              @current-change="onRecordsPage"
            />
          </div>
        </el-tab-pane>
        <!-- 批次统计 -->
        <el-tab-pane label="批次统计" name="batches">
          <el-table :data="batches" border stripe>
            <el-table-column prop="batchNo" label="批次号" width="180" />
            <el-table-column prop="strategy" label="策略" width="80" />
            <el-table-column prop="orderCount" label="订单数" width="90" />
            <el-table-column prop="riderCount" label="骑手数" width="90" />
            <el-table-column prop="totalEmptyDistance" label="总空驶距离" width="120" />
            <el-table-column prop="dispatchTimeMs" label="派单耗时(ms)" width="120" />
            <el-table-column prop="createTime" label="创建时间">
              <template #default="{ row }">
                {{ (row.createTime || '').toString().slice(0, 19) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="batchesTotal"
              :current-page="batchesPage"
              :page-size="pageSize"
              @current-change="onBatchesPage"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
// 派单管理页：读取/切换派单策略、手动触发派单、查看派单记录与批次统计
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'

// 策略选项
const strategyMap = { A: 'A 最近贪心', B: 'B GEO检索', C: 'C 匈牙利批次' }

const currentStrategy = ref('')
const strategy = ref('')
const triggering = ref(false)
const activeTab = ref('records')

// 派单记录
const records = ref([])
const recordsTotal = ref(0)
const recordsPage = ref(1)
const recordsStrategy = ref('')

// 批次统计
const batches = ref([])
const batchesTotal = ref(0)
const batchesPage = ref(1)

const pageSize = 10

// 当前策略文案
const currentStrategyLabel = computed(() => strategyMap[currentStrategy.value] || currentStrategy.value || '未配置')

// 加载当前策略
const loadConfig = async () => {
  try {
    const data = await api.adminConfig()
    currentStrategy.value = (data && data.strategy) || ''
    strategy.value = currentStrategy.value
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 切换策略
const onStrategyChange = async (val) => {
  try {
    await api.adminConfigPut(val)
    ElMessage.success('策略已切换')
    await loadConfig()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 手动触发派单
const onTrigger = async () => {
  triggering.value = true
  try {
    const data = await api.adminTrigger()
    // 弹窗展示批次派单结果 JSON
    await ElMessageBox.alert(JSON.stringify(data, null, 2), '派单结果', { confirmButtonText: '知道了' })
    loadRecords()
    loadBatches()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    triggering.value = false
  }
}

// 加载派单记录
const loadRecords = async () => {
  try {
    const data = await api.adminRecords({
      strategy: recordsStrategy.value || undefined,
      page: recordsPage.value,
      pageSize
    })
    records.value = (data && data.records) || []
    recordsTotal.value = (data && data.total) || records.value.length
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 加载批次统计
const loadBatches = async () => {
  try {
    const data = await api.adminBatches({ page: batchesPage.value, pageSize })
    batches.value = (data && data.records) || []
    batchesTotal.value = (data && data.total) || batches.value.length
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 派单记录筛选
const onRecordsFilter = () => {
  recordsPage.value = 1
  loadRecords()
}

// 分页事件
const onRecordsPage = (p) => {
  recordsPage.value = p
  loadRecords()
}
const onBatchesPage = (p) => {
  batchesPage.value = p
  loadBatches()
}

onMounted(() => {
  loadConfig()
  loadRecords()
  loadBatches()
})
</script>

<style scoped>
.dispatch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.strategy-box {
  display: flex;
  align-items: center;
}
.strategy-box .label {
  color: #606266;
  font-size: 14px;
}
.tabs-card {
  margin-top: 16px;
}
.filter-row {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
