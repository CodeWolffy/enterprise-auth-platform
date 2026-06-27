/** 菜单管理 API */

import { http } from '../http'
import type { TenantRequestConfig } from '../http'
import type { ApiResponse } from '@/types/api'

export type MenuType = '0' | '1'

export interface MenuTreeNode {
  id: number
  type: MenuType
  name: string
  parentId: number | null
  permission: string | null
  path: string | null
  component: string | null
  redirect: string | null
  icon: string | null
  sort: number
  outerStatus: boolean
  applicationKey: string | null
  children: MenuTreeNode[]
}

export interface MenuMutationPayload {
  parentId?: number | null
  type: MenuType
  name: string
  permission?: string | null
  path?: string | null
  component?: string | null
  redirect?: string | null
  icon?: string | null
  sort?: number | null
  outerStatus?: boolean | null
  applicationKey?: string | null
}

export interface BatchMenuActionPayload {
  actions: string[]
}

export async function queryMenuTree() {
  const { data } = await http.get<ApiResponse<MenuTreeNode[]>>('/api/menus/tree')
  return data.data
}

export async function queryGrantableMenuTree(tenantId?: string | null) {
  const { data } = await http.get<ApiResponse<MenuTreeNode[]>>('/api/menus/grantable-tree', {
    params: tenantId ? { tenantId } : undefined,
    tenantScope: 'operator',
  } satisfies TenantRequestConfig)
  return data.data
}

export async function queryMenuTemplateTree() {
  const { data } = await http.get<ApiResponse<MenuTreeNode[]>>('/api/menus/template-tree')
  return data.data
}

export async function createMenu(payload: MenuMutationPayload) {
  const { data } = await http.post<ApiResponse<MenuTreeNode>>('/api/menus', payload)
  return data.data
}

export async function updateMenu(menuId: number, payload: MenuMutationPayload) {
  const { data } = await http.put<ApiResponse<MenuTreeNode>>(`/api/menus/${menuId}`, payload)
  return data.data
}

export async function deleteMenu(menuId: number) {
  await http.delete(`/api/menus/${menuId}`)
}

export async function batchCreateMenuActions(menuId: number, payload: BatchMenuActionPayload) {
  const { data } = await http.post<ApiResponse<MenuTreeNode[]>>(`/api/menus/${menuId}/actions`, payload)
  return data.data
}

export async function sortMenu(menuId: number, sort: number) {
  const { data } = await http.put<ApiResponse<MenuTreeNode>>(`/api/menus/${menuId}/sort`, { sort })
  return data.data
}
