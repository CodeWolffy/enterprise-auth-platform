import { http } from './http'
import type { ApiResponse } from '@/types/auth'

export interface ConsentView {
  registeredClientId: string
  tenantId: string
  clientId: string
  clientName: string
  principalName: string
  authorities: string[]
  lastGrantedAt?: string | null
  lastRevokedAt?: string | null
  auditEventCount: number
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export async function queryConsents(page: number, size: number, clientId?: string, principalName?: string) {
  const { data } = await http.get<ApiResponse<PageResult<ConsentView>>>('/api/auth/consents', {
    params: { page, size, clientId, principalName },
  })
  return data.data
}

export async function revokeConsent(registeredClientId: string, principalName: string) {
  await http.delete('/api/auth/consents', {
    params: { registeredClientId, principalName },
  })
}
