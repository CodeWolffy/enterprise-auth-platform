<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { requestClient } from '#/api/request';
import { delObj, getPage, upload } from '#/api/upms/file';
import { PERMS } from '#/constants/permissions';
import { formatDateTime } from '#/utils/datetime';

import { useColumns, useGridFormSchema } from './data';

const uploadRef = ref<HTMLInputElement>();
const defaultUploadVisibility = 'OWNER';

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: false,
  },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: true,
      pageSize: 10,
    },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const response: any = await getPage({
            ...formValues,
            page: page.currentPage,
            size: page.pageSize,
          });
          return {
            list: response?.records ?? [],
            total: response?.total ?? 0,
          };
        },
      },
    },
    rowConfig: {
      keyField: 'fileKey',
    },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      search: true,
      zoom: false,
    },
  } as VxeTableGridOptions,
});

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  gridApi.setLoading(true);
  try {
    await upload(file, defaultUploadVisibility);
    ElMessage.success('上传成功');
    gridApi.query();
  } finally {
    gridApi.setLoading(false);
    input.value = '';
  }
}

async function downloadFile(row: any) {
  try {
    const response = await requestClient.get(`/files/${row.fileKey}`, {
      responseType: 'blob',
      headers: { isSwitchTenant: false },
    });
    const blob = new Blob([response]);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = row.originalName || row.fileKey;
    anchor.click();
    URL.revokeObjectURL(url);
    ElMessage.success('文件下载成功');
  } catch (error: any) {
    ElMessage.error(error?.message || '下载失败');
  }
}

async function copyUrl(url: string) {
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success('公开链接已复制');
  } catch {
    ElMessage.error('复制失败');
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm('此操作将删除该文件，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });
    await delObj(row.fileKey);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // Cancelled confirmations require no further action.
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.file.add"
          type="primary"
          @click="uploadRef?.click()"
        >
          <Plus class="size-5" />
          上传文件
        </ElButton>
        <input ref="uploadRef" hidden type="file" @change="handleUpload" />
      </template>

      <template #size="{ row }">
        {{ (row.size / 1024).toFixed(1) }} KB
      </template>

      <template #storageType="{ row }">
        <ElTag>{{ row.storageType }}</ElTag>
      </template>

      <template #createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>

      <template #operation="{ row }">
        <ElButton link type="primary" @click="downloadFile(row)">
          下载
        </ElButton>
        <ElButton v-if="row.url" link type="primary" @click="copyUrl(row.url)">
          复制链接
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.file.del"
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
