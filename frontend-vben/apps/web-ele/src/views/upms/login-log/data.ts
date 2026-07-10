import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'userName',
      label: '登录用户',
      componentProps: { clearable: true, placeholder: '请输入登录用户' },
    },
    {
      component: 'Input',
      fieldName: 'ipAddr',
      label: '登录地址',
      componentProps: { clearable: true, placeholder: '请输入登录地址' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '操作状态',
      componentProps: {
        clearable: true,
        options: [
          { label: '成功', value: 'SUCCESS' },
          { label: '失败', value: 'FAILED' },
          { label: '锁定', value: 'LOCKED' },
        ],
        placeholder: '请选择',
      },
    },
    {
      component: 'Input',
      fieldName: 'tenantId',
      label: '租户ID',
      componentProps: { clearable: true, placeholder: '租户编码' },
    },
    {
      component: 'DatePicker',
      fieldName: 'dateRange',
      label: '时间范围',
      componentProps: {
        endPlaceholder: '结束时间',
        rangeSeparator: '-',
        startPlaceholder: '开始时间',
        type: 'datetimerange',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'userName', minWidth: 120, title: '登录用户' },
    { field: 'ipAddr', minWidth: 130, title: '登录地址' },
    { field: 'location', minWidth: 140, title: '登录地点' },
    {
      field: 'createdAt',
      slots: { default: 'createdAt' },
      title: '登录时间',
      width: 180,
    },
    { field: 'browser', minWidth: 120, title: '浏览器' },
    { field: 'os', minWidth: 120, title: '操作系统' },
    {
      field: 'status',
      slots: { default: 'status' },
      title: '操作状态',
      width: 100,
    },
    { field: 'msg', minWidth: 180, title: '操作描述' },
  ];
}
