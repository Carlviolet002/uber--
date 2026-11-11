<template>
  <!-- 订单监控页：状态筛选 + 分页展示全部订单（ADMIN 视角） -->
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单监控</span>
          <div class="filters">
            <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px" @change="onFilterChange">
              <el-option label="待支付" :value="1" />
              <el-option label="待接单" :value="2" />
              <el-option label="派单中" :value="3" />
              <el-option label="待取餐" :value="4" />
              <el-option label="配送中" :value="5" />
              <el-option label="已完成" :value="6" />
            </el-select>
            <el-checkbox v-model="urgeOnly" @change="onFilterChange">仅看催单</el-checkbox>
          </div>
        </div>
      </template>
      <el-table :data="list" border stripe>
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="shopId" label="商家ID" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="total" label="订单金额" width="110">
          <template #default="{ row }">¥{{ Number(row.total).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间">
          <template #default="{ row }">
            {{ (row.createdAt || '').toString().slice(0, 19) }}
          </template>
        </el-table-column>
        <el-table-column prop="riderId" label="骑手ID" width="90" />
      </el-table>
      <div class="pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="pageSize"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
// 订单监控页：按状态筛选 + 分页（ADMIN 调用订单列表接口返回全部订单）
import { ref, onMounted } from 'vue'
import api from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref('') // 空字符串表示全部
const urgeOnly = ref(false) // P1-16 仅看催单开关

// 状态 tag 颜色映射：1 待支付(warning) 2/3/4 (primary) 5 (warning) 6 已完成(success)
const statusTagType = (s) => {
  if (Number(s) === 1) return 'warning'
  if (Number(s) === 5) return 'warning'
  if (Number(s) === 6) return 'success'
  return 'primary'
}

// 加载订单列表
const load = async () => {
  try {
    const data = await api.orderList({
      status: status.value || undefined,
      urgeFlag: urgeOnly.value ? 1 : undefined, // P1-16 仅看催单
      page: page.value,
      pageSize: pageSize.value
    })
    list.value = (data && data.records) || []
    total.value = (data && data.total) || list.value.length
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 筛选条件变化时回到第一页
const onFilterChange = () => {
  page.value = 1
  load()
}

// 翻页
const onPageChange = (p) => {
  page.value = p
  load()
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
