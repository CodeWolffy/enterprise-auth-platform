import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, CsrfTokenResponse } from '@/types/auth'

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
let csrfReady = false
let csrfPromise: Promise<void> | null = null

export const http = axios.create({
  baseURL: backendOrigin,
  timeout: 15000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

const csrfClient = axios.create({
  baseURL: backendOrigin,
  timeout: 15000,
  withCredentials: true,
  withXSRFToken: true,
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
  if (typeof window === 'undefined' || window.location.pathname === '/login') {
    return
  }
  window.location.href = '/login'
}

function isAuthEndpoint(url: string) {
  return url.includes('/api/auth/csrf')
    || url.includes('/api/auth/login')
    || url.includes('/api/auth/logout')
    || url.includes('/api/auth/register')
}

function shouldEnsureCsrf(method: string | undefined, url: string) {
  const normalized = (method ?? 'get').toLowerCase()
  if (normalized === 'get' || normalized === 'head' || normalized === 'options') {
    return false
  }
  return !url.includes('/api/auth/csrf')
}

async function ensureCsrfToken(force = false) {
  if (force) {
    csrfReady = false
  }
  if (csrfReady) {
    return
  }
  if (csrfPromise) {
    await csrfPromise
    return
  }
  csrfPromise = (async () => {
    const { data } = await csrfClient.get<ApiResponse<CsrfTokenResponse>>('/api/auth/csrf')
    const headerName = data.data?.headerName || 'X-XSRF-TOKEN'
    const token = data.data?.token || ''
    if (!token) {
      throw new Error('CSRF token missing')
    }
    http.defaults.headers.common[headerName] = token
    csrfReady = true
  })()
  try {
    await csrfPromise
  } finally {
    csrfPromise = null
  }
}

http.interceptors.request.use(async (config) => {
  const authStore = useAuthStore()
  const requestUrl = String(config.url ?? '')
  if (shouldEnsureCsrf(config.method, requestUrl)) {
    await ensureCsrfToken()
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
      method?: string
      __csrfRetry?: boolean
      silentAuthFailure?: boolean
      suppressErrorMessage?: boolean
    }
    const requestUrl = String(requestConfig.url ?? '')
    const silentAuthFailure = Boolean(requestConfig.silentAuthFailure)
    const suppressErrorMessage = Boolean(requestConfig.suppressErrorMessage)

    if (error.response?.status === 401 && !isAuthEndpoint(requestUrl)) {
      authStore.clearSession()
      if (!silentAuthFailure) {
        showError('登录已失效，请重新登录')
        redirectToLogin()
      }
      return Promise.reject(error)
    }

    const method = String(requestConfig.method ?? 'get').toLowerCase()
    const csrfRetryableMethod = !['get', 'head', 'options'].includes(method)
    if (error.response?.status === 403 && csrfRetryableMethod && !requestConfig.__csrfRetry && !error.response?.data?.code) {
      requestConfig.__csrfRetry = true
      try {
        await ensureCsrfToken(true)
        return http.request(error.config)
      } catch {
        // fallthrough
      }
    }

    if (error.response?.status === 403) {
      if (!suppressErrorMessage) {
        showError(error.response?.data?.message ?? '无权限')
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
