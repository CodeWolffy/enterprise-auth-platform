<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import { editObj } from '#/api/upms/user';

const props = defineProps<{
  userId?: number | string;
  username?: string;
}>();

const emit = defineEmits(['upSuccess']);

const loading = ref(false);
const formRef = ref<FormInstance>();

const validateNewPwd = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请输入新密码'));
  } else if (value.length < 8) {
    callback(new Error('新密码至少 8 位'));
  } else {
    if (state.form.confirmPassword) {
      formRef.value?.validateField('confirmPassword');
    }
    callback();
  }
};

const validateConfirmPwd = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请再次输入新密码'));
  } else if (value === state.form.newPassword) {
    callback();
  } else {
    callback(new Error('两次输入的新密码不一致'));
  }
};

const state = reactive({
  form: {
    newPassword: '',
    confirmPassword: '',
  },
  rules: {
    newPassword: [{ validator: validateNewPwd, trigger: 'blur' }],
    confirmPassword: [{ validator: validateConfirmPwd, trigger: 'blur' }],
  },
});

const submit = () => {
  if (!props.userId || !props.username) {
    ElMessage.warning('缺少目标用户信息');
    return;
  }
  formRef.value?.validate((valid) => {
    if (!valid) return;
    loading.value = true;
    // 管理员重置：复用 PUT /users/{id}，仅下发用户名 + 新密码，
    // 后端对留空字段保持原值，并将该用户标记为下次登录需改密。
    editObj({
      id: props.userId,
      username: props.username,
      password: state.form.newPassword,
    })
      .then(() => {
        ElMessage.success(
          props.username
            ? `已重置 ${props.username} 的密码`
            : '密码重置成功',
        );
        state.form.newPassword = '';
        state.form.confirmPassword = '';
        formRef.value?.resetFields();
        emit('upSuccess');
      })
      .finally(() => {
        loading.value = false;
      });
  });
};
</script>

<template>
  <div class="reset-password-panel">
    <ElAlert
      type="warning"
      :closable="false"
      show-icon
      title="管理员重置密码后，该用户的所有登录会话将被强制下线，并要求其下次登录时修改密码。"
    />
    <ElDescriptions :column="1" border>
      <ElDescriptionsItem label="用户 ID">
        {{ props.userId || '-' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="用户名">
        {{ props.username || '-' }}
      </ElDescriptionsItem>
    </ElDescriptions>

    <ElForm
      ref="formRef"
      :model="state.form"
      :rules="state.rules"
      label-width="120px"
      status-icon
    >
      <ElFormItem label="新密码" prop="newPassword">
        <ElInput
          v-model="state.form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="至少 8 位"
        />
      </ElFormItem>
      <ElFormItem label="确认新密码" prop="confirmPassword">
        <ElInput
          v-model="state.form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
        />
      </ElFormItem>
      <div class="actions">
        <ElButton type="primary" :loading="loading" @click="submit">
          确认重置
        </ElButton>
      </div>
    </ElForm>
  </div>
</template>

<style scoped lang="scss">
.reset-password-panel {
  display: grid;
  gap: 16px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
