import { http } from './http'
import type {
  ApiResponse,
  AuditPage,
  DepartmentView,
  PermissionView,
  RoleView,
  TenantView,
  UserSummary,
} from '@/types/auth'

export async function queryUsers() {
  const { data } = await http.get<ApiResponse<UserSummary[]>>('/api/users')
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

export async function queryTenants() {
  const { data } = await http.get<ApiResponse<TenantView[]>>('/api/tenants')
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

export interface AuditQueryParams {
  tenantId?: string
  eventType?: string
  operator?: string
  requestId?: string
  occurredFrom?: string
  occurredTo?: string
  page?: number
  size?: number
}

export async function queryAuditEvents(params: AuditQueryParams) {
  const { data } = await http.get<ApiResponse<AuditPage>>('/api/audit/events', { params })
  return data.data
}
