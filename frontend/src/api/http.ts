import axios, { AxiosHeaders, type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  requestKey?: string
  retry?: number
  retryDelay?: number
  silentAuthFailure?: boolean
  suppressErrorMessage?: boolean
  __retryCount?: number
}

const retryableMethods = new Set(['get', 'head', 'options'])
const activeControllers = new Map<string, AbortController>()

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

export function cancelRequest(requestKey: string) {
  activeControllers.get(requestKey)?.abort()
  activeControllers.delete(requestKey)
}

export function cancelRequests(requestKeys: string[]) {
  requestKeys.forEach(cancelRequest)
}

export function createRequestScope() {
  const requestKeys = new Set<string>()
  return {
    track(requestKey: string) {
      requestKeys.add(requestKey)
      return requestKey
    },
    cancelAll() {
      cancelRequests([...requestKeys])
      requestKeys.clear()
    },
  }
}

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

function redirectToPasswordChange() {
  if (typeof window === 'undefined' || window.location.pathname === '/account/profile') {
    return
  }
  window.location.href = '/account/profile'
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
  const headers = AxiosHeaders.from(config.headers)
  const token = authStore.token

  if (token && !isAuthEndpoint(requestUrl)) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (authStore.tenantId && !isAuthEndpoint(requestUrl) && !headers.has('X-Tenant-Id')) {
    headers.set('X-Tenant-Id', authStore.tenantId)
  }

  config.headers = headers
  bindAbortController(config as RetryableRequestConfig)
  return config
})

http.interceptors.response.use(
  (response) => {
    releaseAbortController(response.config as RetryableRequestConfig)
    return response
  },
  async (error: AxiosError) => {
    const authStore = useAuthStore()
    const requestConfig = (error.config ?? {}) as RetryableRequestConfig
    const requestUrl = String(requestConfig.url ?? '')
    const silentAuthFailure = Boolean(requestConfig.silentAuthFailure)
    const suppressErrorMessage = Boolean(requestConfig.suppressErrorMessage)

    if (shouldRetry(error, requestConfig)) {
      requestConfig.__retryCount = (requestConfig.__retryCount ?? 0) + 1
      await wait(requestConfig.retryDelay ?? 300)
      return http.request(requestConfig)
    }

    releaseAbortController(requestConfig)

    const code = responseCode(error)

    if (error.response?.status === 401 && !isAuthEndpoint(requestUrl)) {
      const reason = typeof code === 'string' ? code : 'UNAUTHORIZED'
      authStore.clearSession()
      if (!silentAuthFailure) {
        showError(authFailureMessage(reason))
        redirectToLogin(reason)
      }
      return Promise.reject(error)
    }

    if (error.response?.status === 403) {
      if (code === 'PASSWORD_CHANGE_REQUIRED') {
        authStore.requirePasswordChange(authStore.passwordChangeReason || 'PASSWORD_CHANGE_REQUIRED')
        if (!suppressErrorMessage && window.location.pathname !== '/account/profile') {
          showError(resolveResponseMessage(error, '当前会话必须先修改密码'))
        }
        redirectToPasswordChange()
        return Promise.reject(error)
      }
      if (!suppressErrorMessage) {
        showError(resolveResponseMessage(error, '无权限访问'))
      }
      return Promise.reject(error)
    }

    if (!axios.isCancel(error) && !suppressErrorMessage) {
      showError(resolveResponseMessage(error, '请求失败，请稍后重试'))
    }
    return Promise.reject(error)
  },
)

function bindAbortController(config: RetryableRequestConfig) {
  if (!config.requestKey) {
    return
  }
  cancelRequest(config.requestKey)
  const controller = new AbortController()
  activeControllers.set(config.requestKey, controller)
  config.signal = controller.signal
}

function releaseAbortController(config: RetryableRequestConfig) {
  if (config.requestKey && activeControllers.get(config.requestKey)?.signal === config.signal) {
    activeControllers.delete(config.requestKey)
  }
}

function shouldRetry(error: AxiosError, config: RetryableRequestConfig) {
  const method = String(config.method ?? 'get').toLowerCase()
  const maxRetry = config.retry ?? 1
  const retryCount = config.__retryCount ?? 0
  if (!retryableMethods.has(method) || retryCount >= maxRetry || axios.isCancel(error)) {
    return false
  }
  const status = error.response?.status
  return !status || status === 408 || status === 429 || status >= 500
}

function wait(delay: number) {
  return new Promise((resolve) => window.setTimeout(resolve, delay))
}

function resolveResponseMessage(error: AxiosError, fallback: string) {
  const data = error.response?.data
  if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
    return data.message
  }
  return fallback
}

function responseCode(error: AxiosError) {
  const data = error.response?.data
  if (data && typeof data === 'object' && 'code' in data && typeof data.code === 'string') {
    return data.code
  }
  return undefined
}

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
