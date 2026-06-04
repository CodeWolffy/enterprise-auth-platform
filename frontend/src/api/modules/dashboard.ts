import { http } from '../http'
import type { ApiResponse } from '@/types/api'

export interface DashboardDailyTrendPoint {
  date: string
  loginCount: number
  operationCount: number
  loginFailedCount: number
}

export interface DashboardServiceHealthItem {
  code: string
  name: string
  status: 'UP' | 'DOWN' | 'DEGRADED' | string
  message: string
}

export interface DashboardRecentAuditEvent {
  eventType: string
  operator: string
  tenantId: string
  clientIp?: string | null
  occurredAt?: number | null
}

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
  todayLoginCount: number
  onlineUserCount: number
  todayOperationLogCount: number
  todayLoginFailedCount: number
  todayRiskEventCount: number
  dailyTrend: DashboardDailyTrendPoint[]
  serviceHealth: DashboardServiceHealthItem[]
  recentAuditEvents: DashboardRecentAuditEvent[]
}

export async function fetchDashboardStats() {
  const { data } = await http.get<ApiResponse<DashboardStatsResponse>>('/api/dashboard/stats')
  return data.data
}