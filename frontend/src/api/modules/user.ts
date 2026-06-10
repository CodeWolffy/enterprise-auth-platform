/** 用户管理 API */

import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type { RoleView } from '@/types/role'
import type { UserSummary } from '@/types/user'

export interface UserQueryParams {
  username?: string
  mobile?: string
  email?: string
  deptId?: number
  enabled?: boolean
  page?: number
  size?: number
}

export interface UserPage {
  total: number
  page: number
  size: number
  records: UserSummary[]
}

export async function queryUsers(params?: UserQueryParams) {
  const { data } = await http.get<ApiResponse<UserPage>>('/api/users', { params })
  return data.data
}

export async function createUser(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<UserSummary>>('/api/users', payload)
  return data.data
}

export async function updateUser(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<UserSummary>>(`/api/users/${id}`, payload)
  return data.data
}

export async function deleteUser(id: number) {
  await http.delete(`/api/users/${id}`)
}

export async function queryAssignedRoles(userId: number) {
  const { data } = await http.get<ApiResponse<RoleView[]>>(`/api/users/${userId}/roles`)
  return data.data
}

export async function assignUserRoles(userId: number, roleCodes: string[]) {
  const { data } = await http.put<ApiResponse<UserSummary>>(`/api/users/${userId}/roles`, { roleCodes })
  return data.data
}