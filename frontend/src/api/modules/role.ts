/** 角色管理 API */

import { http } from '../http'
import type { TenantRequestConfig } from '../http'
import type { ApiResponse } from '@/types/api'
import type { PageResult } from '@/types/api'
import type { RoleImpactView, RoleView } from '@/types/role'

export interface RoleQueryParams {
  keyword?: string
  dataScopeType?: string
  tenantId?: string
  page?: number
  size?: number
}

export async function queryRoles(params?: RoleQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<RoleView>>>('/api/roles', {
    params,
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function queryRoleOptions() {
  const { data } = await http.get<ApiResponse<RoleView[]>>('/api/roles/options', {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function createRole(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<RoleView>>('/api/roles', payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function updateRole(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<RoleView>>(`/api/roles/${id}`, payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function queryRoleImpact(id: number) {
  const { data } = await http.get<ApiResponse<RoleImpactView>>(`/api/roles/${id}/impact`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function deleteRole(id: number) {
  await http.delete(`/api/roles/${id}`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
}

export async function queryAssignedRoleMenus(roleId: number) {
  const { data } = await http.get<ApiResponse<number[]>>(`/api/roles/${roleId}/menus`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function assignRoleMenus(roleId: number, menuIds: number[]) {
  const { data } = await http.put<ApiResponse<number[]>>(`/api/roles/${roleId}/menus`, { menuIds }, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}
