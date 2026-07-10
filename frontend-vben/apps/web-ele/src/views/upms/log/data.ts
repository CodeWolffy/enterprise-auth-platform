import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'operator',
      label: '操作用户',
      componentProps: { clearable: true, placeholder: '请输入操作用户' },
    },
    {
      component: 'Input',
      fieldName: 'clientIp',
      label: '操作地址',
      componentProps: { clearable: true, placeholder: '请输入操作地址' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '操作状态',
      componentProps: {
        clearable: true,
        options: [
          { label: '成功', value: '1' },
          { label: '失败', value: '0' },
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
    { field: 'operator', minWidth: 110, title: '操作用户' },
    { field: 'eventType', minWidth: 120, title: '操作类型' },
    { field: 'clientIp', minWidth: 130, title: '操作地址' },
    { field: 'location', minWidth: 130, title: '操作地点' },
    { field: 'method', minWidth: 180, title: '操作方法' },
    {
      field: 'status',
      slots: { default: 'status' },
      title: '操作状态',
      width: 100,
    },
    {
      field: 'requestTime',
      slots: { default: 'requestTime' },
      title: '请求时长',
      width: 100,
    },
    {
      field: 'createdAt',
      slots: { default: 'createdAt' },
      title: '创建时间',
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
      width: 100,
    },
  ];
}
