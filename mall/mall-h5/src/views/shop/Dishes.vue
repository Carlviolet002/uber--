<template>
  <!-- 菜品管理（SHOP）：本店菜品列表 + 沽清/恢复切换 + 新增菜品弹窗 -->
  <div class="page">
    <!-- 顶部白底标题栏（商家版浅色系，带返回） -->
    <div class="header">
      <van-icon name="arrow-left" class="back-icon" @click="router.back()" />
      <div class="header-title">菜品管理</div>
    </div>

    <!-- 菜品列表 -->
    <van-empty v-if="dishList.length === 0" description="暂无菜品，点击下方按钮新增" />
    <div v-for="d in dishList" :key="d.id" class="dish-card">
      <div class="dish-info">
        <div class="dish-name">{{ d.name }}</div>
        <div class="dish-meta">
          <van-tag plain type="primary">{{ categoryName(d.categoryId) }}</van-tag>
          <van-tag :type="d.status === 1 ? 'success' : 'default'" class="dish-status">
            {{ d.status === 1 ? '在售' : '沽清' }}
          </van-tag>
        </div>
        <div class="dish-price">¥{{ Number(d.price || 0).toFixed(2) }}</div>
      </div>
      <van-button
        size="small"
        :class="d.status === 1 ? 'btn-gray' : 'btn-yellow'"
        :type="d.status === 1 ? 'default' : 'primary'"
        :loading="actingId === d.id"
        @click="onToggleSoldOut(d)"
      >
        {{ d.status === 1 ? '设为沽清' : '恢复在售' }}
      </van-button>
    </div>

    <!-- 底部新增按钮 -->
    <div class="bottom-bar">
      <van-button round block type="primary" class="btn-yellow" @click="openAdd">＋ 新增菜品</van-button>
    </div>

    <!-- 新增菜品弹窗 -->
    <van-popup v-model:show="showAdd" position="bottom" round :style="{ padding: '16px 16px 24px' }">
      <div class="pop-title">新增菜品</div>
      <van-field v-model="form.name" label="名称" placeholder="请输入菜品名称" />
      <van-field v-model="form.price" type="number" label="价格" placeholder="请输入价格（元）" />
      <van-field
        v-model="form.categoryName"
        label="分类"
        is-link
        readonly
        placeholder="请选择分类"
        @click="showCategory = true"
      />
      <van-field
        v-model="form.description"
        type="textarea"
        rows="2"
        autosize
        label="描述"
        maxlength="100"
        placeholder="菜品描述（选填）"
      />
      <div class="pop-ops">
        <van-button round block type="primary" class="btn-yellow" :loading="adding" @click="onAdd">
          确认新增
        </van-button>
      </div>
    </van-popup>

    <!-- 分类选择器 -->
    <van-popup v-model:show="showCategory" position="bottom" round>
      <van-picker
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategory = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
// 菜品管理：加载本店菜品与分类，支持沽清/恢复（setSoldOut）与新增菜品（dishAdd）
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '../../stores/user'
import { dishes, categories, dishAdd, setSoldOut, shopList } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const nickname = computed(
  () => userStore.userInfo.nickname || userStore.userInfo.username || '商家'
)

const shopId = ref(null)
const dishList = ref([])
const categoryList = ref([])
const showAdd = ref(false)
const showCategory = ref(false)
const adding = ref(false)
const actingId = ref(null) // 正在切换沽清状态的菜品 id
const form = ref({ name: '', price: '', categoryId: null, categoryName: '', description: '' })

/**
 * 商家身份获取：与 Workbench 相同方案。
 * 种子数据 shop1 账号 userId=3、shopId=1，昵称"张姐麻辣烫"与店铺名一致：
 * 优先登录信息 shopId → 昵称匹配店铺名 → userId-2 兜底 → 1。
 */
const resolveShopId = async () => {
  const info = userStore.userInfo || {}
  if (info.shopId) return Number(info.shopId)
  try {
    const shops = (await shopList()) || []
    const hit = shops.find((s) => s.name === info.nickname)
    if (hit) return Number(hit.id)
  } catch (e) {
    // 列表失败走兜底逻辑
  }
  const uid = Number(info.userId ?? info.id)
  if (uid) return uid - 2
  return 1
}

// 分类名映射
const categoryName = (id) => {
  const c = categoryList.value.find((x) => Number(x.id) === Number(id))
  return (c && c.name) || '未分类'
}

// 分类选择器列：对象数组 {text, value}；空时兜底"默认分类"（TODO：后端支持自定义分类后可移除）
const categoryColumns = computed(() => {
  if (categoryList.value.length === 0) return [{ text: '默认分类', value: null }]
  return categoryList.value.map((c) => ({ text: c.name, value: c.id }))
})

// 加载菜品与分类
const load = async () => {
  try {
    dishList.value = (await dishes(shopId.value)) || []
    categoryList.value = (await categories(shopId.value)) || []
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  }
}

// 沽清/恢复切换：soldOut 1=沽清 0=恢复
const onToggleSoldOut = async (d) => {
  actingId.value = d.id
  try {
    await setSoldOut(d.id, d.status === 1 ? 1 : 0)
    showToast(d.status === 1 ? '已设为沽清' : '已恢复在售')
    await load()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    actingId.value = null
  }
}

// 打开新增弹窗并重置表单
const openAdd = () => {
  form.value = { name: '', price: '', categoryId: null, categoryName: '', description: '' }
  showAdd.value = true
}

// 分类选择确认
const onCategoryConfirm = ({ selectedOptions }) => {
  const opt = selectedOptions && selectedOptions[0]
  if (opt) {
    form.value.categoryId = opt.value
    form.value.categoryName = opt.text
  }
  showCategory.value = false
}

// 新增菜品
const onAdd = async () => {
  if (!form.value.name) {
    showToast('请填写菜品名称')
    return
  }
  if (!form.value.price || Number(form.value.price) <= 0) {
    showToast('请填写正确的价格')
    return
  }
  if (form.value.categoryId == null) {
    showToast('请选择分类')
    return
  }
  adding.value = true
  try {
    await dishAdd({
      shopId: shopId.value,
      categoryId: form.value.categoryId,
      name: form.value.name,
      price: Number(form.value.price),
      // 后端 DishDTO 暂无 description 字段，多传会被 Jackson 忽略，为后续扩展保留
      description: form.value.description,
      image: '',
      status: 1
    })
    showToast('新增成功')
    showAdd.value = false
    await load()
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    adding.value = false
  }
}

onMounted(async () => {
  shopId.value = await resolveShopId()
  await load()
})
</script>

<style scoped>
.page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
  padding-bottom: 90px;
}
.header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  z-index: 99;
}
.back-icon {
  font-size: 18px;
  color: var(--mt-text, #222);
  margin-right: 8px;
}
.header-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.dish-card {
  margin: 8px 12px;
  padding: 12px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dish-info {
  flex: 1;
  padding-right: 12px;
}
.dish-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.dish-meta {
  margin-top: 6px;
}
.dish-status {
  margin-left: 6px;
}
.dish-price {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--mt-price, #ff4d26);
}
.bottom-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  /* 底部 5 Tab 导航高 50px，新增按钮上移避免遮挡 */
  bottom: 50px;
  width: 100%;
  max-width: 750px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-sizing: border-box;
}
.pop-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--mt-text, #222);
  margin-bottom: 8px;
  text-align: center;
}
.pop-ops {
  margin-top: 16px;
}
/* 黄底胶囊按钮：覆盖 Vant 主色为美团黄，深色文字保证对比度 */
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
