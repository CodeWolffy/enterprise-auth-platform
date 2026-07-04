<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { DataSourceView } from '#/api/codegen';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElSwitch,
} from 'element-plus';

import { createDataSource, updateDataSource } from '#/api/codegen';

const props = defineProps<{
  modelValue?: DataSourceView | null;
}>();

const emit = defineEmits<{
  (e: 'saved'): void;
  (e: 'close'): void;
}>();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({
  name: '',
  jdbcUrl: '',
  username: '',
  password: '',
  dbName: '',
  host: '',
  port: 3306 as number | string,
  enabled: true,
});

const isEdit = computed(() => Boolean(props.modelValue?.id));

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  jdbcUrl: [{ required: true, message: '请输入 JDBC 地址', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  dbName: [{ required: true, message: '请输入数据库名称', trigger: 'blur' }],
};

function syncFromModel() {
  const m = props.modelValue;
  form.name = m?.name ?? '';
  form.jdbcUrl = m?.jdbcUrl ?? '';
  form.username = m?.username ?? '';
  form.password = '';
  form.dbName = m?.dbName ?? '';
  form.host = m?.host ?? '';
  form.port = m?.port ?? 3306;
  form.enabled = m?.enabled ?? true;
}

watch(
  () => props.modelValue,
  () => syncFromModel(),
  { immediate: true },
);

function resetForm() {
  syncFromModel();
  formRef.value?.clearValidate();
}

function buildPayload() {
  return {
    name: form.name.trim(),
    jdbcUrl: form.jdbcUrl.trim(),
    username: form.username.trim(),
    password: form.password || undefined,
    dbName: form.dbName.trim() || undefined,
    host: form.host.trim() || undefined,
    port:
      typeof form.port === 'number'
        ? form.port
        : Number(form.port) || undefined,
    enabled: form.enabled,
  };
}

async function submit() {
  await formRef.value?.validate();
  loading.value = true;
  try {
    const payload = buildPayload();
    await (isEdit.value && props.modelValue?.id
      ? updateDataSource(props.modelValue.id, payload)
      : createDataSource(payload));
    ElMessage.success('保存成功');
    emit('saved');
  } finally {
    loading.value = false;
  }
}

defineExpose({ resetForm });
</script>

<template>
  <ElForm ref="formRef" :model="form" :rules="rules" label-width="120px">
    <ElFormItem label="名称" prop="name">
      <ElInput v-model="form.name" maxlength="64" />
    </ElFormItem>
    <ElFormItem label="JDBC 地址" prop="jdbcUrl">
      <ElInput v-model="form.jdbcUrl" placeholder="jdbc:mysql://host:3306/db" />
    </ElFormItem>
    <ElFormItem label="主机" prop="host">
      <ElInput v-model="form.host" maxlength="128" placeholder="127.0.0.1" />
    </ElFormItem>
    <ElFormItem label="端口" prop="port">
      <ElInput v-model="form.port" placeholder="3306" />
    </ElFormItem>
    <ElFormItem label="数据库名" prop="dbName">
      <ElInput v-model="form.dbName" maxlength="64" />
    </ElFormItem>
    <ElFormItem label="用户名" prop="username">
      <ElInput v-model="form.username" maxlength="64" />
    </ElFormItem>
    <ElFormItem label="密码">
      <ElInput
        v-model="form.password"
        type="password"
        show-password
        autocomplete="new-password"
        :placeholder="isEdit ? '留空则不修改' : '请输入密码'"
      />
    </ElFormItem>
    <ElFormItem label="启用">
      <ElSwitch v-model="form.enabled" />
    </ElFormItem>
    <div class="form-actions">
      <ElButton @click="emit('close')">关闭</ElButton>
      <ElButton @click="resetForm">重置</ElButton>
      <ElButton type="primary" :loading="loading" @click="submit">
        保存
      </ElButton>
    </div>
  </ElForm>
</template>

<style scoped>
.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
