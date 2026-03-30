import { http } from './http'
import type {
  ApiResponse,
  CaptchaResponse,
  CookieSessionResponse,
  CsrfTokenResponse,
  PermissionSnapshot,
  RegisterOptionsResponse,
} from '@/types/auth'

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

export async function fetchCaptcha() {
  const { data } = await http.get<ApiResponse<CaptchaResponse>>('/api/auth/captcha')
  return data.data
}

export async function loginWithPassword(payload: {
  username: string
  password: string
  captchaId: string
  captchaCode: string
  tenantId?: string
  device?: string
}) {
  await ensureCsrfToken()
  const { data } = await http.post<ApiResponse<CookieSessionResponse>>('/api/auth/login', payload)
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
