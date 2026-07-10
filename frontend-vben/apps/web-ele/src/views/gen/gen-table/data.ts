import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useSourceFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '表名称',
      componentProps: {
        clearable: true,
        placeholder: '请输入表名称或表注释',
      },
    },
  ];
}

export function useImportedFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '关键字',
      componentProps: { clearable: true, placeholder: '表名/注释/类名' },
    },
  ];
}

export function useSourceColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'tableName', minWidth: 180, title: '表名称' },
    { field: 'tableComment', minWidth: 200, title: '表描述' },
    { field: 'engine', title: '引擎', width: 100 },
    { field: 'tableRows', title: '行数', width: 100 },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      slots: { default: 'operation' },
      title: '操作',
      width: 120,
    },
  ];
}

export function useImportedColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'tableName', minWidth: 180, title: '表名称' },
    { field: 'tableComment', minWidth: 180, title: '表描述' },
    { field: 'className', title: '类名', width: 160 },
    { field: 'moduleName', title: '模块', width: 120 },
    { field: 'functionAuthor', title: '作者', width: 100 },
    { field: 'columnCount', title: '字段数', width: 80 },
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
