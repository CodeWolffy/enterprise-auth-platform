<script lang="ts" setup>
import { defineAsyncComponent, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { ElButton, ElMessage, ElTag } from 'element-plus';

import { delObj, getPage, refresh } from '#/api/upms/config';
import { useCrudGrid } from '#/composables/useCrudGrid';
import { PERMS } from '#/constants/permissions';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

import { useColumns, useGridFormSchema } from './data';

const Form = defineAsyncComponent(() => import('./form.vue'));

const formRef = ref();
const formMounted = ref(false);

const configTypeMap: Record<
  string,
  { label: string; type: 'primary' | 'warning' }
> = {
  business: { label: '业务参数', type: 'primary' },
  system: { label: '系统参数', type: 'warning' },
};

const {
  Grid,
  onRefresh,
  onDelete: baseDelete,
} = useCrudGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  columns: useColumns(),
  fetchPage: getPage,
  deleteApi: delObj,
  deleteConfirmMessage: '此操作将删除该参数，是否继续?',
});

function openForm(row?: any) {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
}

async function refreshCache() {
  await refresh();
  ElMessage.success('刷新成功');
}

async function onDelete(row: any) {
  if (row.builtin) {
    ElMessage.warning('内置参数不允许删除');
    return;
  }
  await baseDelete(row);
}
</script>

<template>
  <Page auto-content-height>
    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />

    <Grid>
      <template #toolbar-actions>
        <ElButton @click="refreshCache">刷新缓存</ElButton>
      </template>

      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.config.add"
          type="primary"
          @click="openForm()"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #configType="{ row }">
        <ElTag :type="configTypeMap[row.configType]?.type ?? 'primary'">
          {{ configTypeMap[row.configType]?.label ?? row.configType }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.enabled ? 'success' : 'danger'">
          {{ row.enabled ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #builtin="{ row }">
        <ElTag :type="row.builtin ? 'warning' : 'info'">
          {{ row.builtin ? '是' : '否' }}
        </ElTag>
      </template>

      <template #updatedAt="{ row }">
        {{ formatDateTime(row.updatedAt) }}
      </template>

      <template #operation="{ row }">
        <ElButton
          v-access:code="PERMS.upms.config.edit"
          link
          type="primary"
          @click="openForm(row)"
        >
          修改
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.config.del"
          :disabled="row.builtin"
          link
          type="danger"
          @click="onDelete(row)"
        >
          删除
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
