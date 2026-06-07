/** 菜单管理 API */

import { http } from '../http'
import type { ApiResponse } from '@/types/api'

export type MenuType = 'DIR' | 'MENU' | 'BUTTON' | 'API'

export interface MenuTreeNode {
  id: number
  menuType: MenuType
  resourceKey: string
  menuName: string
  parentId: number | null
  ancestors: string
  routeKey: string | null
  grantKey: string | null
  path: string | null
  component: string | null
  redirect: string | null
  icon: string | null
  orderNo: number
  visible: boolean
  enabled: boolean
  system: boolean
  children: MenuTreeNode[]
}

export interface MenuMutationPayload {
  parentId?: number | null
  menuType: MenuType
  resourceKey: string
  menuName: string
  routeKey?: string | null
  grantKey?: string | null
  path?: string | null
  component?: string | null
  redirect?: string | null
  icon?: string | null
  orderNo?: number | null
  visible?: boolean | null
  enabled?: boolean | null
}

export async function queryMenuTree() {
  const { data } = await http.get<ApiResponse<MenuTreeNode[]>>('/api/menus/tree')
  return data.data
}

export async function queryGrantableMenuTree() {
  const { data } = await http.get<ApiResponse<MenuTreeNode[]>>('/api/menus/grantable-tree')
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

export async function sortMenu(menuId: number, orderNo: number) {
  const { data } = await http.put<ApiResponse<MenuTreeNode>>(`/api/menus/${menuId}/sort`, { orderNo })
  return data.data
}