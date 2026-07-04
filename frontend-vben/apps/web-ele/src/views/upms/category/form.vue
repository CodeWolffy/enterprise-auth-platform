<script lang="ts" setup>
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
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/category';

const emit = defineEmits(['initPage']);

const state = reactive({
  form: {
    code: '',
    name: '',
    matchers: [] as string[],
    matchersText: '',
    description: '',
    sort: 0,
    enabled: true,
  },
  rules: {
    code: [{ required: true, message: '请输入分类编码', trigger: 'change' }],
    name: [{ required: true, message: '请输入分类名称', trigger: 'change' }],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const targetType = ref('dict');
const isEdit = ref(false);

const initForm = (type: string, row?: any) => {
  targetType.value = type;
  if (row && row.code) {
    isEdit.value = true;
    getDetail(type, row.code);
  } else {
    isEdit.value = false;
    resetForm();
  }
  dialog.value = true;
};

const getDetail = (type: string, code: string) => {
  loading.value = true;
  getById(type, code)
    .then((response) => {
      state.form.code = response.code;
      state.form.name = response.name;
      state.form.description = response.description || '';
      state.form.sort = response.sort || 0;
      state.form.enabled = response.enabled ?? true;
      state.form.matchers = response.matchers || [];
      state.form.matchersText = (response.matchers || []).join('\n');
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const handleClose = () => {
  resetForm(formRef.value);
};

const resetForm = (formEl?: FormInstance) => {
  state.form.code = '';
  state.form.name = '';
  state.form.matchers = [];
  state.form.matchersText = '';
  state.form.description = '';
  state.form.sort = 0;
  state.form.enabled = true;
  loading.value = false;
  dialog.value = false;
  formEl?.resetFields();
};

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (isEdit.value) {
        edit();
      } else {
        add();
      }
    }
  });
};

const normalizeMatchers = () => {
  return state.form.matchersText
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
};

const add = () => {
  const payload = {
    code: state.form.code,
    name: state.form.name,
    matchers: normalizeMatchers(),
  };
  addObj(targetType.value, payload)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

const edit = () => {
  const payload = {
    code: state.form.code,
    name: state.form.name,
    matchers: normalizeMatchers(),
  };
  editObj(targetType.value, state.form.code, payload)
    .then(() => {
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
    :title="isEdit ? '修改分类' : '添加分类'"
    width="50%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="分类编码" prop="code">
        <ElInput
          v-model="state.form.code"
          :disabled="isEdit"
          show-word-limit
          maxlength="50"
        />
      </ElFormItem>
      <ElFormItem label="分类名称" prop="name">
        <ElInput v-model="state.form.name" show-word-limit maxlength="100" />
      </ElFormItem>
      <ElFormItem label="匹配规则" prop="matchersText">
        <ElInput
          v-model="state.form.matchersText"
          type="textarea"
          :rows="4"
          placeholder="每行一个匹配规则，例如 auth.*"
        />
      </ElFormItem>
      <ElFormItem label="描述">
        <ElInput
          v-model="state.form.description"
          type="textarea"
          maxlength="200"
        />
      </ElFormItem>
      <ElFormItem label="排序序号">
        <ElInputNumber v-model="state.form.sort" :min="0" />
      </ElFormItem>
      <ElFormItem label="状态">
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
