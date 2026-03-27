import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { redirectToAuthorizationPage } from '@/utils/authRedirect'
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

const AUTH_INVALID_CODES = new Set([
  'SESSION_EXPIRED',
  'SESSION_NOT_FOUND',
  'INVALID_TOKEN',
  'TOKEN_VERSION_MISMATCH',
  'TENANT_MISMATCH',
  'USER_DISABLED',
  'BAD_CREDENTIALS',
  'SESSION_SUBJECT_MISMATCH',
  'ACCESS_TOKEN_TYPE_INVALID',
])

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

async function redirectToLogin() {
  const authStore = useAuthStore()
  const tenantId = authStore.operatorTenantId || authStore.tenantId || 'platform'
  authStore.clearSession()
  await redirectToAuthorizationPage(tenantId)
}

function responseCodeOf(error: any): string | null {
  const code = error?.response?.data?.code
  return typeof code === 'string' ? code : null
}

function isAuthenticationFailure(error: any) {
  const status = error?.response?.status
  const code = responseCodeOf(error)
  if (status === 401) {
    return true
  }
  if (status !== 403) {
    return false
  }
  if (!code) {
    return false
  }
  return AUTH_INVALID_CODES.has(code)
}

function isAuthEndpoint(url: string) {
  return url.includes('/api/auth/csrf')
    || url.includes('/api/auth/oauth/exchange')
    || url.includes('/api/auth/oauth/refresh')
    || url.includes('/api/auth/login')
    || url.includes('/api/auth/refresh')
}

function isTenantHeaderBypassEndpoint(url: string) {
  return isAuthEndpoint(url) || url.includes('/oauth2/')
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
  if (authStore.accessToken) {
    try {
      if (!isAuthEndpoint(requestUrl) && authStore.shouldRefreshToken()) {
        await authStore.refreshTokens()
      }
    } catch {
      showError('Login expired, please sign in again')
      await redirectToLogin()
      return Promise.reject(new Error('token refresh failed'))
    }
  }
  if (authStore.tenantId && !isTenantHeaderBypassEndpoint(requestUrl)) {
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
        showError('Login expired, please sign in again')
        await redirectToLogin()
        return Promise.reject(error)
      }
    }

    const authFailure = isAuthenticationFailure(error)
    if (authFailure && bypassRedirect) {
      return Promise.reject(error)
    }
    if (authFailure) {
      showError('Login expired, please sign in again')
      await redirectToLogin()
      return new Promise(() => {})
    }

    const method = String(error.config?.method ?? 'get').toLowerCase()
    const csrfRetryableMethod = !['get', 'head', 'options'].includes(method)
    if (error.response?.status === 403 && csrfRetryableMethod && !error.config.__csrfRetry && !responseCodeOf(error)) {
      error.config.__csrfRetry = true
      try {
        await ensureCsrfToken(true)
        return http.request(error.config)
      } catch {
        // fallthrough to generic 403 handling
      }
    }

    if (error.response?.status === 403) {
      showError(error.response?.data?.message ?? 'No permission')
      return Promise.reject(error)
    }

    const message = error.response?.data?.message ?? 'Request failed, please retry later'
    showError(message)
    return Promise.reject(error)
  },
)
