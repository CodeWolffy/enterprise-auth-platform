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

export interface ClientView {
  id: number
  tenantId: string
  clientId: string
  clientName: string
  redirectUris: string[]
  scopes: string[]
  grantTypes: string[]
  publicClient: boolean
  requirePkce: boolean
  requireConsent: boolean
  enabled: boolean
  issuedClientSecret?: string | null
  createdAt?: string
  updatedAt?: string
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
  operator: string
  occurredAt?: string | null
}

export interface CategoryOption {
  code: string
  name: string
  matchers: string[]
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
  fileName: string
  recordCount: number
  requestedAt?: string | null
  completedAt?: string | null
  errorMessage?: string | null
}

export interface FeatureFlags {
  gatewayEnabled: boolean
  nacosEnabled: boolean
  mqEnabled: boolean
  seataEnabled: boolean
  jobEnabled: boolean
  lokiEnabled: boolean
}
