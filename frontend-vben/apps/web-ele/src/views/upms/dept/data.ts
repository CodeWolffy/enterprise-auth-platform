import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DeptTreeItem } from '#/api/upms/dept';

export function useColumns(): VxeTableGridOptions<DeptTreeItem>['columns'] {
  return [
    {
      field: 'name',
      minWidth: 180,
      title: '部门名称',
      treeNode: true,
    },
    {
      field: 'code',
      minWidth: 140,
      title: '部门编码',
    },
    {
      field: 'leaderName',
      minWidth: 120,
      title: '负责人',
    },
    {
      field: 'leaderPhone',
      minWidth: 140,
      title: '负责人手机号',
    },
    {
      field: 'orderNo',
      title: '排序',
      width: 90,
    },
    {
      field: 'enabled',
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
      title: '操作',
      width: 180,
      slots: { default: 'operation' },
    },
  ];
}
