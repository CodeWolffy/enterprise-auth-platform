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

export const http = axios.create({
  baseURL: backendOrigin,
  timeout: 15000,
  withCredentials: false,
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

function redirectToLogin(reason?: string) {
  if (typeof window === 'undefined' || window.location.pathname === '/login') {
    return
  }
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const params = new URLSearchParams()
  if (current && current !== '/') {
    params.set('redirect', current)
  }
  if (reason) {
    params.set('authReason', reason)
  }
  const query = params.toString()
  window.location.href = `/login${query ? `?${query}` : ''}`
}

function isAuthEndpoint(url: string) {
  return url.includes('/api/auth/login')
    || url.includes('/api/auth/register')
    || url.includes('/api/auth/captcha')
    || url.includes('/api/auth/register/options')
}

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  const requestUrl = String(config.url ?? '')
  const token = authStore.token
  if (token && !isAuthEndpoint(requestUrl)) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (authStore.tenantId && !isAuthEndpoint(requestUrl)) {
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
    const requestConfig = (error.config ?? {}) as {
      url?: string
      silentAuthFailure?: boolean
      suppressErrorMessage?: boolean
    }
    const requestUrl = String(requestConfig.url ?? '')
    const silentAuthFailure = Boolean(requestConfig.silentAuthFailure)
    const suppressErrorMessage = Boolean(requestConfig.suppressErrorMessage)

    if (error.response?.status === 401 && !isAuthEndpoint(requestUrl)) {
      const code = error.response?.data?.code
      const reason = typeof code === 'string' ? code : 'UNAUTHORIZED'
      authStore.clearSession()
      if (!silentAuthFailure) {
        showError(authFailureMessage(reason))
        redirectToLogin(reason)
      }
      return Promise.reject(error)
    }

    if (error.response?.status === 403) {
      if (!suppressErrorMessage) {
        showError(error.response?.data?.message ?? '无权限访问')
      }
      return Promise.reject(error)
    }

    const message = error.response?.data?.message ?? '请求失败，请稍后重试'
    if (!suppressErrorMessage) {
      showError(message)
    }
    return Promise.reject(error)
  },
)

function authFailureMessage(code: string) {
  if (code === 'SESSION_OFFLINE') {
    return '当前账号已被强制下线，请重新登录'
  }
  if (code === 'SESSION_EXPIRED') {
    return '登录已过期，请重新登录'
  }
  if (code === 'INVALID_TOKEN') {
    return '登录凭证已失效，请重新登录'
  }
  return '请先登录'
}
