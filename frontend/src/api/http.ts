import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const backendOrigin = import.meta.env.VITE_BACKEND_ORIGIN ?? 'http://127.0.0.1:8080'

export const http = axios.create({
  baseURL: backendOrigin,
  timeout: 15000,
})

http.interceptors.request.use(async (config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    if (authStore.shouldRefreshToken()) {
      await authStore.refreshTokens()
    }
    config.headers.Authorization = `Bearer ${authStore.accessToken}`
  }
  if (authStore.tenantId) {
    config.headers['X-Tenant-Id'] = authStore.tenantId
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const authStore = useAuthStore()
    if (error.response?.status === 401 && authStore.refreshToken && !error.config.__retry) {
      error.config.__retry = true
      try {
        await authStore.refreshTokens()
        error.config.headers.Authorization = `Bearer ${authStore.accessToken}`
        return http.request(error.config)
      } catch {
        authStore.clearSession()
      }
    }
    const message = error.response?.data?.message ?? '请求失败，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)
