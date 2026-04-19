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
  operatorTenantId?: string
  roles: string[]
  grants: string[]
  dataScopeType: string
  customDeptIds: number[]
  menus: MenuItem[]
  superAdmin?: boolean
}

export interface MenuItem {
  id: number
  code: string
  title: string
  path?: string | null
  component?: string | null
  routeKey?: string | null
  icon?: string | null
  orderNo?: number | null
  children?: MenuItem[]
}

export interface CookieSessionResponse {
  tenantId: string
  sessionId: string
  expiresAt: number
}

export interface CaptchaResponse {
  captchaId: string
  backgroundImage: string
  sliderImage: string
  backgroundImageWidth: number
  backgroundImageHeight: number
  sliderImageWidth: number
  sliderImageHeight: number
}

export interface CaptchaTrackPoint {
  x: number
  y: number
  t: number
  type: string
}

export interface CaptchaTrackPayload {
  bgImageWidth: number
  bgImageHeight: number
  templateImageWidth: number
  templateImageHeight: number
  startTime: number
  stopTime: number
  left: number
  top: number
  trackList: CaptchaTrackPoint[]
}

export interface CsrfTokenResponse {
  headerName: string
  parameterName: string
  token: string
}

export interface RegisterOptionsResponse {
  defaultTenantId: string
  defaultRoleCodes: string[]
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

export type ResourceType = 'DIR' | 'MENU' | 'BUTTON' | 'API'

export interface ResourceTreeNode {
  id: number
  resourceKey: string
  resourceName: string
  resourceType: ResourceType
  parentId?: number | null
  ancestors?: string | null
  routeKey?: string | null
  grantKey?: string | null
  path?: string | null
  component?: string | null
  icon?: string | null
  orderNo?: number | null
  visible: boolean
  enabled: boolean
  system: boolean
  children?: ResourceTreeNode[]
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

export interface FeatureFlags {
  gatewayEnabled: boolean
  nacosEnabled: boolean
  mqEnabled: boolean
  seataEnabled: boolean
  jobEnabled: boolean
  lokiEnabled: boolean
}
