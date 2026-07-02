<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { addObj, editObj } from '#/api/upms/config';

const emit = defineEmits(['initPage']);

function defaultForm() {
  return {
    id: '' as number | string,
    configKey: '',
    configName: '',
    configValue: '',
    configType: 'business',
    enabled: true,
    builtin: false,
    remark: '',
  };
}

const state = reactive({
  form: defaultForm(),
  rules: {
    configKey: [{ message: '请输入参数键', required: true, trigger: 'change' }],
    configValue: [
      { message: '请输入参数值', required: true, trigger: 'change' },
    ],
  },
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = (row?: any) => {
  state.form = defaultForm();
  if (row && row.id) {
    state.form.id = row.id;
    state.form.configKey = row.configKey;
    state.form.configName = row.configName ?? '';
    state.form.configValue = row.configValue ?? '';
    state.form.configType = row.configType ?? 'business';
    state.form.enabled = row.enabled ?? true;
    state.form.builtin = row.builtin ?? false;
    state.form.remark = row.remark ?? '';
  }
  dialog.value = true;
};

const handleClose = () => {
  dialog.value = false;
  formRef.value?.resetFields();
};

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (!valid) return;
    loading.value = true;
    const payload = {
      id: state.form.id || undefined,
      configKey: state.form.configKey,
      configName: state.form.configName,
      configValue: state.form.configValue,
      configType: state.form.configType,
      enabled: state.form.enabled,
      builtin: state.form.builtin,
      remark: state.form.remark,
    };
    const request = state.form.id ? editObj(payload) : addObj(payload);
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
    :title="state.form.id ? '修改参数' : '新增参数'"
    width="600px"
  >
    <ElForm
      ref="formRef"
      label-width="100px"
      :model="state.form"
      :rules="state.rules"
    >
      <ElFormItem label="参数键" prop="configKey">
        <ElInput
          v-model="state.form.configKey"
          :disabled="!!state.form.id"
          maxlength="128"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="参数名称" prop="configName">
        <ElInput
          v-model="state.form.configName"
          maxlength="128"
          placeholder="请输入参数名称"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="参数值" prop="configValue">
        <ElInput
          v-model="state.form.configValue"
          maxlength="500"
          :rows="3"
          placeholder="请输入参数值"
          show-word-limit
          type="textarea"
        />
      </ElFormItem>
      <ElFormItem label="参数类型" prop="configType">
        <ElSelect v-model="state.form.configType" placeholder="请选择参数类型">
          <ElOption label="业务参数" value="business" />
          <ElOption label="系统参数" value="system" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="启用状态" prop="enabled">
        <ElSwitch
          v-model="state.form.enabled"
          active-text="启用"
          inactive-text="停用"
        />
      </ElFormItem>
      <ElFormItem label="内置参数" prop="builtin">
        <ElSwitch
          v-model="state.form.builtin"
          active-text="是"
          inactive-text="否"
        />
      </ElFormItem>
      <ElFormItem label="备注" prop="remark">
        <ElInput
          v-model="state.form.remark"
          maxlength="255"
          :rows="3"
          placeholder="请输入备注"
          show-word-limit
          type="textarea"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton :loading="loading" type="primary" @click="submitForm(formRef)">
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
