<template>
  <!-- 商家管理页：展示全部商家，支持切换营业状态 -->
  <div>
    <el-card>
      <template #header>商家管理</template>
      <el-table :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="店铺名称" />
        <el-table-column prop="address" label="地址" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '营业中' : '已打烊' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '设为打烊' : '设为营业' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
// 商家管理页：切换营业状态调用 PUT /api/shop/{id}/status
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const list = ref([])

// 加载全部商家（不分页）
const load = async () => {
  try {
    list.value = (await api.shopList()) || []
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 切换营业状态：1 营业 ↔ 0 打烊
const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await api.shopStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '已设为营业' : '已设为打烊')
    load()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

onMounted(load)
</script>
