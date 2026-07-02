import { requestClient } from '#/api/request';
import type { TenantPackageImpactView, TenantPackageView } from '#/types/tenant';

/**
 * 套餐列表
 * 后端：GET /api/tenant-catalog/packages -> List<TenantPackageView>（扁平，无分页）
 */
export async function getList() {
  return requestClient.get<TenantPackageView[]>('/tenant-catalog/packages');
}

/**
 * 新增套餐
 * 后端：POST /api/tenant-catalog/packages (TenantPackageCrudRequest)
 */
export async function addObj(data: any) {
  return requestClient.post<TenantPackageView>('/tenant-catalog/packages', data);
}

/**
 * 修改套餐
 * 后端：PUT /api/tenant-catalog/packages/{id}
 */
export async function editObj(data: any) {
  return requestClient.put<TenantPackageView>(`/tenant-catalog/packages/${data.id}`, data);
}

/**
 * 删除套餐
 * 后端：DELETE /api/tenant-catalog/packages/{id}
 */
export async function delObj(id: number | string) {
  return requestClient.delete(`/tenant-catalog/packages/${id}`);
}

/**
 * 套餐影响分析
 * 后端：GET /api/tenant-catalog/packages/{id}/impact
 */
export async function queryTenantPackageImpact(id: number) {
  return requestClient.get<TenantPackageImpactView>(`/tenant-catalog/packages/${id}/impact`);
}
