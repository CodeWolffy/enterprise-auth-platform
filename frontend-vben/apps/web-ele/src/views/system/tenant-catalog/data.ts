import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import type { TenantPackageView } from '#/types/tenant';

export function useColumns(): VxeTableGridOptions<TenantPackageView>['columns'] {
  return [
    {
      field: 'packageCode',
      title: '套餐编码',
      minWidth: 150,
    },
    {
      field: 'packageName',
      title: '套餐名称',
      minWidth: 160,
    },
    {
      field: 'appKey',
      title: '应用标识',
      minWidth: 220,
      slots: { default: 'appKey' },
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'referencedTenantCount',
      title: '引用租户',
      width: 110,
      slots: { default: 'referencedTenantCount' },
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      title: '操作',
      width: 220,
      slots: { default: 'operation' },
    },
  ];
}