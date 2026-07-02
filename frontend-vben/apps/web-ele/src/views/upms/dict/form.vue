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
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/dict';
import { useDictStore } from '#/store/dict';

const emit = defineEmits(['initPage']);

const defaultForm = () => ({
  id: '',
  dictType: '',
  description: '',
  remarks: '',
  enabled: true,
});

const state = reactive({
  form: defaultForm(),
  rules: {
    dictType: [
      {
        required: true,
        message: '请输入字典类型',
        trigger: 'change',
      },
    ],
    description: [
      {
        required: true,
        message: '请输入字典描述',
        trigger: 'change',
      },
    ],
    enabled: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = (row: any) => {
  state.form = defaultForm();
  if (row && row.id) {
    getDetail(row.id);
  }
  dialog.value = true;
};

const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = response?.dict ?? response;
    })
    .catch(() => {
      loading.value = false;
    });
};

/** 关闭事件 */
const handleClose = () => {
  resetForm(formRef.value);
};

/** 重置表单 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form = defaultForm();
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};

/** 提交按钮 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    }
  });
};

/** 新增 */
const add = () => {
  addObj(state.form)
    .then(() => {
      useDictStore().cleanDict();
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

/** 修改 */
const edit = () => {
  editObj(state.form)
    .then(() => {
      useDictStore().cleanDict();
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

defineExpose({
  initForm,
});
</script>

<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改字典' : '添加字典'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
      status-icon
    >
      <ElFormItem label="字典类型" prop="dictType">
        <ElInput
          v-model="state.form.dictType"
          show-word-limit
          maxlength="64"
        />
      </ElFormItem>
      <ElFormItem label="描述" prop="description">
        <ElInput
          v-model="state.form.description"
          show-word-limit
          maxlength="255"
        />
      </ElFormItem>
      <ElFormItem label="备注" prop="remarks">
        <ElInput
          v-model="state.form.remarks"
          show-word-limit
          type="textarea"
          maxlength="255"
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="enabled">
        <ElRadioGroup v-model="state.form.enabled">
          <ElRadio :value="true">正常</ElRadio>
          <ElRadio :value="false">停用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton
          type="primary"
          @click="submitForm(formRef)"
          :loading="loading"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>