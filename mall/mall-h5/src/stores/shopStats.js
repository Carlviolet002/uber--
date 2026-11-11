// 商家端全局状态：shopId 解析 + 待接单角标 + 消息未读红点，布局壳与各页面共用
import { defineStore } from 'pinia'
import { getShopOrders, messageUnreadCount } from '../api'
import { unreadPlatformCount } from '../mock/shopMock'
import { resolveShopId } from '../views/shop/composables/useShopStats'

export const useShopStatsStore = defineStore('shopStats', {
  state: () => ({
    shopId: null, // 解析出的店铺 id（各页面可直接复用）
    pendingCount: 0, // 待接单订单数（订单 Tab 角标）
    unreadCount: unreadPlatformCount(), // 平台消息未读数（消息 Tab 红点）
    shopInfo: {} // 当前店铺信息缓存
  }),
  actions: {
    /** 解析并缓存 shopId（幂等：已解析直接返回） */
    async ensureShopId() {
      if (this.shopId) return this.shopId
      this.shopId = await resolveShopId()
      return this.shopId
    },
    /** 刷新待接单数（布局级 30s 轮询） */
    async refreshPending() {
      try {
        const shopId = await this.ensureShopId()
        const data = await getShopOrders({ shopId, status: 2, page: 1, pageSize: 50 })
        const list = Array.isArray(data) ? data : (data && data.records) || []
        this.pendingCount = list.length
      } catch (e) {
        // 接口异常保持旧值
      }
    },
    /** 刷新平台消息未读数：real 模式走接口，mock 模式（接口 404）静默回退 shopMock 计数 */
    async refreshUnread() {
      try {
        const count = await messageUnreadCount()
        this.unreadCount = typeof count === 'number' ? count : 0
      } catch (e) {
        this.unreadCount = unreadPlatformCount()
      }
    }
  }
})
