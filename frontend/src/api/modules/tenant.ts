/** 租户管理 API */

import { http } from '../http'
import type {
  ApiResponse,
  TenantView,
  TenantChangeView,
  TenantHistorySummaryView,
  TenantCapabilityOverrideView,
} from '@/types/auth'

export interface TenantQueryParams {
  keyword?: string
  platformLevel?: boolean
  tenantStatus?: number
  page?: number
  size?: number
}

export interface TenantPage {
  total: number
  page: number
  size: number
  records: TenantView[]
}

export interface TenantHistoryPage {
  total: number
  page: number
  size: number
  records: TenantChangeView[]
}

export interface TenantHistoryQueryParams {
  changeType?: string
  fieldKey?: string
  operator?: string
  fromEpochMs?: number
  toEpochMs?: number
  page?: number
  size?: number
}

export interface TenantRequestOptions {
  silentAuthFailure?: boolean
  suppressErrorMessage?: boolean
}

export async function queryTenants(params?: TenantQueryParams, options?: TenantRequestOptions) {
  const { data } = await http.get<ApiResponse<TenantPage>>('/api/tenants', {
    params,
    ...(options ?? {}),
  } as any)
  return data.data
}

export async function createTenant(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<TenantView>>('/api/tenants', payload)
  return data.data
}

export async function updateTenant(tenantId: string, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<TenantView>>(`/api/tenants/${tenantId}`, payload)
  return data.data
}

export async function deleteTenant(tenantId: string) {
  await http.delete(`/api/tenants/${tenantId}`)
}

export async function queryTenantHistory(tenantId: string, params?: TenantHistoryQueryParams) {
  const { data } = await http.get<ApiResponse<TenantHistoryPage>>(`/api/tenants/${tenantId}/history`, {
    params,
  })
  return data.data
}

export async function queryTenantHistorySummary(tenantId: string, params?: Omit<TenantHistoryQueryParams, 'page' | 'size'>) {
  const { data } = await http.get<ApiResponse<TenantHistorySummaryView>>(`/api/tenants/${tenantId}/history/summary`, {
    params,
  })
  return data.data
}

export async function queryTenantCapabilityOverrides(tenantId: string) {
  const { data } = await http.get<ApiResponse<TenantCapabilityOverrideView>>(
    `/api/tenants/${tenantId}/capability-overrides`,
  )
  return data.data
}

export async function updateTenantCapabilityOverrides(
  tenantId: string,
  payload: {
    overrides: Array<{
      capabilityCode: string
      enabled?: boolean | null
      capabilityDescOverride?: string | null
    }>
  },
) {
  const { data } = await http.put<ApiResponse<TenantCapabilityOverrideView>>(
    `/api/tenants/${tenantId}/capability-overrides`,
    payload,
  )
  return data.data
}