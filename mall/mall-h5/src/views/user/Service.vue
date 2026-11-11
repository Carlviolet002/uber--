<template>
  <!-- 客服中心：服务热线 + FAQ（搜索过滤）+ 在线客服 mock 对话 -->
  <div class="service-page">
    <van-nav-bar title="客服中心" left-arrow fixed placeholder @click-left="router.back()" />
    <!-- 服务热线卡 -->
    <div class="hotline-card">
      <div class="hotline-emoji">📞</div>
      <div class="hotline-info">
        <div class="hotline-title">美团外卖客服热线</div>
        <div class="hotline-num">400-800-0000</div>
        <div class="hotline-time">服务时间 9:00-21:00</div>
      </div>
      <a class="hotline-call" href="tel:4008000000">拨打</a>
    </div>
    <!-- 在线客服入口 -->
    <div class="online-card" @click="chatShow = true">
      <div class="online-emoji">💬</div>
      <div class="online-info">
        <div class="online-title">在线客服</div>
        <div class="online-sub">智能客服小美 7×24 小时在线</div>
      </div>
      <van-icon name="arrow" class="online-arrow" />
    </div>
    <!-- 常见问题 -->
    <div class="faq-section">
      <div class="faq-head">
        <span class="faq-title">常见问题</span>
        <div class="faq-search">
          <van-icon name="search" />
          <input v-model="faqKw" class="faq-input" placeholder="搜索问题" @input="debouncedFilter" />
        </div>
      </div>
      <van-skeleton v-if="loading" title :row="3" class="faq-skeleton" />
      <van-collapse v-else v-model="expanded" class="faq-collapse">
        <van-collapse-item v-for="(f, i) in filteredFaq" :key="i" :title="f.q" :name="i">
          <div class="faq-text">{{ f.a }}</div>
        </van-collapse-item>
      </van-collapse>
      <BaseEmpty v-if="!loading && !filteredFaq.length" description="没有找到相关问题，试试其他关键词" />
    </div>

    <!-- 在线客服对话弹层（mock 自动回复） -->
    <van-popup v-model:show="chatShow" position="bottom" round class="chat-popup">
      <div class="chat-head">
        <span class="chat-title">在线客服</span>
        <span class="chat-status">智能客服小美</span>
        <van-icon name="cross" class="chat-close" @click="chatShow = false" />
      </div>
      <div ref="chatBodyRef" class="chat-body">
        <div v-for="(m, i) in chatMsgs" :key="i" class="chat-msg" :class="m.from">
          <div class="chat-bubble">{{ m.text }}</div>
        </div>
        <div v-if="chatTyping" class="chat-msg bot">
          <div class="chat-bubble">正在输入...</div>
        </div>
      </div>
      <div class="chat-input-bar">
        <input
          v-model="chatInput"
          class="chat-input"
          placeholder="描述你的问题，如：退款 / 催单 / 优惠券"
          @keyup.enter="onChatSend"
        />
        <div class="chat-send" @click="onChatSend">发送</div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
// 客服中心：FAQ 与自动回复走 userMock；对话历史 localStorage 持久化
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import BaseEmpty from '../../components/user/BaseEmpty.vue'
import { useDebounce } from '../../composables/useDebounce'
import { getServiceFaq, getServiceReply } from '../../mock/userMock'

const router = useRouter()

const faq = ref([])
const faqKw = ref('')
const expanded = ref([])
const loading = ref(true)
const chatShow = ref(false)
const chatMsgs = ref([])
const chatInput = ref('')
const chatTyping = ref(false)
const chatBodyRef = ref(null)

const filteredFaq = computed(() => {
  const k = faqKw.value.trim()
  if (!k) return faq.value
  return faq.value.filter((f) => f.q.includes(k))
})

const { debounced: debouncedFilter } = useDebounce(() => {
  expanded.value = []
})

// 对话历史持久化
const CHAT_KEY = 'user-mock-service-chat'
const loadChat = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]')
    chatMsgs.value = saved.length
      ? saved
      : [{ from: 'bot', text: '您好，我是智能客服小美～请问有什么可以帮您？' }]
  } catch (e) {
    chatMsgs.value = [{ from: 'bot', text: '您好，我是智能客服小美～请问有什么可以帮您？' }]
  }
}

const saveChat = () => {
  localStorage.setItem(CHAT_KEY, JSON.stringify(chatMsgs.value.slice(-20)))
}

