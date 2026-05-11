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

export interface TokenSessionResponse {
  tenantId: string
  token: string
  expiresAt: number
}

export interface UserSessionView {
  sessionId: string
  username: string
  tenantId: string
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