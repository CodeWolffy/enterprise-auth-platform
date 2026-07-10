import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '关键字',
      componentProps: {
        clearable: true,
        placeholder: '角色名称 / 编码',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      minWidth: 150,
      title: '角色名称',
    },
    {
      field: 'code',
      minWidth: 150,
      title: '角色编码',
    },
    {
      field: 'description',
      minWidth: 180,
      title: '角色描述',
    },
    {
      field: 'dataScopeType',
      slots: { default: 'dataScope' },
      title: '数据权限',
      width: 140,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      slots: { default: 'operation' },
      title: '操作',
      width: 280,
    },
  ];
}
