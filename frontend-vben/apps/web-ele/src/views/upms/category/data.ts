import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CategoryOption } from '#/types/system';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Select',
      defaultValue: 'dict',
      fieldName: 'targetType',
      label: '分类类型',
      componentProps: {
        options: [
          { label: '字典分类', value: 'dict' },
          { label: '参数分类', value: 'config' },
        ],
      },
    },
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '关键字',
      componentProps: {
        clearable: true,
        placeholder: '请输入编码或名称',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions<CategoryOption>['columns'] {
  return [
    { field: 'code', minWidth: 160, title: '分类编码' },
    { field: 'name', minWidth: 180, title: '分类名称' },
    {
      field: 'matchers',
      minWidth: 320,
      slots: { default: 'matchers' },
      title: '匹配规则',
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      slots: { default: 'operation' },
      title: '操作',
      width: 200,
    },
  ];
}
