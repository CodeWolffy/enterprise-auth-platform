/** 资源/权限域类型 */

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