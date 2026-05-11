/** 角色域类型 */

export interface RoleView {
  id: number
  code: string
  name: string
  description?: string | null
  dataScopeType: string
  customDeptIds?: number[]
}