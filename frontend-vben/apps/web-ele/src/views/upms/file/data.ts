import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: '文件名',
      componentProps: { clearable: true, placeholder: '请输入文件名' },
    },
    {
      component: 'Input',
      fieldName: 'contentType',
      label: '内容类型',
      componentProps: { clearable: true, placeholder: 'MIME类型' },
    },
    {
      component: 'Select',
      fieldName: 'storageType',
      label: '存储类型',
      componentProps: {
        clearable: true,
        options: [
          { label: 'MinIO', value: 'MINIO' },
          { label: '本地', value: 'LOCAL' },
        ],
        placeholder: '请选择',
      },
    },
    {
      component: 'Select',
      fieldName: 'visibility',
      label: '可见性',
      componentProps: {
        clearable: true,
        options: [
          { label: '仅自己', value: 'OWNER' },
          { label: '租户内', value: 'TENANT' },
          { label: '公开', value: 'PUBLIC' },
        ],
        placeholder: '请选择',
      },
    },
  ];
}

export function useColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'originalName', minWidth: 200, title: '文件名' },
    { field: 'contentType', title: '内容类型', width: 150 },
    {
      field: 'size',
      slots: { default: 'size' },
      title: '大小',
      width: 100,
    },
    {
      field: 'storageType',
      slots: { default: 'storageType' },
      title: '存储类型',
      width: 100,
    },
    { field: 'visibility', title: '可见性', width: 100 },
    {
      field: 'createdAt',
      slots: { default: 'createdAt' },
      title: '上传时间',
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
      width: 230,
    },
  ];
}
