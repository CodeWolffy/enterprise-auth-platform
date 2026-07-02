import { requestClient } from '#/api/request';

/**
 * 获取字典分页
 * 后端：GET /api/system/dicts?dictType&category&keyword&page&size&sortBy&sortDirection
 */
export async function getPage(query: any) {
  const { page, size, keyword, dictType, category, sortBy, sortDirection } =
    query ?? {};
  return requestClient.get('/system/dicts', {
    params: {
      dictType,
      category,
      keyword,
      page: page ?? 1,
      size: size ?? 10,
      sortBy: sortBy ?? 'createdAt',
      sortDirection: sortDirection ?? 'desc',
    },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取字典
 * 后端：GET /api/system/dicts/{id}
 */
export async function getById(id: string) {
  return requestClient.get(`/system/dicts/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加字典
 * 后端：POST /api/system/dicts (DictCrudRequest)
 */
export async function addObj(data: any) {
  return requestClient.post('/system/dicts', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑字典
 * 后端：PUT /api/system/dicts/{id}
 */
export async function editObj(data: any) {
  return requestClient.put(`/system/dicts/${data.id}`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除字典
 * 后端：DELETE /api/system/dicts/{id}
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/dicts/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 刷新字典缓存
 * 后端：DELETE /api/system/dicts/cache
 */
export async function refresh() {
  return requestClient.delete('/system/dicts/cache', {
    headers: {
      isSwitchTenant: false,
    },
  });
}
