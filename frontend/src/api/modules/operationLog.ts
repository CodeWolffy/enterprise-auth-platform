import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type { AuditPage } from '@/types/audit'

export interface OperationLogQueryParams {
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

export async function queryOperationLogs(params: OperationLogQueryParams) {
  const { data } = await http.get<ApiResponse<AuditPage>>('/api/operation-logs', { params })
  return data.data
}

export async function exportOperationLogs(params: Omit<OperationLogQueryParams, 'page' | 'size'>) {
  const response = await http.get('/api/operation-logs/export', {
    params,
    responseType: 'blob',
  })
  return response.data as Blob
}