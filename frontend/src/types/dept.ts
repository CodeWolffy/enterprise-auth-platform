/** 部门域类型 */

export interface DepartmentView {
  id: number
  tenantId?: string | null
  code?: string | null
  name: string
  parentId?: number | null
  leaderUserId?: number | null
  leaderName?: string | null
  leaderPhone?: string | null
  orderNo?: number | null
  enabled?: number | null
}

export interface DepartmentPayload {
  tenantId?: string | null
  parentId?: number | null
  deptCode?: string | null
  deptName: string
  leaderUserId?: number | null
  leaderName?: string | null
  leaderPhone?: string | null
  orderNo?: number | null
  enabled?: number | null
}
