import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: '用户名',
      componentProps: { clearable: true, placeholder: '用户名' },
    },
    {
      component: 'Input',
      fieldName: 'mobile',
      label: '手机号',
      componentProps: { clearable: true, placeholder: '手机号' },
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: '邮箱',
      componentProps: { clearable: true, placeholder: '邮箱' },
    },
    {
      component: 'Select',
      fieldName: 'enabled',
      label: '状态',
      componentProps: {
        clearable: true,
        options: [
          { label: '启用', value: 'true' },
          { label: '停用', value: 'false' },
        ],
        placeholder: '全部',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'username', minWidth: 120, title: '用户名' },
    { field: 'displayName', minWidth: 120, title: '显示名称' },
    { field: 'mobile', minWidth: 130, title: '手机号' },
    { field: 'email', minWidth: 180, title: '邮箱' },
    { field: 'deptName', minWidth: 140, title: '部门' },
    {
      field: 'roles',
      minWidth: 160,
      slots: { default: 'roles' },
      title: '角色',
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
      slots: { default: 'operation' },
      title: '操作',
      width: 320,
    },
  ];
}
