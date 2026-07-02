import { requestClient } from '#/api/request';

/**
 * 参数分页
 * 后端：GET /api/system/configs/page?keyword&category&page&size&sortBy&sortDirection
 */
export async function getPage(query: any) {
  const { page, size, keyword, category, sortBy, sortDirection } = query ?? {};
  return requestClient.get('/system/configs/page', {
    params: {
      keyword,
      category,
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
 * 参数详情
 * 后端：GET /api/system/configs/{id}
 */
export async function getById(id: number | string) {
  return requestClient.get(`/system/configs/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 新增参数
 * 后端：POST /api/system/configs (ConfigCrudRequest{ configKey*, configName, configValue* })
 */
export async function addObj(data: any) {
  return requestClient.post('/system/configs', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 修改参数
 * 后端：PUT /api/system/configs/{id}
 */
export async function editObj(data: any) {
  return requestClient.put(`/system/configs/${data.id}`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除参数
 * 后端：DELETE /api/system/configs/{id}
 */
export async function delObj(id: number | string) {
  return requestClient.delete(`/system/configs/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 批量删除参数
 * 后端：DELETE /api/system/configs?ids=1,2
 */
export async function delObjs(ids: Array<number | string>) {
  return requestClient.delete('/system/configs', {
    params: { ids: ids.join(',') },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 刷新参数缓存
 * 后端：DELETE /api/system/configs/cache
 */
export async function refresh() {
  return requestClient.delete('/system/configs/cache', {
    headers: {
      isSwitchTenant: false,
    },
  });
}
