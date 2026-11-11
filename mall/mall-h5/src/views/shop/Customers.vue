<template>
  <!-- 顾客管理：顾客列表（shopMock 假数据）+ 搜索 + 标记/投诉/限制下单 -->
  <div class="page">
    <van-nav-bar title="顾客管理" left-arrow @click-left="router.back()" />

    <!-- 搜索框 -->
    <div class="search-wrap">
      <van-search v-model="keyword" placeholder="按昵称 / 手机尾号搜索" shape="round" />
    </div>

    <van-skeleton v-if="loading" title :row="4" style="margin: 12px" />
    <van-empty v-else-if="filtered.length === 0" description="没有找到相关顾客" />
    <div v-else v-for="c in filtered" :key="c.id" class="customer-card">
      <div class="customer-head">
        <div class="customer-avatar">{{ c.nick.slice(0, 1) }}</div>
        <div class="customer-info">
          <div class="customer-name">
            {{ c.nick }}
            <van-tag v-if="c.marked" plain type="warning">重要顾客</van-tag>
            <van-tag v-if="c.blocked" plain type="danger">已限制下单</van-tag>
          </div>
          <div class="customer-meta">尾号 {{ c.phoneTail }} · {{ c.orderCount }} 单 · 累计 ¥{{ c.totalSpend.toFixed(2) }}</div>
          <div class="customer-meta">最近下单：{{ c.lastTime }}</div>
        </div>
      </div>
      <div class="customer-ops">
        <van-button size="mini" :class="c.marked ? 'btn-gray' : 'btn-yellow'" :type="c.marked ? 'default' : 'primary'" @click="onToggleMark(c)">
          {{ c.marked ? '取消标记' : '标记' }}
        </van-button>
        <van-button size="mini" class="btn-gray" type="default" @click="onComplain(c)">投诉</van-button>
        <van-button size="mini" :class="c.blocked ? 'btn-yellow' : 'btn-gray'" :type="c.blocked ? 'primary' : 'default'" @click="onToggleBlock(c)">
          {{ c.blocked ? '解除限制' : '限制下单' }}
        </van-button>
      </div>
    </div>

    <!-- 投诉弹窗 -->
    <van-action-sheet
      v-model:show="showComplain"
      :actions="complainActions"
      cancel-text="取消"
      description="选择投诉类型（演示）"
      @select="onComplainSelect"
    />
  </div>
</template>

<script setup>
// 顾客管理：shopMock 假数据 + localStorage 持久化标记/限制状态；TODO 接 GET /api/shop/customers
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getCustomers, updateCustomer } from '../../mock/shopMock'

const router = useRouter()

const loading = ref(true)
const keyword = ref('')
const customers = ref([])
const showComplain = ref(false)
const complainTarget = ref(null)

const complainActions = [
  { name: '配送慢' },
  { name: '餐品问题' },
  { name: '服务态度' },
  { name: '恶意差评' }
]

// 搜索过滤
const filtered = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return customers.value
  return customers.value.filter(
    (c) => c.nick.includes(kw) || c.phoneTail.includes(kw)
  )
})

// 标记/取消标记
const onToggleMark = (c) => {
  const list = updateCustomer(c.id, { marked: !c.marked })
  customers.value = list
  showToast(c.marked ? '已取消标记' : '已标记为重要顾客')
}

// 投诉
const onComplain = (c) => {
  complainTarget.value = c
  showComplain.value = true
}

const onComplainSelect = (action) => {
  showToast(`已记录投诉：${action.name}（演示）`)
  showComplain.value = false
}

// 限制下单/解除
const onToggleBlock = async (c) => {
  if (!c.blocked) {
    try {
      await showConfirmDialog({ title: '限制下单', message: `确认限制顾客「${c.nick}」下单吗？` })
    } catch (e) {
      return
    }
  }
  const list = updateCustomer(c.id, { blocked: !c.blocked })
  customers.value = list
  showToast(c.blocked ? '已解除限制' : '已限制该顾客下单')
}

onMounted(async () => {
  try {
    customers.value = await getCustomers()
  } catch (e) {
    // mock 层无网络异常，此处仅兜底
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
  padding-bottom: 70px;
}
.search-wrap {
  background: #fff;
}
.customer-card {
  margin: 8px 12px;
  padding: 12px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.customer-head {
  display: flex;
  align-items: center;
}
.customer-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #fff3cc;
  color: #b8860b;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.customer-info {
  margin-left: 10px;
  flex: 1;
  min-width: 0;
}
.customer-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--mt-text, #222);
  display: flex;
  align-items: center;
  gap: 6px;
}
.customer-meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--mt-text-3, #999);
}
.customer-ops {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.btn-yellow {
  --van-button-primary-background: var(--mt-yellow, #ffc300);
  --van-button-primary-border-color: var(--mt-yellow, #ffc300);
  --van-button-primary-color: #333;
  --van-button-radius: var(--mt-radius-btn, 24px);
}
.btn-gray {
  --van-button-default-background: #f2f3f5;
  --van-button-default-border-color: #f2f3f5;
  --van-button-default-color: #646566;
  --van-button-radius: var(--mt-radius-btn, 24px);
}
</style>
