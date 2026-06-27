/** 租户管理 API */

import { http, type TenantRequestConfig } from '../http'
import type { ApiResponse } from '@/types/api'
import type {
  TenantView,
  TenantChangeView,
  TenantHistorySummaryView,
} from '@/types/tenant'

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

export interface TenantRequestOptions extends Pick<TenantRequestConfig, 'silentAuthFailure' | 'suppressErrorMessage'> {}

export async function queryTenants(params?: TenantQueryParams, options?: TenantRequestOptions) {
  const { data } = await http.get<ApiResponse<TenantPage>>('/api/tenants', {
    params,
    tenantScope: 'operator',
    ...(options ?? {}),
  } satisfies TenantRequestConfig)
  return data.data
}

export async function createTenant(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<TenantView>>('/api/tenants', payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function updateTenant(tenantId: string, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<TenantView>>(`/api/tenants/${tenantId}`, payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function deleteTenant(tenantId: string) {
  await http.delete(`/api/tenants/${tenantId}`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
}

export async function queryTenantHistory(tenantId: string, params?: TenantHistoryQueryParams) {
  const { data } = await http.get<ApiResponse<TenantHistoryPage>>(`/api/tenants/${tenantId}/history`, {
    params,
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function queryTenantHistorySummary(tenantId: string, params?: Omit<TenantHistoryQueryParams, 'page' | 'size'>) {
  const { data } = await http.get<ApiResponse<TenantHistorySummaryView>>(`/api/tenants/${tenantId}/history/summary`, {
    params,
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function queryTenantMenus(tenantId: string) {
  const { data } = await http.get<ApiResponse<number[]>>(`/api/tenants/${tenantId}/menus`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function saveTenantMenus(tenantId: string, menuIds: number[]) {
  await http.put(
    `/api/tenants/${tenantId}/menus`,
    { menuIds },
    { tenantScope: 'operator' } satisfies TenantRequestConfig,
  )
}
