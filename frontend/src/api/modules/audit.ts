/** 审计日志 API */

import { http } from '../http'
import type { ApiResponse, AuditPage, AuditExportTask, AuditExportPolicy } from '@/types/auth'

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

export interface AuditExportTaskPage {
  total: number
  page: number
  size: number
  records: AuditExportTask[]
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