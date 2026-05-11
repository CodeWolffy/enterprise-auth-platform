/** 部门管理 API */

import { http } from '../http'
import type { ApiResponse, DepartmentView } from '@/types/auth'

export async function queryDepartments() {
  const { data } = await http.get<ApiResponse<DepartmentView[]>>('/api/depts')
  return data.data
}

export async function createDepartment(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<DepartmentView>>('/api/depts', payload)
  return data.data
}

export async function updateDepartment(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<DepartmentView>>(`/api/depts/${id}`, payload)
  return data.data
}

export async function deleteDepartment(id: number) {
  await http.delete(`/api/depts/${id}`)
}