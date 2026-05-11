/** 系统设置域类型 */

export interface CategoryOption {
  code: string
  name: string
  matchers: string[]
}

export interface CategoryAuditView {
  eventType: string
  operator: string
  occurredAt?: number | null
  payloadJson: string
}

export interface CategoryAnalysis {
  code: string
  name: string
  targetType: string
  matchers: string[]
  referenceCount: number
  sampleReferences: string[]
  recentAudits: CategoryAuditView[]
  trend: { date: string; count: number }[]
}

export interface DictView {
  id: number
  dictType: string
  category: string
  dictCode: string
  dictValue: string
  createdBy: string
}

export interface ConfigView {
  id: number
  configKey: string
  category: string
  configName: string
  configValue: string
  createdBy: string
}

export interface NoticeView {
  id: number
  noticeTitle: string
  noticeContent: string
  published: boolean
  publishTime?: number | null
  workflowStatus: string
  createdBy: string
}