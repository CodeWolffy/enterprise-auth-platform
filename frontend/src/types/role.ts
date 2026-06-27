/** 角色域类型 */

export interface RoleImpactView {
  roleId: number
  roleCode: string
  roleName: string
  assignedUserCount: number
  sampleUserIds: number[]
  assignedMenuCount: number
  deleteBlocked: boolean
  warnings: string[]
}

export interface RoleView {
  id: number
  tenantId?: string | null
  code: string
  name: string
  description?: string | null
  dataScopeType: string
  customDeptIds?: number[]
}