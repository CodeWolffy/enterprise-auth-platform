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
  dataSourceId: number;
  tableNames: string[];
  packageName?: string;
  author?: string;
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
export async function saveTableColumns(
  tableId: number,
  columns: any[],
) {
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

// ==================== 原始表查询（白名单） ====================

/**
 * 分页查询白名单内的可生成表
 * 后端：GET /api/codegen/tables
 */
export async function getAllowlistTables(query: any) {
  return requestClient.get('/codegen/tables', {
    params: query,
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
  tableName: string;
  moduleName: string;
  packageName: string;
  className: string;
  includeBackend?: boolean;
  includeFrontend?: boolean;
  selectedFiles?: string[];
  autoRegister?: boolean;
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
  tableName: string;
  moduleName: string;
  packageName: string;
  className: string;
  includeBackend?: boolean;
  includeFrontend?: boolean;
  selectedFiles?: string[];
  overwrite?: boolean;
  autoRegister?: boolean;
}) {
  return requestClient.post('/codegen/download', data, {
    headers: {
      isSwitchTenant: false,
      'Content-Type': 'application/json',
    },
    responseReturn: 'raw',
  });
}

// ==================== 自定义模板管理 ====================

/**
 * 分页查询自定义模板
 * 后端：GET /api/codegen/templates
 */
export async function getTemplates(query: any) {
  return requestClient.get('/codegen/templates', {
    params: query,
    headers: { isSwitchTenant: false },
  });
}

/**
 * 创建自定义模板
 * 后端：POST /api/codegen/templates
 */
export async function createTemplate(payload: {
  name: string;
  language: string;
  templateCategory: string;
  pathMatchRegex: string;
  content: string;
}) {
  return requestClient.post('/codegen/templates', payload, {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 更新自定义模板
 * 后端：PUT /api/codegen/templates/{templateId}
 */
export async function updateTemplate(
  templateId: number,
  payload: {
    name: string;
    language: string;
    templateCategory: string;
    pathMatchRegex: string;
    content: string;
  },
) {
  return requestClient.put(`/codegen/templates/${templateId}`, payload, {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 查询单个自定义模板详情
 * 后端：GET /api/codegen/templates/{templateId}
 */
export async function getTemplate(templateId: number) {
  return requestClient.get(`/codegen/templates/${templateId}`, {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 删除自定义模板
 * 后端：DELETE /api/codegen/templates/{templateId}
 */
export async function deleteTemplate(templateId: number) {
  return requestClient.delete(`/codegen/templates/${templateId}`, {
    headers: { isSwitchTenant: false },
  });
}

// ==================== 代码生成（写入文件系统） ====================

/**
 * 生成代码到隔离目录
 * 后端：POST /api/codegen/generate
 */
export async function generateCode(data: {
  tableName: string;
  moduleName: string;
  packageName: string;
  className: string;
  includeBackend?: boolean;
  includeFrontend?: boolean;
  selectedFiles?: string[];
  overwrite?: boolean;
  autoRegister?: boolean;
}) {
  return requestClient.post('/codegen/generate', data, {
    headers: { isSwitchTenant: false },
  });
}
