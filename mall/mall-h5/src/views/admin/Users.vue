<template>
  <!-- 用户管理页：分页展示全部注册用户 -->
  <div>
    <el-card>
      <template #header>用户管理</template>
      <el-table :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间">
          <template #default="{ row }">
            {{ (row.createTime || '').toString().slice(0, 19) }}
          </template>
        </el-table-column>
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
// 用户管理页：调用 /api/user/list 分页查询
import { ref, onMounted } from 'vue'
import api from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

// 加载用户列表
const load = async () => {
  try {
    const data = await api.userList({ page: page.value, pageSize: pageSize.value })
    // 兼容后端返回 {records,total} 或直接返回数组
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
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
