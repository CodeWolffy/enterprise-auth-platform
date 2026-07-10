<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { computed, reactive, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/category';

const emit = defineEmits(['initPage']);

const state = reactive({
  form: {
    code: '',
    name: '',
    matchersText: '',
  },
  rules: {
    code: [{ required: true, message: '请输入分类编码', trigger: 'change' }],
    name: [{ required: true, message: '请输入分类名称', trigger: 'change' }],
    matchersText: [
      {
        validator: (
          _rule: unknown,
          value: string,
          callback: (error?: Error) => void,
        ) => {
          const hasMatcher = String(value ?? '')
            .split('\n')
            .some((item) => item.trim());
          callback(
            hasMatcher ? undefined : new Error('请至少填写一个匹配规则'),
          );
        },
        trigger: 'blur',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const targetType = ref('dict');
const isEdit = ref(false);

const targetTypeLabel = computed(() =>
  targetType.value === 'dict' ? '字典分类' : '参数分类',
);

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
      if (!response) {
        ElMessage.error('分类配置不存在');
        loading.value = false;
        return;
      }
      state.form.code = response.code;
      state.form.name = response.name;
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
  state.form.matchersText = '';
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
    :title="`${isEdit ? '修改' : '添加'}${targetTypeLabel}`"
    width="640px"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-position="top"
      :rules="state.rules"
    >
      <ElAlert
        class="category-form-tip"
        type="info"
        :closable="false"
        show-icon
        title="匹配规则支持精确匹配和前缀通配，例如 sys_status 或 sys_*。每行填写一条规则。"
      />
      <ElFormItem label="规则类型">
        <ElInput :model-value="targetTypeLabel" disabled />
      </ElFormItem>
      <ElFormItem label="分类编码" prop="code">
        <ElInput
          v-model="state.form.code"
          :disabled="isEdit"
          placeholder="例如 system、menu、tenant"
          show-word-limit
          maxlength="50"
        />
      </ElFormItem>
      <ElFormItem label="分类名称" prop="name">
        <ElInput
          v-model="state.form.name"
          placeholder="例如 系统字典"
          show-word-limit
          maxlength="100"
        />
      </ElFormItem>
      <ElFormItem label="匹配规则" prop="matchersText">
        <ElInput
          v-model="state.form.matchersText"
          type="textarea"
          :rows="5"
          placeholder="sys_*\nmenu_*\ntenant_package_*"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">取消</ElButton>
        <ElButton
          type="primary"
          @click="submitForm(formRef)"
          :loading="loading"
        >
          保存
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.category-form-tip {
  margin-bottom: 16px;
}
</style>
