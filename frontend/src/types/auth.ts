export interface ApiResponse<T> {
  code: string
  success: boolean
  data: T
  message: string
}

export interface PermissionSnapshot {
  userId: number
  username: string
  tenantId: string
  roles: string[]
  permissions: string[]
  dataScopeType: string
  customDeptIds: number[]
  menus: MenuItem[]
}

export interface MenuItem {
  code: string
  title: string
  path: string
  component: string
}

export interface OAuthTokenResponse {
  access_token: string
  refresh_token?: string
  token_type: string
  expires_in: number
  scope?: string
}

export interface CookieSessionResponse {
  tenantId: string
  sessionId: string
  expiresAt: string
}

export interface CsrfTokenResponse {
  headerName: string
  parameterName: string
  token: string
}

export interface ClientView {
  id: number
  tenantId: string
  clientId: string
  clientName: string
  redirectUris: string[]
  scopes: string[]
  scopeDescriptions?: Record<string, string>
  scopeDetails?: ClientScopeDetailView[]
  scopeTypeSummary?: Record<string, number>
  grantTypes: string[]
  publicClient: boolean
  requirePkce: boolean
  requireConsent: boolean
  enabled: boolean
  integrationGuidance?: ClientIntegrationGuidanceView | null
  issuedClientSecret?: string | null
  statusHistory?: ClientStatusHistoryView[]
  createdAt?: string
  updatedAt?: string
}

export interface ClientScopeDetailView {
  scopeCode: string
  scopeName: string
  scopeDesc?: string | null
  scopeType?: string | null
  visibleInConsent: boolean
  defaultSelected: boolean
}

export interface ClientIntegrationGuidanceView {
  recommendedGrantType: string
  requirePkce: boolean
  requireConsent: boolean
  summary: string
  scopeTips: string[]
}

export interface ClientStatusHistoryView {
  eventType: string
  summary: string
  operator?: string | null
  occurredAt?: string | null
  payload?: Record<string, unknown>
}

export interface OAuthScopeView {
  id: number
  scopeCode: string
  scopeName: string
  scopeDesc?: string | null
  scopeType?: string | null
  defaultSelected: boolean
  visibleInConsent: boolean
  sortOrder?: number | null
  enabled: boolean
  updatedAt?: string | null
  referencedClientCount?: number
  referencedClientIds?: string[]
}

export interface ClientStatusPayload {
  enabled: boolean
}

export interface RotateClientSecretPayload {
  clientSecret: string
}

export interface UserSummary {
  id: number
  tenantId: string
  username: string
  displayName?: string | null
  mobile?: string | null
  email?: string | null
  deptId?: number | null
  enabled: boolean
  roles: string[]
  permissions: string[]
  dataScopeType: string
}

export interface RoleView {
  id: number
  code: string
  name: string
  description?: string | null
  dataScopeType: string
  customDeptIds?: number[]
}

export interface PermissionView {
  id: number
  resourceCode: string
  actionCode: string
  scopeCode: string
  permissionName?: string | null
  permissionCode: string
}

export interface DepartmentView {
  id: number
  code?: string | null
  name: string
  parentId?: number | null
  leaderUserId?: number | null
}

export interface TenantView {
  tenantId: string
  name: string
  platformLevel: boolean
  tenantStatus: number
  expireAt?: string | null
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
  occurredAt?: string | null
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
  updatedAt?: string | null
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
  updatedAt?: string | null
  referencedPackageCount?: number
  referencedPackageCodes?: string[]
  referencedTenantCount?: number
  referencedTenantIds?: string[]
  overrideReferenceCount?: number
}

export interface CategoryOption {
  code: string
  name: string
  matchers: string[]
}

export interface CategoryAuditView {
  eventType: string
  operator: string
  occurredAt?: string | null
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
  publishTime?: string | null
  workflowStatus: string
  createdBy: string
}

export interface AuditEvent {
  type: string
  operator: string
  tenantId: string
  requestId?: string | null
  clientIp?: string | null
  occurredAt: string
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
  expiresAt?: string | null
  requestedAt?: string | null
  completedAt?: string | null
  errorMessage?: string | null
}

export interface AuditExportPolicy {
  retentionDays: number
  maxTasks: number
}

export interface FeatureFlags {
  gatewayEnabled: boolean
  nacosEnabled: boolean
  mqEnabled: boolean
  seataEnabled: boolean
  jobEnabled: boolean
  lokiEnabled: boolean
}
