import { requestClient } from '#/api/request';

/**
 * 用户分页
 * 后端：GET /api/users?username&mobile&email&enabled&deptId&page&size -> PageResult<UserSummary>
 */
export async function getPage(query: any) {
  const { page, size, username, mobile, email, enabled, deptId } = query ?? {};
  return requestClient.get('/users', {
    params: {
      username,
      mobile,
      email,
      enabled,
      deptId,
      page: page ?? 1,
      size: size ?? 10,
    },
  });
}

/**
 * 新增用户
 * 后端：POST /api/users (CreateUserRequest，roleCodes 为角色编码集合，password 创建必填)
 */
export async function addObj(data: any) {
  return requestClient.post('/users', data);
}

/**
 * 修改用户
 * 后端：PUT /api/users/{userId}（password 留空表示不改）
 */
export async function editObj(data: any) {
  return requestClient.put(`/users/${data.id}`, data);
}

/**
 * 删除用户
 * 后端：DELETE /api/users/{userId}
 */
export async function delObj(id: number | string) {
  return requestClient.delete(`/users/${id}`);
}

/**
 * 分配用户角色
 * 后端：PUT /api/users/{userId}/roles (AssignRolesRequest{ roleCodes })
 */
export async function assignRoles(
  userId: number | string,
  roleCodes: string[],
) {
  return requestClient.put(`/users/${userId}/roles`, { roleCodes });
}

/**
 * 查询用户已分配的角色
 * 后端：GET /api/users/{userId}/roles
 */
export async function getAssignedRoles(userId: number | string) {
  return requestClient.get(`/users/${userId}/roles`);
}

/**
 * 修改当前账户密码
 * 后端：POST /api/account/password/change (AccountPasswordChangeRequest)
 */
export async function editPassword(data: {
  newPassword: string;
  password: string;
}) {
  return requestClient.post('/account/password/change', {
    oldPassword: data.password,
    newPassword: data.newPassword,
  });
}