const scrollChat = () => {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

const onChatSend = async () => {
  const t = chatInput.value.trim()
  if (!t || chatTyping.value) return
  chatInput.value = ''
  chatMsgs.value.push({ from: 'user', text: t })
  saveChat()
  scrollChat()
  chatTyping.value = true
  setTimeout(async () => {
    chatTyping.value = false
    chatMsgs.value.push({ from: 'bot', text: await getServiceReply(t) })
    saveChat()
    scrollChat()
  }, 600)
}

onMounted(async () => {
  faq.value = await getServiceFaq()
  loading.value = false
  loadChat()
})
</script>

<style scoped>
.service-page {
  max-width: 750px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--mt-bg);
  padding-bottom: 20px;
}
/* ===== 热线卡 ===== */
.hotline-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--mt-gradient);
  margin: 8px;
  border-radius: var(--mt-radius);
  padding: 16px;
  box-shadow: 0 4px 10px rgba(255, 195, 0, 0.3);
}
.hotline-emoji {
  font-size: 32px;
}
.hotline-info {
  flex: 1;
}
.hotline-title {
  font-size: 13px;
  font-weight: 700;
  color: #333;
}
.hotline-num {
  margin-top: 3px;
  font-size: 20px;
  font-weight: 800;
  color: #333;
  letter-spacing: 1px;
}
.hotline-time {
  margin-top: 2px;
  font-size: 11px;
  color: rgba(51, 51, 51, 0.6);
}
.hotline-call {
  background: #333;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 18px;
  padding: 8px 20px;
  text-decoration: none;
}
/* ===== 在线客服卡 ===== */
.online-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  margin: 0 8px 8px;
  border-radius: var(--mt-radius);
  padding: 14px 16px;
  cursor: pointer;
}
.online-emoji {
  font-size: 28px;
}
.online-info {
  flex: 1;
}
.online-title {
  font-size: 14px;
  font-weight: 700;
  color: #333;
}
.online-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--mt-text-3);
}
.online-arrow {
  color: #ccc;
}
/* ===== FAQ ===== */
.faq-section {
  background: #fff;
  margin: 8px;
  border-radius: var(--mt-radius);
  padding: 14px 16px;
}
.faq-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.faq-title {
  font-size: 15px;
  font-weight: 700;
  color: #333;
}
.faq-search {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--mt-bg);
  border-radius: 14px;
  padding: 5px 10px;
  color: var(--mt-text-3);
}
.faq-input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 12px;
  width: 90px;
  color: var(--mt-text);
}
.faq-skeleton {
  padding: 8px 0;
}
.faq-collapse :deep(.van-collapse-item__title) {
  font-size: 13px;
  color: #333;
}
.faq-text {
  font-size: 12px;
  color: var(--mt-text-2);
  line-height: 1.8;
}
/* ===== 客服对话弹层 ===== */
.chat-popup {
  border-radius: 20px 20px 0 0;
  height: 78vh;
  display: flex;
  flex-direction: column;
}
.chat-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid #f5f5f5;
}
.chat-title {
  font-size: 16px;
  font-weight: 700;
  color: #333;
}
.chat-status {
  font-size: 11px;
  color: var(--mt-success);
  background: rgba(6, 174, 86, 0.1);
  border-radius: 8px;
  padding: 1px 6px;
}
.chat-close {
  margin-left: auto;
  color: var(--mt-text-3);
  font-size: 18px;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  background: #fafafa;
}
.chat-msg {
  display: flex;
  margin-bottom: 12px;
}
.chat-msg.user {
  justify-content: flex-end;
}
.chat-bubble {
  max-width: 76%;
  font-size: 13px;
  line-height: 1.6;
  padding: 9px 13px;
  border-radius: 14px;
  word-break: break-all;
}
.chat-msg.bot .chat-bubble {
  background: #fff;
  color: #333;
  border-top-left-radius: 4px;
}
.chat-msg.user .chat-bubble {
  background: var(--mt-gradient);
  color: #333;
  border-top-right-radius: 4px;
}
.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  border-top: 1px solid #f5f5f5;
  background: #fff;
}
.chat-input {
  flex: 1;
  border: none;
  outline: none;
  background: var(--mt-bg);
  border-radius: 18px;
  padding: 9px 14px;
  font-size: 13px;
  color: var(--mt-text);
}
.chat-send {
  background: var(--mt-gradient);
  color: #333;
  font-size: 13px;
  font-weight: 700;
  border-radius: 18px;
  padding: 9px 18px;
  cursor: pointer;
}
</style>
