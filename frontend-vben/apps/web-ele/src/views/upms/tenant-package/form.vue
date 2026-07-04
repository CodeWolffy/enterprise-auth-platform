<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { addObj, editObj } from '#/api/upms/tenant-package';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

function defaultForm() {
  return {
    id: '' as number | string,
    packageCode: '',
    packageName: '',
    subtitle: '',
    originalPrice: 0,
    salesPrice: 0,
    appKey: [] as string[],
    orderNo: 0,
    status: '0',
    descriptionMd: '',
  };
}

const state = reactive({
  form: defaultForm(),
  rules: {
    packageCode: [
      { message: '请输入套餐编码', required: true, trigger: 'change' },
    ],
    packageName: [
      { message: '请输入套餐名称', required: true, trigger: 'change' },
    ],
  },
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const dictOptions = useDict('tenant_package_app_key');

const initForm = (row?: any) => {
  state.form = defaultForm();
  if (row && row.id) {
    state.form.id = row.id;
    state.form.packageCode = row.packageCode;
    state.form.packageName = row.packageName;
    state.form.subtitle = row.subtitle ?? '';
    state.form.originalPrice = Number(row.originalPrice ?? 0);
    state.form.salesPrice = Number(row.salesPrice ?? 0);
    state.form.appKey = row.appKey ? row.appKey.split(',').filter(Boolean) : [];
    state.form.orderNo = row.orderNo ?? 0;
    state.form.status = row.status ?? '0';
    state.form.descriptionMd = row.descriptionMd ?? '';
  }
  dialog.value = true;
};

const handleClose = () => {
  dialog.value = false;
  formRef.value?.resetFields();
};

function buildPayload() {
  return {
    id: state.form.id || undefined,
    packageCode: state.form.packageCode,
    packageName: state.form.packageName,
    subtitle: state.form.subtitle || null,
    salesPrice: state.form.salesPrice,
    originalPrice: state.form.originalPrice,
    descriptionMd: state.form.descriptionMd || null,
    appKey: state.form.appKey.length > 0 ? state.form.appKey.join(',') : null,
    orderNo: state.form.orderNo ?? 0,
    status: state.form.status,
  };
}

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (!valid) return;
    loading.value = true;
    const request = state.form.id
      ? editObj(buildPayload())
      : addObj(buildPayload());
    request
      .then(() => {
        ElMessage.success(state.form.id ? '修改成功' : '新增成功');
        dialog.value = false;
        emit('initPage');
      })
      .finally(() => {
        loading.value = false;
      });
  });
};

defineExpose({ initForm });
</script>

<template>
  <ElDialog
    v-model="dialog"
    :before-close="handleClose"
    :title="state.form.id ? '修改套餐' : '新增套餐'"
    width="640px"
  >
    <ElForm
      ref="formRef"
      label-width="120px"
      :model="state.form"
      :rules="state.rules"
    >
      <ElFormItem label="套餐编码" prop="packageCode">
        <ElInput
          v-model="state.form.packageCode"
          :disabled="!!state.form.id"
          maxlength="50"
        />
      </ElFormItem>
      <ElFormItem label="套餐名称" prop="packageName">
        <ElInput
          v-model="state.form.packageName"
          maxlength="50"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="副标题" prop="subtitle">
        <ElInput v-model="state.form.subtitle" maxlength="200" />
      </ElFormItem>
      <ElFormItem label="原价（元）" prop="originalPrice">
        <ElInputNumber
          v-model="state.form.originalPrice"
          :min="0"
          :precision="2"
        />
      </ElFormItem>
      <ElFormItem label="销售价（元）" prop="salesPrice">
        <ElInputNumber
          v-model="state.form.salesPrice"
          :min="0"
          :precision="2"
        />
      </ElFormItem>
      <ElFormItem label="应用标识" prop="appKey">
        <ElSelect
          v-model="state.form.appKey"
          multiple
          placeholder="请选择应用标识"
        >
          <ElOption
            v-for="item in dictOptions.tenant_package_app_key"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="排序" prop="orderNo">
        <ElInputNumber v-model="state.form.orderNo" :min="0" />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio value="0">正常</ElRadio>
          <ElRadio value="1">停用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="描述" prop="descriptionMd">
        <ElInput
          v-model="state.form.descriptionMd"
          maxlength="1000"
          :rows="3"
          type="textarea"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton
          :loading="loading"
          type="primary"
          @click="submitForm(formRef)"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
