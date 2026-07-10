import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'category',
      label: '参数分类',
      componentProps: { clearable: true, placeholder: '如 auth/system' },
    },
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '关键字',
      componentProps: {
        clearable: true,
        placeholder: '参数键/名称/值/备注',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'configKey', minWidth: 220, title: '参数键' },
    { field: 'configName', minWidth: 160, title: '参数名称' },
    { field: 'category', title: '分类', width: 120 },
    {
      field: 'configType',
      slots: { default: 'configType' },
      title: '类型',
      width: 110,
    },
    { field: 'configValue', minWidth: 220, title: '参数值' },
    {
      field: 'enabled',
      slots: { default: 'status' },
      title: '状态',
      width: 90,
    },
    {
      field: 'builtin',
      slots: { default: 'builtin' },
      title: '内置',
      width: 90,
    },
    { field: 'remark', minWidth: 180, title: '备注' },
    {
      field: 'updatedAt',
      slots: { default: 'updatedAt' },
      title: '更新时间',
      width: 180,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      slots: { default: 'operation' },
      title: '操作',
      width: 180,
    },
  ];
}
