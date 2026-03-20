import { http } from './http'
import type { ApiResponse, PermissionSnapshot } from '@/types/auth'
import { consumeOAuthContext, getBackendOrigin, getPublicClientId, getRedirectUri } from '@/utils/oauth'

export async function fetchPermissionSnapshot() {
  const { data } = await http.get<ApiResponse<PermissionSnapshot>>('/api/auth/me')
  return data.data
}

export async function exchangeAuthorizationCode(code: string, state: string) {
  const context = consumeOAuthContext()
  if (!context.verifier || !context.state || context.state !== state) {
    throw new Error('授权状态校验失败，请重新登录')
  }
  const params = new URLSearchParams()
  params.set('grant_type', 'authorization_code')
  params.set('client_id', getPublicClientId())
  params.set('redirect_uri', getRedirectUri())
  params.set('code', code)
  params.set('code_verifier', context.verifier)

  const response = await fetch(new URL('/oauth2/token', getBackendOrigin()), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  })
  if (!response.ok) {
    throw new Error('换取访问令牌失败')
  }
  const payload = await response.json()
  return { payload, tenantId: context.tenantId }
}

export async function refreshOauthToken(refreshToken: string) {
  const params = new URLSearchParams()
  params.set('grant_type', 'refresh_token')
  params.set('client_id', getPublicClientId())
  params.set('refresh_token', refreshToken)
  const response = await fetch(new URL('/oauth2/token', getBackendOrigin()), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  })
  if (!response.ok) {
    throw new Error('刷新访问令牌失败')
  }
  return response.json()
}
