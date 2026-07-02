import { requestClient } from '#/api/request';

/**
 * 仪表盘统计
 * 后端：GET /api/dashboard/stats
 */
export async function getStats() {
  return requestClient.get('/dashboard/stats');
}