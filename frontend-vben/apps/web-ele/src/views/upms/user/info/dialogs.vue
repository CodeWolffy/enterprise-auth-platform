<script setup lang="ts">
import { defineAsyncComponent } from 'vue';

import { ElDialog } from 'element-plus';

defineProps<{
  activeUser?: any;
}>();
const LoginLog = defineAsyncComponent(() => import('./loginlog.vue'));
const OperationLog = defineAsyncComponent(() => import('./operatelog.vue'));
const ResetPwd = defineAsyncComponent(() => import('./resetpwd.vue'));

const loginLogVisible = defineModel<boolean>('loginLogVisible', {
  default: false,
});
const operationLogVisible = defineModel<boolean>('operationLogVisible', {
  default: false,
});
const resetPwdVisible = defineModel<boolean>('resetPwdVisible', {
  default: false,
});
</script>

<template>
  <ElDialog
    v-model="loginLogVisible"
    :title="`登录日志 - ${activeUser?.username || ''}`"
    width="920px"
  >
    <LoginLog v-if="loginLogVisible" :user-name="activeUser?.username" />
  </ElDialog>
  <ElDialog
    v-model="operationLogVisible"
    :title="`操作日志 - ${activeUser?.username || ''}`"
    width="920px"
  >
    <OperationLog v-if="operationLogVisible" :operator="activeUser?.username" />
  </ElDialog>
  <ElDialog
    v-model="resetPwdVisible"
    :title="`重置密码 - ${activeUser?.username || ''}`"
    width="520px"
  >
    <ResetPwd
      v-if="resetPwdVisible"
      :user-id="activeUser?.id"
      :username="activeUser?.username"
    />
  </ElDialog>
</template>
