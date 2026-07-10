import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'dictLabel',
      minWidth: 140,
      title: '字典标签',
    },
    {
      field: 'dictValue',
      minWidth: 140,
      title: '字典键值',
    },
    {
      field: 'showClass',
      slots: { default: 'showClass' },
      title: '回显样式',
      width: 110,
    },
    {
      field: 'remarks',
      minWidth: 150,
      title: '备注',
    },
    {
      field: 'enabled',
      slots: { default: 'status' },
      title: '状态',
      width: 90,
    },
    {
      field: 'sort',
      title: '排序',
      width: 80,
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
      width: 180,
    },
  ];
}
