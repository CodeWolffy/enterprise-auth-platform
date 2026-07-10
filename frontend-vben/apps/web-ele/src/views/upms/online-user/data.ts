import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'sessionId', title: '会话ID', width: 180 },
    { field: 'username', minWidth: 110, title: '登录用户' },
    { field: 'tenantId', minWidth: 120, title: '租户编码' },
    { field: 'activeTenantId', minWidth: 120, title: '活跃租户' },
    { field: 'clientIp', title: '登录IP', width: 140 },
    { field: 'loginLocation', minWidth: 140, title: '登录地址' },
    { field: 'device', minWidth: 160, title: '设备标识' },
    {
      field: 'issuedAt',
      slots: { default: 'issuedAt' },
      title: '签发时间',
      width: 180,
    },
    {
      field: 'expiresAt',
      slots: { default: 'expiresAt' },
      title: '过期时间',
      width: 180,
    },
    {
      field: 'lastAccessAt',
      slots: { default: 'lastAccessAt' },
      title: '最后访问',
      width: 180,
    },
    {
      field: 'active',
      slots: { default: 'status' },
      title: '状态',
      width: 90,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      slots: { default: 'operation' },
      title: '操作',
      width: 130,
    },
  ];
}
