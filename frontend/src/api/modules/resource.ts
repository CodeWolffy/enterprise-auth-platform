/** 资源/菜单管理 API */

import { http } from '../http'
import type { ApiResponse, ResourceTreeNode, ResourceType } from '@/types/auth'

export interface ResourceMutationPayload {
  parentId?: number | null
  resourceType: ResourceType
  resourceKey: string
  resourceName: string
  routeKey?: string | null
  grantKey?: string | null
  path?: string | null
  component?: string | null
  icon?: string | null
  orderNo?: number | null
  visible?: boolean | null
  enabled?: boolean | null
}

export async function queryResourceTree() {
  const { data } = await http.get<ApiResponse<ResourceTreeNode[]>>('/api/resources/tree')
  return data.data
}

export async function createResource(payload: ResourceMutationPayload) {
  const { data } = await http.post<ApiResponse<ResourceTreeNode>>('/api/resources', payload)
  return data.data
}

export async function updateResource(resourceId: number, payload: ResourceMutationPayload) {
  const { data } = await http.put<ApiResponse<ResourceTreeNode>>(`/api/resources/${resourceId}`, payload)
  return data.data
}

export async function deleteResource(resourceId: number) {
  await http.delete(`/api/resources/${resourceId}`)
}

export async function sortResource(resourceId: number, orderNo: number) {
  const { data } = await http.put<ApiResponse<ResourceTreeNode>>(`/api/resources/${resourceId}/sort`, { orderNo })
  return data.data
}