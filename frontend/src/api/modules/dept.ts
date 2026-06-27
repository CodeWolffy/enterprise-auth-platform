/** 部门管理 API */

import { http } from '../http'
import type { TenantRequestConfig } from '../http'
import type { ApiResponse } from '@/types/api'
import type { DepartmentPayload, DepartmentView } from '@/types/dept'

export async function queryDepartments() {
  const { data } = await http.get<ApiResponse<DepartmentView[]>>('/api/depts', {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function createDepartment(payload: DepartmentPayload) {
  const { data } = await http.post<ApiResponse<DepartmentView>>('/api/depts', payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function updateDepartment(id: number, payload: DepartmentPayload) {
  const { data } = await http.put<ApiResponse<DepartmentView>>(`/api/depts/${id}`, payload, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function deleteDepartment(id: number) {
  await http.delete(`/api/depts/${id}`, {
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
}
