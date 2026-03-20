import { http } from './http'
import type {
  ApiResponse,
  AuditExportTask,
  AuditPage,
  DepartmentView,
  PermissionView,
  RoleView,
  TenantChangeView,
  TenantView,
  UserSummary,
} from '@/types/auth'

export interface UserQueryParams {
  username?: string
  mobile?: string
  email?: string
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

export async function queryAssignedPermissions(roleId: number) {
  const { data } = await http.get<ApiResponse<PermissionView[]>>(`/api/roles/${roleId}/permissions`)
  return data.data
}

export async function assignRolePermissions(roleId: number, permissionCodes: string[]) {
  const { data } = await http.put<ApiResponse<PermissionView[]>>(`/api/roles/${roleId}/permissions`, { permissionCodes })
  return data.data
}

export async function queryPermissions() {
  const { data } = await http.get<ApiResponse<PermissionView[]>>('/api/permissions')
  return data.data
}

export async function createPermission(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<PermissionView>>('/api/permissions', payload)
  return data.data
}

export async function updatePermission(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<PermissionView>>(`/api/permissions/${id}`, payload)
  return data.data
}

export async function deletePermission(id: number) {
  await http.delete(`/api/permissions/${id}`)
}

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

export async function queryTenants(params?: TenantQueryParams) {
  const { data } = await http.get<ApiResponse<TenantPage>>('/api/tenants', { params })
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

export async function queryTenantHistory(tenantId: string, page = 1, size = 10) {
  const { data } = await http.get<ApiResponse<TenantHistoryPage>>(`/api/tenants/${tenantId}/history`, {
    params: { page, size },
  })
  return data.data
}

export interface AuditQueryParams {
  tenantId?: string
  eventType?: string
  operator?: string
  requestId?: string
  clientIp?: string
  occurredFrom?: string
  occurredTo?: string
  page?: number
  size?: number
}

export async function queryAuditEvents(params: AuditQueryParams) {
  const { data } = await http.get<ApiResponse<AuditPage>>('/api/audit/events', { params })
  return data.data
}

export async function exportAuditEvents(params: Omit<AuditQueryParams, 'page' | 'size'>) {
  const response = await http.get('/api/audit/events/export', {
    params,
    responseType: 'blob',
  })
  return response.data as Blob
}

export interface AuditExportTaskPage {
  total: number
  page: number
  size: number
  records: AuditExportTask[]
}

export async function createAuditExportTask(params: Omit<AuditQueryParams, 'page' | 'size'>) {
  const { data } = await http.post<ApiResponse<AuditExportTask>>('/api/audit/exports', null, { params })
  return data.data
}

export async function queryAuditExportTasks(params?: { tenantId?: string; status?: string; page?: number; size?: number }) {
  const { data } = await http.get<ApiResponse<AuditExportTaskPage>>('/api/audit/exports', { params })
  return data.data
}

export async function downloadAuditExportTask(taskId: number) {
  const response = await http.get(`/api/audit/exports/${taskId}/download`, {
    responseType: 'blob',
  })
  return response.data as Blob
}
