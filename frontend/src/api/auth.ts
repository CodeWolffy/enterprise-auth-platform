import { http } from './http'
import type {
  ApiResponse,
  CaptchaResponse,
  PermissionSnapshot,
  RegisterOptionsResponse,
  TokenSessionResponse,
  UserSessionView,
} from '@/types/auth'

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
  if (!data.data?.captchaId) {
    throw new Error('验证码 ID 缺失')
  }
  return data.data
}

export async function verifyCaptcha(captchaId: string, captchaCode: string) {
  const { data } = await http.post<ApiResponse<void>>('/api/auth/captcha/verify', {
    captchaId,
    captchaCode,
  })
  return data
}

export async function loginWithPassword(payload: {
  username: string
  password: string
  captchaId: string
  captchaCode: string
  tenantId?: string
  device?: string
}) {
  const { data } = await http.post<ApiResponse<TokenSessionResponse>>('/api/auth/login', payload)
  return data.data
}

export async function logoutCurrentSession() {
  await http.post('/api/auth/logout')
}

export async function querySessions() {
  const { data } = await http.get<ApiResponse<UserSessionView[]>>('/api/auth/sessions')
  return data.data
}

export async function forceOffline(sessionId: string) {
  await http.post(`/api/auth/sessions/${sessionId}/offline`)
}
