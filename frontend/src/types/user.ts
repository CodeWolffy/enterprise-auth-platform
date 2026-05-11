/** 用户域类型 */

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