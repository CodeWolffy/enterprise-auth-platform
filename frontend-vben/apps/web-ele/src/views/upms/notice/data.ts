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
        placeholder: '请输入关键字',
      },
    },
    {
      component: 'Select',
      fieldName: 'workflowStatus',
      label: '公告状态',
      componentProps: {
        clearable: true,
        options: [
          { label: '草稿', value: 'DRAFT' },
          { label: '已排期', value: 'SCHEDULED' },
          { label: '已发布', value: 'PUBLISHED' },
        ],
        placeholder: '请选择',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'noticeTitle',
      minWidth: 220,
      title: '公告标题',
    },
    {
      field: 'workflowStatus',
      slots: { default: 'workflowStatus' },
      title: '状态',
      width: 100,
    },
    {
      field: 'publishTime',
      slots: { default: 'publishTime' },
      title: '发布时间',
      width: 180,
    },
    {
      field: 'createdBy',
      title: '创建人',
      width: 120,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      slots: { default: 'operation' },
      title: '操作',
      width: 240,
    },
  ];
}
