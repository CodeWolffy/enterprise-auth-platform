import { requestClient } from '#/api/request';

/**
 * 获取在线用户列表（全租户）
 * 后端：GET /api/auth/sessions?scope=all&page=&size=
 */
export async function getList(query: any = {}) {
  return requestClient.get('/auth/sessions', {
    params: {
      scope: 'all',
      ...query,
    },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 强制下线
 * 后端：POST /api/auth/sessions/{sessionId}/offline
 */
export async function delObj(sessionId: string) {
  return requestClient.post(`/auth/sessions/${sessionId}/offline`, null, {
    headers: {
      isSwitchTenant: false,
    },
  });
}