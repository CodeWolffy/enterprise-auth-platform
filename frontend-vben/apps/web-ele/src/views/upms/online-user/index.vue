<script setup lang="ts">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page } from '@vben/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getList } from '#/api/upms/online-user';
import { PERMS } from '#/constants/permissions';
import { formatDateTime } from '#/utils/datetime';

import { useColumns } from './data';

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: true,
      pageSize: 20,
    },
    proxyConfig: {
      ajax: {
        query: async ({ page }) => {
          const response: any = await getList({
            page: page.currentPage,
            size: page.pageSize,
          });
          if (Array.isArray(response)) {
            return { list: response, total: response.length };
          }
          return {
            list: response?.records ?? [],
            total: response?.total ?? 0,
          };
        },
      },
    },
    rowConfig: {
      keyField: 'sessionId',
    },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      zoom: false,
    },
  } as VxeTableGridOptions,
});

async function forceOffline(row: any) {
  try {
    await ElMessageBox.confirm('此操作将强退该用户，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '强退',
      type: 'warning',
    });
    await delObj(row.sessionId);
    ElMessage.success('强退成功');
    gridApi.query();
  } catch {
    // Cancelled confirmations require no further action.
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #issuedAt="{ row }">
        {{ formatDateTime(row.issuedAt) }}
      </template>

      <template #expiresAt="{ row }">
        {{ formatDateTime(row.expiresAt) }}
      </template>

      <template #lastAccessAt="{ row }">
        {{ formatDateTime(row.lastAccessAt) }}
      </template>

      <template #status="{ row }">
        <ElTag :type="row.active ? 'success' : 'danger'">
          {{ row.active ? '有效' : '失效' }}
        </ElTag>
      </template>

      <template #operation="{ row }">
        <ElButton
          v-if="!row.currentSession"
          v-access:code="PERMS.upms.onlineUser.kick"
          link
          type="danger"
          @click="forceOffline(row)"
        >
          强退用户
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
