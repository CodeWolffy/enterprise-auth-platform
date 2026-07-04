<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { getPopupContainer } from '@vben/utils';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { addObj, editObj } from '#/api/upms/tenant-package';
import { useDict } from '#/utils/dict';

const emit = defineEmits<{ success: [] }>();

const { tenant_package_app_key } = useDict('tenant_package_app_key');

const formData = ref<any>();

const schema: VbenFormSchema[] = [
  {
    component: 'Input',
    fieldName: 'packageCode',
    label: '套餐编码',
    rules: 'required',
    componentProps: { maxlength: 50 },
  },
  {
    component: 'Input',
    fieldName: 'packageName',
    label: '套餐名称',
    rules: 'required',
    componentProps: { maxlength: 50, showWordLimit: true },
  },
  {
    component: 'Input',
    fieldName: 'subtitle',
    label: '副标题',
    componentProps: { maxlength: 200 },
  },
  {
    component: 'InputNumber',
    fieldName: 'originalPrice',
    label: '原价（元）',
    componentProps: { min: 0, precision: 2 },
  },
  {
    component: 'InputNumber',
    fieldName: 'salesPrice',
    label: '销售价（元）',
    componentProps: { min: 0, precision: 2 },
  },
  {
    component: 'Select',
    fieldName: 'appKey',
    label: '应用标识',
    componentProps: {
      multiple: true,
      options: tenant_package_app_key,
      getPopupContainer,
      placeholder: '请选择应用标识',
    },
  },
  {
    component: 'InputNumber',
    fieldName: 'orderNo',
    label: '排序',
    defaultValue: 0,
    componentProps: { min: 0 },
  },
  {
    component: 'RadioGroup',
    fieldName: 'status',
    label: '状态',
    defaultValue: '0',
    componentProps: {
      options: [
        { label: '正常', value: '0' },
        { label: '停用', value: '1' },
      ],
    },
    rules: 'required',
  },
  {
    component: 'Input',
    fieldName: 'descriptionMd',
    label: '描述',
    componentProps: {
      type: 'textarea',
      rows: 3,
      maxlength: 1000,
    },
  },
];

const [Form, formApi] = useVbenForm({
  commonConfig: {
    colon: true,
    formItemClass: 'col-span-2 md:col-span-1',
  },
  schema,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2 gap-x-4',
});

const [Drawer, drawerApi] = useVbenDrawer({
  onConfirm: onSubmit,
  onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = drawerApi.getData<any>();
    if (data?.id) {
      formData.value = data;
      formApi.setValues({
        ...data,
        appKey: data.appKey ? data.appKey.split(',').filter(Boolean) : [],
        originalPrice: Number(data.originalPrice ?? 0),
        salesPrice: Number(data.salesPrice ?? 0),
        orderNo: data.orderNo ?? 0,
      });
    } else {
      formData.value = null;
      formApi.resetForm();
    }
  },
});

async function onSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) return;
  drawerApi.lock();
  try {
    const values = await formApi.getValues<any>();
    const payload = {
      ...values,
      id: formData.value?.id || undefined,
      appKey:
        Array.isArray(values.appKey) && values.appKey.length > 0
          ? values.appKey.join(',')
          : null,
      subtitle: values.subtitle || null,
      descriptionMd: values.descriptionMd || null,
    };
    await (formData.value?.id ? editObj(payload) : addObj(payload));
    ElMessage.success(formData.value?.id ? '修改成功' : '新增成功');
    drawerApi.close();
    emit('success');
  } catch (error: any) {
    ElMessage.error(
      error?.response?.data?.message || error?.message || '操作失败',
    );
  } finally {
    drawerApi.unlock();
  }
}

const getDrawerTitle = computed(() =>
  formData.value?.id ? '修改套餐' : '新增套餐',
);
</script>

<template>
  <Drawer class="w-full max-w-[640px]" :title="getDrawerTitle">
    <Form class="mx-4" />
  </Drawer>
</template>
