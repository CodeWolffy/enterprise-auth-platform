export const PERMISSIONS = {
  USER_READ: 'user:read',
  USER_WRITE: 'user:write',
  ROLE_READ: 'role:read',
  ROLE_WRITE: 'role:write',
  DEPT_READ: 'dept:read',
  DEPT_WRITE: 'dept:write',
  TENANT_READ: 'tenant:read',
  TENANT_WRITE: 'tenant:write',
  SYSTEM_READ: 'system:read',
  SYSTEM_WRITE: 'system:write',
  AUDIT_READ: 'audit:read',
  AUDIT_WRITE: 'audit:write',
  FILE_READ: 'file:read',
  FILE_WRITE: 'file:write',
  DASHBOARD_READ: 'dashboard:read',
  OPERATION_LOG_READ: 'operation-log:read',
  OPERATION_LOG_EXPORT: 'operation-log:export',
  WORKFLOW_READ: 'workflow:read',
  WORKFLOW_WRITE: 'workflow:write',
  CODEGEN_READ: 'codegen:read',
  CODEGEN_WRITE: 'codegen:write',
  CODEGEN_DOWNLOAD: 'codegen:download',
  SESSION_WRITE: 'session:write',
} as const

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS]

export const PERMISSION_CATALOG: Array<{
  code: PermissionCode
  label: string
  module: string
}> = [
  { code: PERMISSIONS.USER_READ, label: '用户查看', module: 'user' },
  { code: PERMISSIONS.USER_WRITE, label: '用户管理', module: 'user' },
  { code: PERMISSIONS.ROLE_READ, label: '角色查看', module: 'role' },
  { code: PERMISSIONS.ROLE_WRITE, label: '角色管理', module: 'role' },
  { code: PERMISSIONS.DEPT_READ, label: '部门查看', module: 'dept' },
  { code: PERMISSIONS.DEPT_WRITE, label: '部门管理', module: 'dept' },
  { code: PERMISSIONS.TENANT_READ, label: '租户查看', module: 'tenant' },
  { code: PERMISSIONS.TENANT_WRITE, label: '租户管理', module: 'tenant' },
  { code: PERMISSIONS.SYSTEM_READ, label: '系统查看', module: 'system' },
  { code: PERMISSIONS.SYSTEM_WRITE, label: '系统管理', module: 'system' },
  { code: PERMISSIONS.AUDIT_READ, label: '审计查看', module: 'audit' },
  { code: PERMISSIONS.AUDIT_WRITE, label: '审计管理', module: 'audit' },
  { code: PERMISSIONS.FILE_READ, label: '文件查看', module: 'file' },
  { code: PERMISSIONS.FILE_WRITE, label: '文件管理', module: 'file' },
  { code: PERMISSIONS.DASHBOARD_READ, label: '仪表盘查看', module: 'dashboard' },
  { code: PERMISSIONS.OPERATION_LOG_READ, label: '操作日志查看', module: 'operation-log' },
  { code: PERMISSIONS.OPERATION_LOG_EXPORT, label: '操作日志导出', module: 'operation-log' },
  { code: PERMISSIONS.WORKFLOW_READ, label: '工作流查看', module: 'workflow' },
  { code: PERMISSIONS.WORKFLOW_WRITE, label: '工作流管理', module: 'workflow' },
  { code: PERMISSIONS.CODEGEN_READ, label: '代码生成查看', module: 'codegen' },
  { code: PERMISSIONS.CODEGEN_WRITE, label: '代码生成管理', module: 'codegen' },
  { code: PERMISSIONS.CODEGEN_DOWNLOAD, label: '代码生成下载', module: 'codegen' },
  { code: PERMISSIONS.SESSION_WRITE, label: '会话管理', module: 'session' },
]

export const ALL_PERMISSIONS = PERMISSION_CATALOG.map((item) => item.code)