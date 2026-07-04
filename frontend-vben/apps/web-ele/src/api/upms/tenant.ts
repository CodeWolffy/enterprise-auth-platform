import { requestClient } from '#/api/request';
import { toInstantIso } from '#/utils/datetime';

/**
 * 获取租户列表（扁平列表，用于租户切换器）
 * 后端：GET /api/tenants
 */
export async function getList(query: any = {}) {
  return requestClient.get('/tenants', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取租户分页
 * 后端：GET /api/tenants?keyword&platformLevel&tenantStatus&page&size
 */
export async function getPage(query: any) {
  const { page, size, keyword, platformLevel, tenantStatus } = query ?? {};
  return requestClient.get('/tenants', {
    params: {
      keyword,
      platformLevel: normalizePlatformLevel(platformLevel),
      tenantStatus,
      page: page ?? 1,
      size: size ?? 10,
    },
    headers: {
      isSwitchTenant: false,
    },
  });
}

function normalizePlatformLevel(value: any) {
  if (value === 'PLATFORM') return true;
  if (value === 'BUSINESS') return false;
  return value;
}

/**
 * 根据编码获取租户
 * 后端：GET /api/tenants/{tenantId}
 */
export async function getById(id: string) {
  return requestClient.get(`/tenants/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加租户
 * 后端：POST /api/tenants (CreateTenantRequest)
 */
export async function addObj(data: any) {
  return requestClient.post('/tenants', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑租户
 * 后端：PUT /api/tenants/{tenantId}
 */
export async function editObj(data: any) {
  return requestClient.put(`/tenants/${data.tenantId}`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除租户
 * 后端：DELETE /api/tenants/{tenantId}
 */
export async function delObj(id: string) {
  return requestClient.delete(`/tenants/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取租户菜单ID集合
 * 后端：GET /api/tenants/{tenantId}/menus -> Set<Long>
 */
export async function getTenantMenuList(tenantId: string) {
  return requestClient.get(`/tenants/${tenantId}/menus`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 保存租户菜单分配（全量替换）
 * 后端：PUT /api/tenants/{tenantId}/menus { menuIds }
 */
export async function saveTenantMenu(data: {
  menuIds: number[];
  tenantId: string;
}) {
  return requestClient.put(
    `/tenants/${data.tenantId}/menus`,
    { menuIds: data.menuIds },
    {
      headers: {
        isSwitchTenant: false,
      },
    },
  );
}

/**
 * 获取租户变更历史（分页）
 * 后端：GET /api/tenants/{tenantId}/history?changeType&fieldKey&operator&from&to&page&size
 */
export async function getTenantHistory(tenantId: string, query: any) {
  const { page, size, changeType, fieldKey, operator, from, to, dateRange } =
    query ?? {};
  const [rangeStart, rangeEnd] = Array.isArray(dateRange) ? dateRange : [];
  return requestClient.get(`/tenants/${tenantId}/history`, {
    params: {
      changeType,
      fieldKey,
      operator,
      from: toInstantIso(from ?? rangeStart),
      to: toInstantIso(to ?? rangeEnd),
      page: page ?? 1,
      size: size ?? 10,
    },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取租户变更历史汇总
 * 后端：GET /api/tenants/{tenantId}/history/summary
 */
export async function getTenantHistorySummary(tenantId: string) {
  return requestClient.get(`/tenants/${tenantId}/history/summary`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}
