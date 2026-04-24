import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { permissionDirective } from './directives/v-permission'
import './styles/main.scss'
import './styles/auth-pages.scss'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useAuthStore(pinia).restore()
app.use(router)
app.use(ElementPlus)

app.directive('permission', permissionDirective)

app.mount('#app')