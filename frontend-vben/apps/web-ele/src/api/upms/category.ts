import { requestClient } from '#/api/request';

/**
 * 获取分类选项列表（按目标类型）
 * 后端：GET /api/system/categories/{targetType}
 */
export async function getOptions(targetType: string) {
  return requestClient.get(`/system/categories/${targetType}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取全部分类选项（按目标类型分组）
 * 后端：GET /api/system/categories -> Record<string, CategoryOption[]>
 */
export async function getAllCategories() {
  return requestClient.get('/system/categories', {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 获取单个分类选项
 * 后端无独立详情接口，复用分类选项列表定位当前 code。
 */
export async function getById(targetType: string, code: string) {
  const options = await getOptions(targetType);
  return (options as any[]).find((item) => item.code === code) ?? null;
}

/**
 * 获取分类配置分析
 * 后端：GET /api/system/categories/{targetType}/{code}/analysis
 */
export async function getAnalysis(targetType: string, code: string) {
  return requestClient.get(`/system/categories/${targetType}/${code}/analysis`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 新增分类选项
 * 后端：POST /api/system/categories/{targetType}
 */
export async function addObj(targetType: string, data: any) {
  return requestClient.post(`/system/categories/${targetType}`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 修改分类选项
 * 后端：PUT /api/system/categories/{targetType}/{code}
 */
export async function editObj(targetType: string, code: string, data: any) {
  return requestClient.put(`/system/categories/${targetType}/${code}`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除分类选项
 * 后端：DELETE /api/system/categories/{targetType}/{code}
 */
export async function delObj(targetType: string, code: string) {
  return requestClient.delete(`/system/categories/${targetType}/${code}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}