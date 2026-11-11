// 购物车状态仓库：支持 localStorage 持久化，且同一时间只允许存放一家店铺的商品
import { defineStore } from 'pinia'

export const useCartStore = defineStore('cart', {
  state: () => ({
    // 购物车条目：[{dishId, name, price, image, num}]
    items: JSON.parse(localStorage.getItem('cart') || '[]'),
    // 当前购物车所属店铺 id
    shopId: JSON.parse(localStorage.getItem('cartShopId') || 'null')
  }),
  getters: {
    // 商品总数量
    totalCount: (s) => s.items.reduce((sum, i) => sum + (Number(i.num) || 0), 0),
    // 商品总价（保留 2 位小数）
    totalPrice: (s) => Number(s.items.reduce((sum, i) => sum + (Number(i.num) || 0) * (Number(i.price) || 0), 0).toFixed(2))
  },
  actions: {
    // 持久化到 localStorage
    save() {
      localStorage.setItem('cart', JSON.stringify(this.items))
      localStorage.setItem('cartShopId', JSON.stringify(this.shopId))
    },
    // 添加商品：不同店铺则清空购物车重来；同菜品则累加数量
    add(item) {
      if (this.shopId !== null && this.shopId !== item.shopId) {
        this.items = []
      }
      this.shopId = item.shopId
      const found = this.items.find((i) => i.dishId === item.dishId)
      if (found) {
        found.num += Number(item.num) || 1
      } else {
        this.items.push({ ...item, num: Number(item.num) || 1 })
      }
      this.save()
    },
    // 直接设置某菜品数量（数量为 0 时移除该条目）
    setNum(dishId, num) {
      const idx = this.items.findIndex((i) => i.dishId === dishId)
      if (idx === -1) return
      if (num <= 0) {
        this.items.splice(idx, 1)
      } else {
        this.items[idx].num = num
      }
      this.save()
    },
    // 按下标移除条目
    remove(index) {
      this.items.splice(index, 1)
      this.save()
    },
    // 清空购物车
    clear() {
      this.items = []
      this.shopId = null
      this.save()
    }
  }
})
