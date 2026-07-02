import { requestClient } from '#/api/request';

/**
 * 获取公告分页列表
 * 后端：GET /api/system/notices?published&workflowStatus&keyword&page&size&sortBy&sortDirection
 */
export async function getPage(query: any) {
  const {
    page,
    size,
    published,
    workflowStatus,
    keyword,
    sortBy,
    sortDirection,
  } = query ?? {};
  return requestClient.get('/system/notices', {
    params: {
      published,
      workflowStatus,
      keyword,
      page: page ?? 1,
      size: size ?? 10,
      sortBy,
      sortDirection,
    },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取公告详情
 * 后端：GET /api/system/notices/{id}
 */
export async function getById(id: string) {
  return requestClient.get(`/system/notices/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 新增公告
 * 后端：POST /api/system/notices
 */
export async function addObj(data: any) {
  return requestClient.post('/system/notices', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 修改公告
 * 后端：PUT /api/system/notices/{id}
 */
export async function editObj(data: any) {
  return requestClient.put(`/system/notices/${data.id}`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除公告
 * 后端：DELETE /api/system/notices/{id}
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/notices/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}
