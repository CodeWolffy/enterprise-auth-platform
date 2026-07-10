import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '租户名称',
      componentProps: {
        clearable: true,
        placeholder: '请输入租户编码或名称',
      },
    },
    {
      component: 'Select',
      fieldName: 'platformLevel',
      label: '租户级别',
      componentProps: {
        clearable: true,
        options: [
          { label: '平台级', value: 'PLATFORM' },
          { label: '业务级', value: 'BUSINESS' },
        ],
        placeholder: '全部',
      },
    },
    {
      component: 'Select',
      fieldName: 'tenantStatus',
      label: '状态',
      componentProps: {
        clearable: true,
        options: [
          { label: '启用', value: '1' },
          { label: '停用', value: '0' },
        ],
        placeholder: '全部',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'tenantId', title: '租户编码', width: 160 },
    { field: 'name', minWidth: 140, title: '租户名称' },
    {
      field: 'platformLevel',
      slots: { default: 'platformLevel' },
      title: '租户级别',
      width: 100,
    },
    {
      field: 'contactName',
      minWidth: 150,
      slots: { default: 'contact' },
      title: '联系人',
    },
    {
      field: 'expireAt',
      slots: { default: 'expireAt' },
      title: '授权到期',
      width: 170,
    },
    {
      field: 'tenantStatus',
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
      width: 320,
    },
  ];
}
