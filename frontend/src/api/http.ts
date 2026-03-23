import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

function resolveBackendOrigin() {
  const configuredOrigin = import.meta.env.VITE_BACKEND_ORIGIN
  if (configuredOrigin) {
    return configuredOrigin
  }
  if (typeof window === 'undefined') {
    return 'http://127.0.0.1:8080'
  }
  return `${window.location.protocol}//${window.location.hostname}:8080`
}

const backendOrigin = resolveBackendOrigin()
let redirectingToLogin = false

export const http = axios.create({
  baseURL: backendOrigin,
  timeout: 15000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
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
  }
  if (authStore.tenantId) {
    const currentTenantHeader = typeof config.headers?.get === 'function'
      ? config.headers.get('X-Tenant-Id')
      : config.headers?.['X-Tenant-Id']
    if (!currentTenantHeader) {
      config.headers['X-Tenant-Id'] = authStore.tenantId
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const authStore = useAuthStore()
    const requestUrl = String(error.config?.url ?? '')
    const inAuthCallback = typeof window !== 'undefined' && window.location.pathname === '/auth/callback'
    const bypassRedirect = requestUrl.includes('/api/auth/csrf')
      || requestUrl.includes('/api/auth/oauth/exchange')
      || (inAuthCallback && requestUrl.includes('/api/auth/me'))
    const canRetryRefresh = !requestUrl.includes('/api/auth/oauth/refresh') && !requestUrl.includes('/api/auth/oauth/exchange')
    if (error.response?.status === 401 && authStore.accessToken && !error.config.__retry && canRetryRefresh) {
      error.config.__retry = true
      try {
        await authStore.refreshTokens()
        return http.request(error.config)
      } catch {
        showError('登录状态已失效，请重新登录')
        redirectToLogin()
        return Promise.reject(error)
      }
    }
    if ((error.response?.status === 401 || error.response?.status === 403) && bypassRedirect) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401 || error.response?.status === 403) {
      showError('登录状态已失效，请重新登录')
      redirectToLogin()
      return new Promise(() => {})
    }

    const message = error.response?.data?.message ?? '请求失败，请稍后重试'
    showError(message)
    return Promise.reject(error)
  },
)
