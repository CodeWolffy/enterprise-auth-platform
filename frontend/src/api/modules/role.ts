/** 角色管理 API */

import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type { RoleView } from '@/types/role'

export async function queryRoles() {
  const { data } = await http.get<ApiResponse<RoleView[]>>('/api/roles')
  return data.data
}

export async function createRole(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<RoleView>>('/api/roles', payload)
  return data.data
}

export async function updateRole(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<RoleView>>(`/api/roles/${id}`, payload)
  return data.data
}

export async function deleteRole(id: number) {
  await http.delete(`/api/roles/${id}`)
}

export async function queryAssignedRoleResources(roleId: number) {
  const { data } = await http.get<ApiResponse<number[]>>(`/api/roles/${roleId}/resources`)
  return data.data
}

export async function assignRoleResources(roleId: number, resourceIds: number[]) {
  const { data } = await http.put<ApiResponse<number[]>>(`/api/roles/${roleId}/resources`, { resourceIds })
  return data.data
}