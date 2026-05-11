/** 租户域类型 */

export interface TenantView {
  tenantId: string
  name: string
  platformLevel: boolean
  tenantStatus: number
  expireAt?: number | null
  packageCode?: string | null
  packageName?: string | null
  userQuota?: number | null
  storageQuotaGb?: number | null
  capabilityCodes?: string[]
  capabilityDescriptions?: Record<string, string>
  lifecycleNote?: string | null
}

export interface TenantCapabilityOverrideItemView {
  capabilityCode: string
  capabilityName: string
  capabilityDesc?: string | null
  packageEnabled: boolean
  overrideEnabled?: boolean | null
  effectiveEnabled: boolean
  capabilityDescOverride?: string | null
  effectiveDesc?: string | null
}

export interface TenantCapabilityOverrideView {
  tenantId: string
  packageCode?: string | null
  packageName?: string | null
  packageCapabilityCodes: string[]
  effectiveCapabilityCodes: string[]
  overrides: TenantCapabilityOverrideItemView[]
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
  capabilityChanges: number
  statusChanges: number
  profileChanges: number
  affectedFieldCounts: Record<string, number>
  recentTimeline: TenantChangeView[]
}

export interface TenantPackageView {
  id: number
  packageCode: string
  packageName: string
  userQuota?: number | null
  storageQuotaGb?: number | null
  packageDesc?: string | null
  enabled: boolean
  capabilityCodes: string[]
  updatedAt?: number | null
  referencedTenantCount?: number
  referencedTenantIds?: string[]
}

export interface TenantCapabilityView {
  id: number
  capabilityCode: string
  capabilityName: string
  capabilityDesc?: string | null
  sortOrder?: number | null
  enabled: boolean
  updatedAt?: number | null
  referencedPackageCount?: number
  referencedPackageCodes?: string[]
  referencedTenantCount?: number
  referencedTenantIds?: string[]
  overrideReferenceCount?: number
}