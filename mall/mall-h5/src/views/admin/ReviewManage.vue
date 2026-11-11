<template>
  <!-- P1-17 评价管理：跨店评价分页列表 + 平台审核（通过/下架）+ 平台回复 -->
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>评价管理</span>
          <div class="filters">
            <el-input v-model="keyword" placeholder="内容关键词" clearable style="width: 160px" @keyup.enter="onSearch" />
            <el-input v-model="shopId" placeholder="店铺ID" clearable style="width: 110px" @keyup.enter="onSearch" />
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="shopId" label="店铺ID" width="90" />
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column label="综合评分" width="90">
          <template #default="{ row }">{{ row.shopScoreOverall }}.0</template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditTagType(row.auditStatus)">
              {{ auditText(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商家回复" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.shopReply">{{ row.shopReply }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="平台回复" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.platformReply">{{ row.platformReply }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <!-- 按两列是否各自有值判断，不再依赖会被覆盖的 replyBy 单列 -->
        <el-table-column label="回复来源" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.shopReply" type="info" size="small">商家</el-tag>
            <el-tag v-if="row.platformReply" type="warning" size="small" style="margin-left:4px">平台</el-tag>
            <span v-if="!row.shopReply && !row.platformReply" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="onAudit(row, 1)">通过</el-button>
            <el-button size="small" type="danger" @click="onAudit(row, 2)">下架</el-button>
            <el-button size="small" @click="openReply(row)">平台回复</el-button>
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

    <!-- 平台回复对话框 -->
    <el-dialog v-model="replyVisible" title="平台回复" width="480px">
      <el-input
        v-model="replyContent"
        type="textarea"
        :rows="4"
        placeholder="请输入平台回复内容"
        maxlength="512"
        show-word-limit
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replyLoading" @click="submitReply">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// P1-17 评价管理：分页列表 / 平台审核 / 平台回复（区别于商家回复）
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const shopId = ref('')
const loading = ref(false)

// 平台回复弹窗
const replyVisible = ref(false)
const replyLoading = ref(false)
const replyContent = ref('')
const replyTargetId = ref(null)

const auditText = (s) => {
  if (Number(s) === 2) return '已下架'
  if (Number(s) === 1) return '已通过'
  return '待审核'
}
const auditTagType = (s) => {
  if (Number(s) === 2) return 'danger'
  if (Number(s) === 1) return 'success'
  return 'info'
}

const load = async () => {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (shopId.value) params.shopId = shopId.value
    const data = await api.adminReviews(params)
    list.value = (data && data.records) || []
    total.value = (data && data.total) || list.value.length
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  page.value = 1
  load()
}
const reset = () => {
  keyword.value = ''
  shopId.value = ''
  page.value = 1
  load()
}
const onPageChange = (p) => {
  page.value = p
  load()
}

// 平台审核：auditStatus 1 通过 / 2 下架
const onAudit = async (row, auditStatus) => {
  try {
    await api.auditReview(row.id, auditStatus)
    ElMessage.success(auditStatus === 1 ? '已通过' : '已下架')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

// 平台回复
const openReply = (row) => {
  replyTargetId.value = row.id
  replyContent.value = row.platformReply || ''
  replyVisible.value = true
}
const submitReply = async () => {
  if (!replyContent.value || !replyContent.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  replyLoading.value = true
  try {
    await api.platformReplyReview(replyTargetId.value, replyContent.value.trim())
    ElMessage.success('平台回复成功')
    replyVisible.value = false
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    replyLoading.value = false
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
.filters { display: flex; gap: 8px; }
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.muted { color: #c0c4cc; }
</style>
