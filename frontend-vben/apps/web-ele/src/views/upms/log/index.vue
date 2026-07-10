<script setup lang="ts">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { ElButton, ElDialog, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPage } from '#/api/upms/sys-log';
import { formatDateTime } from '#/utils/datetime';
import { operationStatusMeta } from '#/utils/log-status';

import { useColumns, useGridFormSchema } from './data';
import LogDetail from './detail.vue';

const detailVisible = ref(false);
const detailRow = ref<any>(null);

const [Grid] = useVbenVxeGrid({
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
            desc: 'created_at',
          });
          return {
            list: response?.records ?? [],
            total: response?.total ?? 0,
          };
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      search: true,
      zoom: false,
    },
  } as VxeTableGridOptions,
});

function openDetail(row: any) {
  detailRow.value = row;
  detailVisible.value = true;
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #status="{ row }">
        <ElTag :type="operationStatusMeta(row.status).type">
          {{ operationStatusMeta(row.status).label }}
        </ElTag>
      </template>

      <template #requestTime="{ row }">
        <ElTag type="info">{{ row.requestTime }}ms</ElTag>
      </template>

      <template #createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>

      <template #operation="{ row }">
        <ElButton link type="primary" @click="openDetail(row)"> 详情 </ElButton>
      </template>
    </Grid>

    <ElDialog
      v-model="detailVisible"
      destroy-on-close
      title="日志详情"
      width="720px"
    >
      <LogDetail :row="detailRow" />
    </ElDialog>
  </Page>
</template>
