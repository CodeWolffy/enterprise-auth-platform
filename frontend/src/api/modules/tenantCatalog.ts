import { http, type TenantRequestConfig } from '../http'
import type { ApiResponse } from '@/types/api'
import type {
  TenantPackageImpactView,
  TenantPackageView,
} from '@/types/tenant'

export interface TenantPackagePayload {
  packageCode: string
  packageName: string
  subtitle?: string
  salesPrice?: number
  originalPrice?: number
  descriptionMd?: string
  appKey?: string
  orderNo?: number
  packageDesc?: string
  status?: '0' | '1'
}

export async function queryTenantPackages() {
  const { data } = await http.get<ApiResponse<TenantPackageView[]>>('/api/tenant-catalog/packages', {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function createTenantPackage(payload: TenantPackagePayload) {
  const { data } = await http.post<ApiResponse<TenantPackageView>>('/api/tenant-catalog/packages', payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function updateTenantPackage(id: number, payload: TenantPackagePayload) {
  const { data } = await http.put<ApiResponse<TenantPackageView>>(`/api/tenant-catalog/packages/${id}`, payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function queryTenantPackageImpact(id: number) {
  const { data } = await http.get<ApiResponse<TenantPackageImpactView>>(`/api/tenant-catalog/packages/${id}/impact`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function deleteTenantPackage(id: number) {
  await http.delete(`/api/tenant-catalog/packages/${id}`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
}
