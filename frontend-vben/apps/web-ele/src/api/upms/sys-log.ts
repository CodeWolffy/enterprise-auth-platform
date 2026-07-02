import { requestClient } from '#/api/request';
import { normalizePageQuery } from '#/utils/page-query';

/**
 * 获取系统日志分页
 * 后端：GET /api/logs/operation
 */
export async function getPage(query: any) {
  return requestClient.get('/logs/operation', {
    params: normalizePageQuery(query),
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 后端无单条详情接口，详情使用列表行数据回填
 */
export async function getById(_id: string) {
  return Promise.resolve(null);
}