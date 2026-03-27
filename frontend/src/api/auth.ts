import { http } from './http'
import type { ApiResponse, CookieSessionResponse, CsrfTokenResponse, PermissionSnapshot, RegisterOptionsResponse } from '@/types/auth'
import { consumeOAuthContext, getRedirectUri } from '@/utils/oauth'

let csrfReady = false
let csrfPromise: Promise<void> | null = null

async function ensureCsrfToken() {
  if (csrfReady) {
    return
  }
  if (csrfPromise) {
    await csrfPromise
    return
  }
  csrfPromise = (async () => {
    const { data } = await http.get<ApiResponse<CsrfTokenResponse>>('/api/auth/csrf')
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

export async function fetchPermissionSnapshot() {
  const { data } = await http.get<ApiResponse<PermissionSnapshot>>('/api/auth/me')
  return data.data
}

export async function fetchRegisterOptions() {
  const { data } = await http.get<ApiResponse<RegisterOptionsResponse>>('/api/auth/register/options')
  return data.data
}

export async function exchangeAuthorizationCode(code: string, state: string) {
  const context = consumeOAuthContext()
  if (!context.verifier || !context.state || context.state !== state) {
    throw new Error('授权状态校验失败，请重新登录')
  }
  await ensureCsrfToken()
  const requestConfig = context.tenantId
    ? {
        headers: {
          'X-Tenant-Id': context.tenantId,
        },
        params: {
          tenantId: context.tenantId,
        },
      }
    : undefined

  const { data } = await http.post<ApiResponse<CookieSessionResponse>>(
    '/api/auth/oauth/exchange',
    {
      code,
      codeVerifier: context.verifier,
      redirectUri: getRedirectUri(),
    },
    requestConfig,
  )
  return { payload: data.data, tenantId: data.data.tenantId || context.tenantId || 'platform' }
}

export async function refreshOauthToken() {
  await ensureCsrfToken()
  const { data } = await http.post<ApiResponse<CookieSessionResponse>>('/api/auth/oauth/refresh')
  return data.data
}

export async function logoutCurrentSession() {
  await ensureCsrfToken()
  await http.post('/api/auth/logout')
}

export async function querySessions() {
  const { data } = await http.get<ApiResponse<any[]>>('/api/auth/sessions')
  return data.data
}

export async function forceOffline(sessionId: string) {
  await ensureCsrfToken()
  await http.post(`/api/auth/sessions/${sessionId}/offline`)
}
