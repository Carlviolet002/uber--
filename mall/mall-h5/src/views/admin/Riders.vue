<template>
  <!-- 骑手管理页：仅查看与管理骑手；新增骑手已收归门户自助注册（RIDER 角色） -->
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>骑手管理</span>
          <span class="tip">骑手账号创建已统一至门户自助注册（勾选 RIDER 角色），本页仅用于查看与管理</span>
        </div>
      </template>
      <el-table :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentLng" label="当前经度" width="120" />
        <el-table-column prop="currentLat" label="当前纬度" width="120" />
        <el-table-column prop="capacity" label="容量" width="90" />
        <el-table-column prop="acceptCount" label="接单数" width="90" />
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
// 骑手管理页：状态 0 休息 / 1 在线 / 2 配送中
// 注：新增骑手入口已收敛至门户自助注册（RIDER 角色），本页仅保留列表查看与翻页
import { ref, onMounted } from 'vue'
import api from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

// 状态文案
const statusText = (s) => (s === 1 ? '在线' : s === 2 ? '配送中' : '休息')
// 状态 tag 颜色
const statusType = (s) => (s === 1 ? 'success' : s === 2 ? 'warning' : 'info')

// 加载骑手列表
const load = async () => {
  try {
    const data = await api.adminRiders({ page: page.value, pageSize: pageSize.value })
    list.value = (data && data.records) || data || []
    total.value = (data && data.total) || list.value.length
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
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
  gap: 12px;
}
.tip {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
