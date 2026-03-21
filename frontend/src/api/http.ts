import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const backendOrigin = import.meta.env.VITE_BACKEND_ORIGIN ?? 'http://127.0.0.1:8080'
let redirectingToLogin = false

export const http = axios.create({
  baseURL: backendOrigin,
  timeout: 15000,
})

function showError(message: string) {
  ElMessage({
    type: 'error',
    message,
    offset: 24,
    appendTo: document.body,
    grouping: true,
  })
}

function redirectToLogin() {
  const authStore = useAuthStore()
  authStore.clearSession()
  if (window.location.pathname === '/login' || redirectingToLogin) {
    return
  }
  redirectingToLogin = true
  window.location.href = '/login'
}

http.interceptors.request.use(async (config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    try {
      if (authStore.shouldRefreshToken()) {
        await authStore.refreshTokens()
      }
    } catch {
      showError('登录状态已失效，请重新登录')
      redirectToLogin()
      return Promise.reject(new Error('token refresh failed'))
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
        showError('登录状态已失效，请重新登录')
        redirectToLogin()
        return Promise.reject(error)
      }
    }
    if (error.response?.status === 401 || error.response?.status === 403) {
      showError('登录状态已失效，请重新登录')
      redirectToLogin()
      return Promise.reject(error)
    }

    const message = error.response?.data?.message ?? '请求失败，请稍后重试'
    showError(message)
    return Promise.reject(error)
  },
)
