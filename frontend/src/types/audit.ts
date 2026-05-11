/** 审计域类型 */

export interface AuditEvent {
  type: string
  operator: string
  tenantId: string
  requestId?: string | null
  clientIp?: string | null
  occurredAt: number
  details: Record<string, unknown>
}

export interface AuditPage {
  total: number
  page: number
  size: number
  records: AuditEvent[]
}

export interface AuditExportTask {
  id: number
  tenantId: string
  operator: string
  status: string
  archived: boolean
  archivable: boolean
  fileName: string
  recordCount: number
  progressPercent: number
  progressStage: string
  retentionExpired: boolean
  retentionSummary?: string | null
  expiresAt?: number | null
  requestedAt?: number | null
  completedAt?: number | null
  errorMessage?: string | null
}

export interface AuditExportPolicy {
  retentionDays: number
  maxTasks: number
}