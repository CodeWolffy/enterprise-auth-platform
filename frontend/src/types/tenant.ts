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
  userQuota?: number | null
  storageQuotaGb?: number | null
  capabilityCodes?: string[]
  capabilityDescriptions?: Record<string, string>
  logoUrl?: string | null
  contactName?: string | null
  contactPhone?: string | null
  contactEmail?: string | null
  website?: string | null
  address?: string | null
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

export interface TenantResourceScopeMenuView {
  id: number
  parentId?: number | null
  name: string
  type?: string | null
  permission?: string | null
  path?: string | null
}

export interface TenantCapabilitySummaryView {
  tenantId: string
  packageCode?: string | null
  packageName?: string | null
  packageCapabilityCodes: string[]
  effectiveCapabilityCodes: string[]
  addedCapabilities: string[]
  disabledCapabilities: string[]
  packageCapabilityCount: number
  effectiveCapabilityCount: number
  addedCapabilityCount: number
  disabledCapabilityCount: number
  visibleMenus: TenantResourceScopeMenuView[]
  grantableMenus: TenantResourceScopeMenuView[]
  visibleMenuCount: number
  grantableMenuCount: number
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
  subtitle?: string | null
  salesPrice?: number | null
  originalPrice?: number | null
  descriptionMd?: string | null
  appKey?: string | null
  orderNo?: number | null
  userQuota?: number | null
  storageQuotaGb?: number | null
  packageDesc?: string | null
  enabled: boolean
  capabilityCodes: string[]
  visibleResourceCount?: number
  grantResourceCount?: number
  sampleResourceKeys?: string[]
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
  enabled: boolean
  capabilityCodes: string[]
  visibleResourceCount: number
  grantResourceCount: number
  sampleResourceKeys: string[]
  referencedTenantCount: number
  referencedTenantIds: string[]
  rules: ImpactRuleView[]
  recommendedActions: string[]
}

export interface TenantCapabilityImpactView {
  id: number
  capabilityCode: string
  capabilityName: string
  enabled: boolean
  referencedPackageCount: number
  referencedPackageCodes: string[]
  referencedTenantCount: number
  referencedTenantIds: string[]
  overrideReferenceCount: number
  rules: ImpactRuleView[]
  recommendedActions: string[]
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