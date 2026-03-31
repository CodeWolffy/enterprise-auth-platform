import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { permissionDirective } from './directives/v-permission'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import './styles/main.scss'
import './styles/auth-pages.scss'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useAuthStore(pinia).restore()
app.use(router)

app.directive('permission', permissionDirective)

app.mount('#app')
