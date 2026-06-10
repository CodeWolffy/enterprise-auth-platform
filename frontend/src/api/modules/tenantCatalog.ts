import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type {
  TenantCapabilityImpactView,
  TenantCapabilityView,
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
  userQuota?: number
  storageQuotaGb?: number
  packageDesc?: string
  enabled?: boolean
  capabilityCodes: string[]
}

export interface TenantCapabilityPayload {
  capabilityCode: string
  capabilityName: string
  capabilityDesc?: string
  sortOrder?: number
  enabled?: boolean
}

export async function queryTenantPackages() {
  const { data } = await http.get<ApiResponse<TenantPackageView[]>>('/api/tenant-catalog/packages')
  return data.data
}

export async function createTenantPackage(payload: TenantPackagePayload) {
  const { data } = await http.post<ApiResponse<TenantPackageView>>('/api/tenant-catalog/packages', payload)
  return data.data
}

export async function updateTenantPackage(id: number, payload: TenantPackagePayload) {
  const { data } = await http.put<ApiResponse<TenantPackageView>>(`/api/tenant-catalog/packages/${id}`, payload)
  return data.data
}

export async function queryTenantPackageImpact(id: number) {
  const { data } = await http.get<ApiResponse<TenantPackageImpactView>>(`/api/tenant-catalog/packages/${id}/impact`)
  return data.data
}

export async function deleteTenantPackage(id: number) {
  await http.delete(`/api/tenant-catalog/packages/${id}`)
}

export async function queryTenantCapabilities() {
  const { data } = await http.get<ApiResponse<TenantCapabilityView[]>>('/api/tenant-catalog/capabilities')
  return data.data
}

export async function createTenantCapability(payload: TenantCapabilityPayload) {
  const { data } = await http.post<ApiResponse<TenantCapabilityView>>('/api/tenant-catalog/capabilities', payload)
  return data.data
}

export async function updateTenantCapability(id: number, payload: TenantCapabilityPayload) {
  const { data } = await http.put<ApiResponse<TenantCapabilityView>>(`/api/tenant-catalog/capabilities/${id}`, payload)
  return data.data
}

export async function queryTenantCapabilityImpact(id: number) {
  const { data } = await http.get<ApiResponse<TenantCapabilityImpactView>>(`/api/tenant-catalog/capabilities/${id}/impact`)
  return data.data
}

export async function deleteTenantCapability(id: number) {
  await http.delete(`/api/tenant-catalog/capabilities/${id}`)
}