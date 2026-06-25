import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type { LogPage, OperationLogRecord, LoginLogRecord } from '@/types/log'

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

export interface LoginLogQueryParams {
  tenantId?: string
  userName?: string
  status?: string
  clientIp?: string
  fromEpochMs?: number
  toEpochMs?: number
  page?: number
  size?: number
}

export async function queryOperationLogs(params: OperationLogQueryParams) {
  const { data } = await http.get<ApiResponse<LogPage<OperationLogRecord>>>('/api/logs/operation', { params })
  return data.data
}

export async function queryLoginLogs(params: LoginLogQueryParams) {
  const { data } = await http.get<ApiResponse<LogPage<LoginLogRecord>>>('/api/logs/login', { params })
  return data.data
}