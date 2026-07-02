import { requestClient } from '#/api/request';

const SYS = { headers: { isSwitchTenant: false } };

export interface SystemMenu {
  id: number | string;
  name: string;
  parentId: number | string;
  type?: '0' | '1' | 'link' | 'embedded';
  path?: string;
  component?: string;
  icon?: string;
  permission?: string;
  applicationKey?: string;
  sort?: number;
  children?: SystemMenu[];
  meta?: Record<string, any>;
  linkSrc?: string;
}

/**
 * 平台菜单模板树（菜单管理用）
 * 后端：GET /api/menus/tree
 */
export async function getList() {
  return requestClient.get('/menus/tree', SYS);
}

/**
 * 菜单详情
 * 后端：GET /api/menus/{id}
 */
export async function getById(id: number | string) {
  return requestClient.get(`/menus/${id}`, SYS);
}

/**
 * 新增菜单
 * 后端：POST /api/menus
 */
export async function addObj(data: any) {
  return requestClient.post('/menus', data, SYS);
}

/**
 * 编辑菜单
 * 后端：PUT /api/menus/{id}
 */
export async function editObj(data: any) {
  return requestClient.put(`/menus/${data.id}`, data, SYS);
}

/**
 * 删除菜单
 * 后端：DELETE /api/menus/{id}
 */
export async function delObj(id: number | string) {
  return requestClient.delete(`/menus/${id}`, SYS);
}

/**
 * 可授权菜单树（角色/租户分配使用）
 * 后端：GET /api/menus/grantable-tree -> List<MenuTreeNode>
 */
export async function getTenantMenu() {
  return requestClient.get('/menus/grantable-tree', SYS);
}

/**
 * 角色已分配菜单 ID 集合
 * 后端：GET /api/roles/{roleId}/menus -> Set<Long>（直接是 ID 数组）
 */
export async function getMenusByRole(roleId: number | string) {
  return requestClient.get(`/roles/${roleId}/menus`);
}

/**
 * 菜单模板树（用于批量创建菜单）
 * 后端：GET /api/menus/template-tree
 */
export async function getMenuTemplateTree() {
  return requestClient.get('/menus/template-tree', SYS);
}

/**
 * 批量创建菜单按钮/权限
 * 后端：POST /api/menus/{menuId}/actions
 */
export async function batchCreateMenuActions(
  menuId: number | string,
  payload: any[],
) {
  return requestClient.post(`/menus/${menuId}/actions`, payload, SYS);
}

/**
 * 菜单排序
 * 后端：PUT /api/menus/{menuId}/sort
 */
export async function sortMenu(menuId: number | string, sort: number) {
  return requestClient.put(
    `/menus/${menuId}/sort`,
    { sort },
    SYS,
  );
}
