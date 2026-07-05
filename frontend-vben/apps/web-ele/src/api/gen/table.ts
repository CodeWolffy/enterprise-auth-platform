import { requestClient } from '#/api/request';

// ==================== 数据表管理（数据源下的表） ====================

/**
 * 分页查询数据源下的可导入表
 * 后端：GET /api/codegen/datasources/{dsId}/tables
 */
export async function getDataSourceTables(dsId: number, query: any) {
  return requestClient.get(`/codegen/datasources/${dsId}/tables`, {
    params: query,
    headers: { isSwitchTenant: false },
  });
}

/**
 * 导入表配置
 * 后端：POST /api/codegen/tables/import
 */
export async function importTables(payload: {
  author?: string;
  dataSourceId: number;
  packageName?: string;
  tableNames: string[];
}) {
  return requestClient.post('/codegen/tables/import', payload, {
    headers: { isSwitchTenant: false },
  });
}

// ==================== 已导入表配置管理 ====================

/**
 * 分页查询已导入表配置
 * 后端：GET /api/codegen/imported-tables
 */
export async function getImportedTables(query: any) {
  return requestClient.get('/codegen/imported-tables', {
    params: query,
    headers: { isSwitchTenant: false },
  });
}

/**
 * 查询导入表字段配置详情
 * 后端：GET /api/codegen/imported-tables/{tableId}
 */
export async function getTableConfig(tableId: number) {
  return requestClient.get(`/codegen/imported-tables/${tableId}`, {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 保存导入表字段配置
 * 后端：PUT /api/codegen/imported-tables/{tableId}/columns
 */
export async function saveTableColumns(tableId: number, columns: any[]) {
  return requestClient.put(
    `/codegen/imported-tables/${tableId}/columns`,
    { columns },
    { headers: { isSwitchTenant: false } },
  );
}

/**
 * 删除导入表配置
 * 后端：DELETE /api/codegen/imported-tables/{tableId}
 */
export async function deleteImportedTable(tableId: number) {
  return requestClient.delete(`/codegen/imported-tables/${tableId}`, {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 查询数据表字段详情
 * 后端：GET /api/codegen/tables/{tableName}
 */
export async function getTableDetail(tableName: string) {
  return requestClient.get(`/codegen/tables/${tableName}`, {
    headers: { isSwitchTenant: false },
  });
}

// ==================== 代码生成操作 ====================

/**
 * 预览生成结果
 * 后端：POST /api/codegen/preview
 */
export async function previewCode(data: {
  autoRegister?: boolean;
  className: string;
  includeBackend?: boolean;
  includeFrontend?: boolean;
  moduleName: string;
  packageName: string;
  selectedFiles?: string[];
  tableName: string;
}) {
  return requestClient.post('/codegen/preview', data, {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 下载生成产物（ZIP 包）
 * 后端：POST /api/codegen/download
 */
export async function downloadCode(data: {
  autoRegister?: boolean;
  className: string;
  includeBackend?: boolean;
  includeFrontend?: boolean;
  moduleName: string;
  overwrite?: boolean;
  packageName: string;
  selectedFiles?: string[];
  tableName: string;
}) {
  return requestClient.post('/codegen/download', data, {
    headers: {
      isSwitchTenant: false,
      'Content-Type': 'application/json',
    },
    responseReturn: 'raw',
    responseType: 'blob',
  });
}

// ==================== 代码生成（写入文件系统） ====================

/**
 * 生成代码到隔离目录
 * 后端：POST /api/codegen/generate
 */
export async function generateCode(data: {
  autoRegister?: boolean;
  className: string;
  includeBackend?: boolean;
  includeFrontend?: boolean;
  moduleName: string;
  overwrite?: boolean;
  packageName: string;
  selectedFiles?: string[];
  tableName: string;
}) {
  return requestClient.post('/codegen/generate', data, {
    headers: { isSwitchTenant: false },
  });
}
