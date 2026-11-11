<template>
  <!-- 评价管理：店铺评价列表 + 商家回复（后端接口已就绪） -->
  <div class="page">
    <van-nav-bar title="评价管理" left-arrow @click-left="router.back()" />
    <van-skeleton v-if="loading" title :row="4" style="margin: 12px" />
    <van-empty v-else-if="reviews.length === 0" description="暂无评价，用心做菜总会收获好评～" />
    <div v-else v-for="r in reviews" :key="r.id" class="review-card">
      <div class="review-head">
        <span class="review-user">{{ r.userNickname || '匿名用户' }}</span>
        <div class="review-stars">
          <span v-for="i in 5" :key="i" :class="['star', i <= (r.shopScoreOverall || 5) ? 'on' : '']">★</span>
        </div>
        <span class="review-time">{{ fmtTime(r.createTime) }}</span>
      </div>
      <div class="review-content">{{ r.content || '（无文字评价）' }}</div>
      <div class="review-meta">
        <van-tag plain type="warning">口味 {{ r.shopScoreTaste || 5 }}</van-tag>
        <van-tag plain type="primary">包装 {{ r.shopScorePack || 5 }}</van-tag>
        <span v-if="r.orderNo" class="review-order">订单 {{ r.orderNo }}</span>
      </div>
      <!-- 商家回复区：不展示 reply_time，该列与平台回复共用会被覆盖 -->
      <div v-if="r.shopReply" class="reply-box">
        <div class="reply-label">商家回复</div>
        <div class="reply-text">{{ r.shopReply }}</div>
        <div class="reply-edit" @click="openReply(r)">修改回复</div>
      </div>
      <div v-else class="reply-btn" @click="openReply(r)">回复</div>
      <!-- 平台回复（admin 端写入）：独立展示，区别于商家回复 -->
      <div v-if="r.platformReply" class="platform-reply-box">
        <div class="reply-label">平台回复</div>
        <div class="reply-text">{{ r.platformReply }}</div>
      </div>
    </div>

    <!-- 回复弹窗 -->
    <van-dialog
      v-model:show="showReply"
      title="回复评价"
      show-cancel-button
      :before-close="onReplyConfirm"
    >
      <div class="dialog-body">
        <van-field
          v-model="replyText"
          type="textarea"
          rows="3"
          autosize
          maxlength="100"
          show-word-limit
          placeholder="感谢惠顾，欢迎再次光临～"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
// 评价管理：GET /api/shop/{shopId}/reviews 拉取，PUT /api/shop/review/{id}/reply 回复
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useShopStatsStore } from '../../stores/shopStats'
import { getShopReviews, replyReview } from '@/api'

const router = useRouter()
const stats = useShopStatsStore()

const loading = ref(true)
const reviews = ref([])
const showReply = ref(false)
const replyText = ref('')
const replyingId = ref(null)

const fmtTime = (t) => (t ? String(t).slice(0, 19).replace('T', ' ') : '')

const openReply = (r) => {
  replyingId.value = r.id
  replyText.value = r.shopReply || ''
  showReply.value = true
}

// 确认回复（before-close 拦截确认动作）
const onReplyConfirm = async (action) => {
  if (action !== 'confirm') return true
  if (!replyText.value.trim()) {
    showToast('回复内容不能为空')
    return false
  }
  try {
    await replyReview(replyingId.value, replyText.value.trim())
    showToast('回复成功')
    await load()
    return true
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
    return false
  }
}

const load = async () => {
  try {
    const data = await getShopReviews(await stats.ensureShopId())
    reviews.value = Array.isArray(data) ? data : (data && data.records) || []
  } catch (e) {
    // 错误提示已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--mt-bg, #f5f5f5);
  padding-bottom: 70px;
}
.review-card {
  margin: 8px 12px;
  padding: 14px;
  background: #fff;
  border-radius: var(--mt-radius, 16px);
}
.review-head {
  display: flex;
  align-items: center;
}
.review-user {
  font-size: 14px;
  font-weight: 600;
  color: var(--mt-text, #222);
}
.review-stars {
  margin-left: 8px;
}
.star {
  color: #e5e5e5;
  font-size: 13px;
}
.star.on {
  color: var(--mt-yellow, #ffc300);
}
.review-time {
  margin-left: auto;
  font-size: 11px;
  color: #c8c9cc;
}
.review-content {
  margin-top: 8px;
  font-size: 14px;
  line-height: 21px;
  color: var(--mt-text, #222);
}
.review-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.review-order {
  font-size: 11px;
  color: #c8c9cc;
}
.reply-box {
  margin-top: 10px;
  padding: 10px;
  background: #f7f8fa;
  border-radius: 10px;
}
.reply-label {
  font-size: 11px;
  color: var(--mt-text-3, #999);
}
.reply-text {
  margin-top: 4px;
  font-size: 13px;
  color: var(--mt-text-2, #666);
}
.reply-edit {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mt-yellow, #ffc300);
  text-align: right;
  cursor: pointer;
}
/* 平台回复：蓝底左侧色条，与灰底商家回复区分 */
.platform-reply-box {
  margin-top: 10px;
  padding: 10px;
  background: #ecf5ff;
  border-left: 3px solid #1989fa;
  border-radius: 10px;
}
.reply-btn {
  margin-top: 10px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: #333;
  background: var(--mt-yellow, #ffc300);
  border-radius: var(--mt-radius-btn, 24px);
  padding: 7px 0;
  cursor: pointer;
}
.dialog-body {
  padding: 8px 16px 16px;
}
</style>
