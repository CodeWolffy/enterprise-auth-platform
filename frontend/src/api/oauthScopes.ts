import { http } from './http'
import type { ApiResponse, OAuthScopeView } from '@/types/auth'

export interface OauthScopePayload {
  scopeCode: string
  scopeName: string
  scopeDesc?: string
  scopeType?: string
  defaultSelected?: boolean
  visibleInConsent?: boolean
  sortOrder?: number
  enabled?: boolean
}

export async function queryOauthScopes() {
  const { data } = await http.get<ApiResponse<OAuthScopeView[]>>('/api/oauth-scopes')
  return data.data
}

export async function createOauthScope(payload: OauthScopePayload) {
  const { data } = await http.post<ApiResponse<OAuthScopeView>>('/api/oauth-scopes', payload)
  return data.data
}

export async function updateOauthScope(id: number, payload: OauthScopePayload) {
  const { data } = await http.put<ApiResponse<OAuthScopeView>>(`/api/oauth-scopes/${id}`, payload)
  return data.data
}

export async function deleteOauthScope(id: number) {
  await http.delete(`/api/oauth-scopes/${id}`)
}
