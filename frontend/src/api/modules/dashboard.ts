import { http } from '../http'
import type { ApiResponse } from '@/types/api'

export interface DashboardStatsResponse {
  scope: 'PLATFORM' | 'TENANT' | 'VISIBLE' | string
  tenantId?: string | null
  userCount: number
  roleCount: number
  tenantCount: number
  fileCount: number
  storageBytes: number
  operationLogCount: number
  recentOperationLogCount: number
}

export async function fetchDashboardStats() {
  const { data } = await http.get<ApiResponse<DashboardStatsResponse>>('/api/dashboard/stats')
  return data.data
}