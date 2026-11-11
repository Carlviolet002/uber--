import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 美团风格主题变量覆盖（须在 vant / element-plus 样式之后引入才能生效）
import './styles/theme.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Vant)
app.use(ElementPlus)
app.mount('#app')
