/** 部门域类型 */

export interface DepartmentView {
  id: number
  code?: string | null
  name: string
  parentId?: number | null
  leaderUserId?: number | null
}