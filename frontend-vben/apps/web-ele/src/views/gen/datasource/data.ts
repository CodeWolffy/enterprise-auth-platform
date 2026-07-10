import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DataSourceView } from '#/api/codegen';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '名称/库名/主机',
      componentProps: { clearable: true, placeholder: '请输入关键字' },
    },
  ];
}

export function useColumns(): VxeTableGridOptions<DataSourceView>['columns'] {
  return [
    { field: 'name', minWidth: 140, title: '名称' },
    { field: 'dbName', minWidth: 140, title: '数据库名称' },
    { field: 'host', minWidth: 140, title: '主机' },
    { field: 'port', title: '端口', width: 100 },
    { field: 'username', title: '用户名', width: 120 },
    {
      field: 'external',
      slots: { default: 'sourceType' },
      title: '类型',
      width: 90,
    },
    {
      field: 'externalAuthorized',
      slots: { default: 'authorization' },
      title: '授权',
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
      width: 280,
    },
  ];
}
