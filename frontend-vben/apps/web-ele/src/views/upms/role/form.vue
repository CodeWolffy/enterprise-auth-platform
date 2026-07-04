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
  ElTreeSelect,
} from 'element-plus';

import { getTreeList } from '#/api/upms/dept';
import { addObj, editObj } from '#/api/upms/sys-role';

const emit = defineEmits(['initPage']);

const DATA_SCOPE_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '本部门', value: 'DEPT' },
  { label: '本部门及以下', value: 'DEPT_AND_CHILDREN' },
  { label: '仅本人', value: 'SELF' },
  { label: '自定义', value: 'CUSTOM' },
];

function defaultForm() {
  return {
    id: '' as number | string,
    roleName: '',
    roleCode: '',
    roleDesc: '',
    dataScopeType: 'SELF',
    customDeptIds: [] as number[],
  };
}

const state = reactive({
  form: defaultForm(),
  rules: {
    roleName: [
      { message: '请输入角色名称', required: true, trigger: 'change' },
    ],
    roleCode: [
      { message: '请输入角色编码', required: true, trigger: 'change' },
    ],
    dataScopeType: [
      { message: '请选择数据权限范围', required: true, trigger: 'change' },
    ],
    customDeptIds: [
      {
        validator: (_rule: any, value: number[], callback: any) => {
          if (
            state.form.dataScopeType === 'CUSTOM' &&
            (!value || value.length === 0)
          ) {
            callback(new Error('自定义数据权限需至少选择一个部门'));
          } else {
            callback();
          }
        },
        trigger: 'change',
      },
    ],
  },
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const deptTree = ref<any[]>([]);
const deptProps = { children: 'children', label: 'name', value: 'id' };

const loadDeptTree = () => {
  if (deptTree.value.length > 0) return;
  getTreeList()
    .then((r: any) => {
      deptTree.value = r ?? [];
    })
    .catch(() => {});
};

// 后端无单条查询接口，直接使用列表行数据回填（RoleView -> 表单）
const initForm = (row?: any) => {
  state.form = defaultForm();
  if (row && row.id) {
    state.form.id = row.id;
    state.form.roleCode = row.code;
    state.form.roleName = row.name;
    state.form.roleDesc = row.description ?? '';
    state.form.dataScopeType = row.dataScopeType ?? 'SELF';
    state.form.customDeptIds = row.customDeptIds ?? [];
  }
  loadDeptTree();
  dialog.value = true;
};

const handleClose = () => {
  dialog.value = false;
  formRef.value?.resetFields();
};

function buildPayload() {
  return {
    id: state.form.id || undefined,
    roleCode: state.form.roleCode,
    roleName: state.form.roleName,
    roleDesc: state.form.roleDesc,
    dataScopeType: state.form.dataScopeType,
    // 仅 CUSTOM 范围下发自定义部门
    customDeptIds:
      state.form.dataScopeType === 'CUSTOM' ? state.form.customDeptIds : [],
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
    :title="state.form.id ? '修改角色' : '添加角色'"
    width="600px"
  >
    <ElForm
      ref="formRef"
      label-width="120px"
      :model="state.form"
      :rules="state.rules"
    >
      <ElFormItem label="角色名称" prop="roleName">
        <ElInput v-model="state.form.roleName" maxlength="50" show-word-limit />
      </ElFormItem>
      <ElFormItem label="角色编码" prop="roleCode">
        <ElInput
          v-model="state.form.roleCode"
          :disabled="!!state.form.id"
          maxlength="50"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="数据权限" prop="dataScopeType">
        <ElSelect v-model="state.form.dataScopeType" style="width: 100%">
          <ElOption
            v-for="o in DATA_SCOPE_OPTIONS"
            :key="o.value"
            :label="o.label"
            :value="o.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem
        v-if="state.form.dataScopeType === 'CUSTOM'"
        label="自定义部门"
        prop="customDeptIds"
      >
        <ElTreeSelect
          v-model="state.form.customDeptIds"
          :data="deptTree"
          :props="deptProps"
          :render-after-expand="false"
          check-strictly
          clearable
          collapse-tags
          collapse-tags-tooltip
          multiple
          node-key="id"
          placeholder="请选择可见部门"
          show-checkbox
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="角色描述" prop="roleDesc">
        <ElInput
          v-model="state.form.roleDesc"
          maxlength="200"
          :rows="2"
          show-word-limit
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
