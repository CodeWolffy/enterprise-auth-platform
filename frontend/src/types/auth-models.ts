/** 认证与会话域类型 */

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
  avatarFileKey?: string | null
  avatarUrl?: string | null
  superAdmin?: boolean
}

export interface MenuItem {
  id: number
  code: string
  title: string
  name?: string | null
  path?: string | null
  component?: string | null
  permission?: string | null
  icon?: string | null
  sort?: number | null
  children?: MenuItem[]
}

export interface TokenSessionResponse {
  tenantId: string
  token: string
  expiresAt: number
  passwordChangeRequired?: boolean
  passwordChangeReason?: 'FORCE_CHANGE' | 'PASSWORD_EXPIRED' | string | null
}

export interface UserSessionView {
  sessionId: string
  username: string
  tenantId: string
  activeTenantId?: string | null
  clientIp?: string | null
  device?: string | null
  issuedAt?: number | null
  expiresAt?: number | null
  lastAccessAt?: number | null
  active: boolean
  currentSession?: boolean
}

export interface SessionPageResult {
  total: number
  page: number
  size: number
  records: UserSessionView[]
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

export interface RegisterOptionsResponse {
  defaultTenantId: string
  defaultRoleCodes: string[]
}