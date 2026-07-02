import type { UserInfo } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户信息
 * 后端：GET /api/auth/me -> PermissionSnapshotResponse
 * 映射为 Vben UserInfo，并透传后端扩展字段（tenantId/superAdmin/dataScopeType）。
 */
export async function getUserInfoApi() {
  const me = await requestClient.get<any>('/auth/me', {
    headers: { isSwitchTenant: false },
  });
  const roles: string[] = Array.from(me?.roles ?? []);
  const permissions: string[] = Array.from(me?.grants ?? []);
  return {
    userId: me?.userId,
    username: me?.username,
    realName: me?.username,
    avatar: me?.avatarUrl ?? '',
    roles,
    permissions,
    // 后端扩展字段，供页面/数据权限使用
    tenantId: me?.tenantId,
    operatorTenantId: me?.operatorTenantId,
    superAdmin: me?.superAdmin,
    dataScopeType: me?.dataScopeType,
    customDeptIds: me?.customDeptIds,
  } as unknown as UserInfo;
}
