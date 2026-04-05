import { http } from './http'
import type {
  ApiResponse,
  AuditExportPolicy,
  AuditExportTask,
  AuditPage,
  DepartmentView,
  ResourceTreeNode,
  ResourceType,
  RoleView,
  TenantChangeView,
  TenantCapabilityOverrideView,
  TenantView,
  TenantHistorySummaryView,
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

export interface TenantHistoryQueryParams {
  changeType?: string
  fieldKey?: string
  operator?: string
  fromEpochMs?: number
  toEpochMs?: number
  page?: number
  size?: number
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

export async function queryAssignedRoleResources(roleId: number) {
  const { data } = await http.get<ApiResponse<number[]>>(`/api/roles/${roleId}/resources`)
  return data.data
}

export async function assignRoleResources(roleId: number, resourceIds: number[]) {
  const { data } = await http.put<ApiResponse<number[]>>(`/api/roles/${roleId}/resources`, { resourceIds })
  return data.data
}

export async function queryResourceTree() {
  const { data } = await http.get<ApiResponse<ResourceTreeNode[]>>('/api/resources/tree')
  return data.data
}

export interface ResourceMutationPayload {
  parentId?: number | null
  resourceType: ResourceType
  resourceKey: string
  resourceName: string
  routeKey?: string | null
  grantKey?: string | null
  path?: string | null
  component?: string | null
  icon?: string | null
  orderNo?: number | null
  visible?: boolean | null
  enabled?: boolean | null
}

export async function createResource(payload: ResourceMutationPayload) {
  const { data } = await http.post<ApiResponse<ResourceTreeNode>>('/api/resources', payload)
  return data.data
}

export async function updateResource(resourceId: number, payload: ResourceMutationPayload) {
  const { data } = await http.put<ApiResponse<ResourceTreeNode>>(`/api/resources/${resourceId}`, payload)
  return data.data
}

export async function deleteResource(resourceId: number) {
  await http.delete(`/api/resources/${resourceId}`)
}

export async function sortResource(resourceId: number, orderNo: number) {
  const { data } = await http.put<ApiResponse<ResourceTreeNode>>(`/api/resources/${resourceId}/sort`, { orderNo })
  return data.data
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

export interface TenantRequestOptions {
  silentAuthFailure?: boolean
  suppressErrorMessage?: boolean
}

export async function queryTenants(params?: TenantQueryParams, options?: TenantRequestOptions) {
  const { data } = await http.get<ApiResponse<TenantPage>>('/api/tenants', {
    params,
    ...(options ?? {}),
  } as any)
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

export async function queryTenantHistory(tenantId: string, params?: TenantHistoryQueryParams) {
  const { data } = await http.get<ApiResponse<TenantHistoryPage>>(`/api/tenants/${tenantId}/history`, {
    params,
  })
  return data.data
}

export async function queryTenantHistorySummary(tenantId: string, params?: Omit<TenantHistoryQueryParams, 'page' | 'size'>) {
  const { data } = await http.get<ApiResponse<TenantHistorySummaryView>>(`/api/tenants/${tenantId}/history/summary`, {
    params,
  })
  return data.data
}

export async function queryTenantCapabilityOverrides(tenantId: string) {
  const { data } = await http.get<ApiResponse<TenantCapabilityOverrideView>>(
    `/api/tenants/${tenantId}/capability-overrides`,
  )
  return data.data
}

export async function updateTenantCapabilityOverrides(
  tenantId: string,
  payload: {
    overrides: Array<{
      capabilityCode: string
      enabled?: boolean | null
      capabilityDescOverride?: string | null
    }>
  },
) {
  const { data } = await http.put<ApiResponse<TenantCapabilityOverrideView>>(
    `/api/tenants/${tenantId}/capability-overrides`,
    payload,
  )
  return data.data
}

export interface AuditQueryParams {
  tenantId?: string
  eventType?: string
  operator?: string
  requestId?: string
  clientIp?: string
  fromEpochMs?: number
  toEpochMs?: number
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

export async function queryAuditExportTasks(params?: {
  tenantId?: string
  status?: string
  operator?: string
  page?: number
  size?: number
}) {
  const { data } = await http.get<ApiResponse<AuditExportTaskPage>>('/api/audit/exports', { params })
  return data.data
}

export async function downloadAuditExportTask(taskId: number) {
  const response = await http.get(`/api/audit/exports/${taskId}/download`, {
    responseType: 'blob',
  })
  return response.data as Blob
}

export async function deleteAuditExportTask(taskId: number) {
  await http.delete(`/api/audit/exports/${taskId}`)
}

export async function retryAuditExportTask(taskId: number) {
  const { data } = await http.post<ApiResponse<AuditExportTask>>(`/api/audit/exports/${taskId}/retry`)
  return data.data
}

export async function archiveAuditExportTask(taskId: number) {
  const { data } = await http.post<ApiResponse<AuditExportTask>>(`/api/audit/exports/${taskId}/archive`)
  return data.data
}

export async function archiveAuditExportTasks(params: { tenantId?: string; status?: string; completedBeforeEpochMs: number }) {
  const { data } = await http.post<ApiResponse<number>>('/api/audit/exports/archive', null, { params })
  return data.data
}

export async function cleanupAuditExportTasks(params: { tenantId?: string; status?: string; completedBeforeEpochMs: number }) {
  const { data } = await http.delete<ApiResponse<number>>('/api/audit/exports', { params })
  return data.data
}

export async function queryAuditExportPolicy(tenantId?: string) {
  const { data } = await http.get<ApiResponse<AuditExportPolicy>>('/api/audit/exports/policy', {
    params: { tenantId },
  })
  return data.data
}

export async function updateAuditExportPolicy(payload: AuditExportPolicy, tenantId?: string) {
  const { data } = await http.put<ApiResponse<AuditExportPolicy>>('/api/audit/exports/policy', payload, {
    params: { tenantId },
  })
  return data.data
}
