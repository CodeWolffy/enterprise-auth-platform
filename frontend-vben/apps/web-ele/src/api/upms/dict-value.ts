import { requestClient } from '#/api/request';

/**
 * 获取字典值列表（按字典ID）
 * 后端：GET /api/system/dicts/{id}/values
 */
export async function getList(dictId: string) {
  return requestClient.get(`/system/dicts/${dictId}/values`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 按字典类型查询字典值列表
 * 后端：GET /api/system/dicts/values?dictType=
 */
export async function getByType(dictType: string) {
  return requestClient.get('/system/dicts/values', {
    params: { dictType },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 根据ID获取字典值
 * 后端：GET /api/system/dict-values/{valueId}
 */
export async function getById(id: string) {
  return requestClient.get(`/system/dict-values/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 添加字典值
 * 后端：POST /api/system/dicts/{id}/values
 */
export async function addObj(data: { dictId: string; [key: string]: any }) {
  return requestClient.post(`/system/dicts/${data.dictId}/values`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑字典值
 * 后端：PUT /api/system/dict-values/{valueId}
 */
export async function editObj(data: { id?: string; valueId?: string; [key: string]: any }) {
  const valueId = data.valueId ?? data.id;
  return requestClient.put(`/system/dict-values/${valueId}`, data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 删除字典值
 * 后端：DELETE /api/system/dict-values/{valueId}
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/dict-values/${id}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}