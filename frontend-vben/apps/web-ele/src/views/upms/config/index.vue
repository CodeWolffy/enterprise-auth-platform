<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { defineAsyncComponent, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getPage, refresh } from '#/api/upms/config';
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

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: false,
  },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: { enabled: true, pageSize: 10 },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const response: any = await getPage({
            ...formValues,
            page: page.currentPage,
            size: page.pageSize,
            sortBy: 'createdAt',
            sortDirection: 'desc',
          });
          return {
            list: response?.records ?? [],
            total: response?.total ?? 0,
          };
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      search: true,
      zoom: false,
    },
  } as VxeTableGridOptions,
});

function onRefresh() {
  gridApi.query();
}

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
  try {
    await ElMessageBox.confirm('此操作将删除该参数，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });
    await delObj(row.id);
    ElMessage.success('删除成功');
    onRefresh();
  } catch {
    // Cancelled confirmations require no further action.
  }
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
