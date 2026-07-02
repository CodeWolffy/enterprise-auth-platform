import { requestClient } from '#/api/request';

/**
 * 角色分页
 * 后端：GET /api/roles?keyword&dataScopeType&tenantId&page&size -> PageResult<RoleView>
 */
export async function getPage(query: any) {
  const { page, size, keyword, dataScopeType, tenantId } = query ?? {};
  return requestClient.get('/roles', {
    params: {
      keyword,
      dataScopeType,
      tenantId,
      page: page ?? 1,
      size: size ?? 10,
    },
  });
}

/**
 * 角色选项列表
 * 后端：GET /api/roles/options
 */
export async function getList() {
  return requestClient.get('/roles/options');
}

/**
 * 新增角色
 * 后端：POST /api/roles (CreateRoleRequest)
 */
export async function addObj(data: any) {
  return requestClient.post('/roles', data);
}

/**
 * 编辑角色
 * 后端：PUT /api/roles/{roleId} (CreateRoleRequest，ID 在路径)
 */
export async function editObj(data: any) {
  return requestClient.put(`/roles/${data.id}`, data);
}

/**
 * 删除角色
 * 后端：DELETE /api/roles/{roleId}
 */
export async function delObj(id: number | string) {
  return requestClient.delete(`/roles/${id}`);
}

/**
 * 查询角色删除影响分析
 * 后端：GET /api/roles/{roleId}/impact
 */
export async function queryRoleImpact(id: number | string) {
  return requestClient.get(`/roles/${id}/impact`);
}

/**
 * 分配角色菜单（全量替换）
 * 后端：PUT /api/roles/{roleId}/menus (AssignMenusRequest{ menuIds })
 */
export async function saveRoleMenu(data: {
  menuIds: Array<number | string>;
  roleId: number | string;
}) {
  return requestClient.put(`/roles/${data.roleId}/menus`, {
    menuIds: data.menuIds,
  });
}
