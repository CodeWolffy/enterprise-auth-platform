import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'dictType',
      label: '字典类型',
      componentProps: { clearable: true, placeholder: '请输入字典类型' },
    },
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '关键字',
      componentProps: { clearable: true, placeholder: '字典编码/值' },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'dictType', minWidth: 160, title: '字典类型' },
    { field: 'description', minWidth: 180, title: '字典描述' },
    { field: 'valueCount', title: '字典值数量', width: 120 },
    { field: 'remarks', minWidth: 160, title: '备注' },
    {
      field: 'enabled',
      slots: { default: 'status' },
      title: '状态',
      width: 90,
    },
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
      width: 250,
    },
  ];
}
