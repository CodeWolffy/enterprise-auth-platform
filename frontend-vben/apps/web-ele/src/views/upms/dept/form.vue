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
  ElTreeSelect,
} from 'element-plus';

import { addObj, editObj, getTreeList } from '#/api/upms/dept';

const emit = defineEmits(['initPage']);

function defaultForm() {
  return {
    id: '' as number | string,
    deptName: '',
    deptCode: '',
    parentId: 0 as any,
    leaderUserId: null as null | number,
    leaderName: '',
    leaderPhone: '',
    orderNo: 0,
    enabled: 1,
  };
}

const state = reactive<any>({
  form: defaultForm(),
  rules: {
    deptName: [
      { message: '请输入部门名称', required: true, trigger: 'change' },
    ],
  },
  deptTree: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const defaultProps = { children: 'children', label: 'name' };

const initForm = (row?: any) => {
  state.form = defaultForm();
  if (row && row.id) {
    state.form.id = row.id;
    state.form.deptName = row.name;
    state.form.deptCode = row.code ?? '';
    state.form.parentId = row.parentId ?? 0;
    state.form.leaderUserId = row.leaderUserId ?? null;
    state.form.leaderName = row.leaderName ?? '';
    state.form.leaderPhone = row.leaderPhone ?? '';
    state.form.orderNo = row.orderNo ?? 0;
    state.form.enabled = row.enabled ?? 1;
  }
  getDeptData();
  dialog.value = true;
};

const handleClose = () => {
  dialog.value = false;
  formRef.value?.resetFields();
};

function buildPayload() {
  return {
    id: state.form.id || undefined,
    parentId: state.form.parentId === 0 ? null : state.form.parentId,
    deptCode: state.form.deptCode || null,
    deptName: state.form.deptName,
    leaderUserId: state.form.leaderUserId || null,
    leaderName: state.form.leaderName || null,
    leaderPhone: state.form.leaderPhone || null,
    orderNo: state.form.orderNo ?? 0,
    enabled: state.form.enabled,
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

const getDeptData = async () => {
  const tree = (await getTreeList()) as any[];
  state.deptTree = [{ id: 0, name: '顶级部门', children: tree ?? [] }];
};

defineExpose({ initForm });
</script>

<template>
  <ElDialog
    v-model="dialog"
    :before-close="handleClose"
    :title="state.form.id ? '修改部门' : '添加部门'"
    width="600px"
  >
    <ElForm
      ref="formRef"
      label-width="120px"
      :model="state.form"
      :rules="state.rules"
    >
      <ElFormItem label="上级部门" prop="parentId">
        <ElTreeSelect
          v-model="state.form.parentId"
          check-strictly
          :data="state.deptTree"
          node-key="id"
          :props="defaultProps"
          :render-after-expand="false"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="部门名称" prop="deptName">
        <ElInput v-model="state.form.deptName" maxlength="50" show-word-limit />
      </ElFormItem>
      <ElFormItem label="部门编码" prop="deptCode">
        <ElInput v-model="state.form.deptCode" maxlength="50" />
      </ElFormItem>
      <ElFormItem label="负责人用户ID" prop="leaderUserId">
        <ElInputNumber
          v-model="state.form.leaderUserId"
          :min="0"
          style="width: 100%"
          placeholder="关联的用户ID"
        />
      </ElFormItem>
      <ElFormItem label="负责人" prop="leaderName">
        <ElInput v-model="state.form.leaderName" maxlength="50" />
      </ElFormItem>
      <ElFormItem label="负责人手机号" prop="leaderPhone">
        <ElInput v-model="state.form.leaderPhone" maxlength="32" />
      </ElFormItem>
      <ElFormItem label="排序" prop="orderNo">
        <ElInputNumber v-model="state.form.orderNo" :min="0" />
      </ElFormItem>
      <ElFormItem label="状态" prop="enabled">
        <ElRadioGroup v-model="state.form.enabled">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">停用</ElRadio>
        </ElRadioGroup>
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
