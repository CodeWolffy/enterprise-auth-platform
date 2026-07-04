import { requestClient } from '#/api/request';
import { normalizePageQuery } from '#/utils/page-query';

/**
 * 获取登录日志分页
 * 后端：GET /api/logs/login
 */
export async function getPage(query: any) {
  return requestClient.get('/logs/login', {
    params: normalizePageQuery(query),
    headers: {
      isSwitchTenant: false,
    },
  });
}
