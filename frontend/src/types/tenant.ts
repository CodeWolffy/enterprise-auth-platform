/** 租户域类型 */

export interface TenantView {
  tenantId: string
  name: string
  platformLevel: boolean
  tenantStatus: number
  authBeginAt?: number | null
  expireAt?: number | null
  packageCode?: string | null
  packageName?: string | null
  logoUrl?: string | null
  contactName?: string | null
  contactPhone?: string | null
  contactEmail?: string | null
  website?: string | null
  address?: string | null
  lifecycleNote?: string | null
}

export interface TenantChangeView {
  id: number
  tenantId: string
  changeType: string
  fieldKey: string
  oldValue?: string | null
  newValue?: string | null
  summary: string
  impactSummary?: string | null
  operator: string
  occurredAt?: number | null
}

export interface TenantHistorySummaryView {
  tenantId: string
  totalChanges: number
  packageChanges: number
  menuChanges: number
  statusChanges: number
  profileChanges: number
  affectedFieldCounts: Record<string, number>
  recentTimeline: TenantChangeView[]
}

export interface TenantPackageView {
  id: number
  packageCode: string
  packageName: string
  subtitle?: string | null
  salesPrice?: number | null
  originalPrice?: number | null
  descriptionMd?: string | null
  appKey?: string | null
  orderNo?: number | null
  packageDesc?: string | null
  status: '0' | '1'
  updatedAt?: number | null
  referencedTenantCount?: number
  referencedTenantIds?: string[]
}

export interface ImpactRuleView {
  ruleCode: string
  level: 'ERROR' | 'WARN' | string
  hit: boolean
  message: string
  relatedCount: number
  blocking: boolean
}

export interface TenantPackageImpactView {
  id: number
  packageCode: string
  packageName: string
  status: '0' | '1'
  appKey?: string | null
  referencedTenantCount: number
  referencedTenantIds: string[]
  rules: ImpactRuleView[]
  recommendedActions: string[]
}
