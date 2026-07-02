<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTreeSelect,
} from 'element-plus';

import { getTreeList } from '#/api/upms/dept';
import { getList as getRoleList } from '#/api/upms/sys-role';
import { addObj, editObj } from '#/api/upms/user';

const emit = defineEmits(['initPage']);

function defaultForm() {
  return {
    id: '' as number | string,
    username: '',
    displayName: '',
    mobile: '',
    email: '',
    password: '',
    deptId: undefined as any,
    enabled: true,
    roleCodes: [] as string[],
  };
}

const state = reactive<any>({
  form: defaultForm(),
  rules: {
    username: [
      { message: '请输入用户名(5-16位)', required: true, trigger: 'change' },
      { pattern: /^[a-zA-Z0-9_]{3,16}$/, message: '用户名只能包含字母、数字和下划线，3-16位', trigger: 'blur' },
    ],
    mobile: [
      { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的11位手机号', trigger: 'blur' },
    ],
    email: [
      { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
    ],
    password: [
      { min: 8, max: 64, message: '密码长度8-64位', trigger: 'blur' },
    ],
    deptId: [
      { message: '请选择部门', required: true, trigger: 'change' },
    ],
    roleCodes: [
      { type: 'array', required: true, message: '请至少分配一个角色', trigger: 'change' },
    ],
  },
  roleList: [] as any[],
  deptList: [] as any[],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const defaultProps = { children: 'children', label: 'name' };

const initForm = (row?: any) => {
  state.form = defaultForm();
  if (row && row.id) {
    state.form.id = row.id;
    state.form.username = row.username;
    state.form.displayName = row.displayName ?? '';
    state.form.mobile = row.mobile ?? '';
    state.form.email = row.email ?? '';
    state.form.deptId = row.deptId ?? undefined;
    state.form.enabled = row.enabled ?? true;
    state.form.roleCodes = Array.from(row.roles ?? []);
  }
  handleRoleList();
  handleDeptList();
  dialog.value = true;
};

const handleClose = () => {
  dialog.value = false;
  formRef.value?.resetFields();
};

function buildPayload() {
  return {
    id: state.form.id || undefined,
    username: state.form.username,
    displayName: state.form.displayName || null,
    mobile: state.form.mobile || null,
    email: state.form.email || null,
    password: state.form.password || null,
    deptId: state.form.deptId || null,
    enabled: state.form.enabled,
    roleCodes: state.form.roleCodes,
  };
}

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (!valid) return;
    if (!state.form.id && !state.form.password) {
      ElMessage.warning('请输入初始密码');
      return;
    }
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

const handleRoleList = () => {
  getRoleList().then((r: any) => {
    state.roleList = r ?? [];
  });
};
const handleDeptList = () => {
  getTreeList()
    .then((r: any) => {
      state.deptList = r ?? [];
    })
    .catch(() => {});
};

defineExpose({ initForm });
</script>

<template>
  <ElDialog
    v-model="dialog"
    :before-close="handleClose"
    :title="state.form.id ? '修改用户' : '新增用户'"
    width="640px"
  >
    <ElForm
      ref="formRef"
      label-width="120px"
      :model="state.form"
      :rules="state.rules"
    >
      <ElFormItem label="用户名" prop="username">
        <ElInput
          v-model="state.form.username"
          :disabled="!!state.form.id"
          maxlength="16"
        />
      </ElFormItem>
      <ElFormItem label="显示名称" prop="displayName">
        <ElInput v-model="state.form.displayName" maxlength="50" />
      </ElFormItem>
      <ElFormItem label="手机号" prop="mobile">
        <ElInput v-model="state.form.mobile" maxlength="20" />
      </ElFormItem>
      <ElFormItem label="邮箱" prop="email">
        <ElInput v-model="state.form.email" maxlength="128" />
      </ElFormItem>
      <ElFormItem
        :label="state.form.id ? '密码(留空不改)' : '初始密码'"
        prop="password"
      >
        <ElInput
          v-model="state.form.password"
          autocomplete="new-password"
          show-password
          type="password"
        />
      </ElFormItem>
      <ElFormItem label="部门" prop="deptId">
        <ElTreeSelect
          v-model="state.form.deptId"
          check-strictly
          :data="state.deptList"
          node-key="id"
          :props="defaultProps"
          :render-after-expand="false"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="enabled">
        <ElRadioGroup v-model="state.form.enabled">
          <ElRadio :value="true">启用</ElRadio>
          <ElRadio :value="false">停用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="角色" prop="roleCodes">
        <ElCheckboxGroup v-model="state.form.roleCodes">
          <ElCheckbox
            v-for="item in state.roleList"
            :key="item.code"
            :value="item.code"
          >
            {{ item.name }}
          </ElCheckbox>
        </ElCheckboxGroup>
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
