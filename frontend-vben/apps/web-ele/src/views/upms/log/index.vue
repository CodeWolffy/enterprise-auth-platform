<script setup lang="ts">
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { ElButton, ElDialog, ElTag } from 'element-plus';

import { useCrudGrid } from '#/composables/useCrudGrid';
import { getPage } from '#/api/upms/sys-log';
import { formatDateTime } from '#/utils/datetime';
import { operationStatusMeta } from '#/utils/log-status';

import { useColumns, useGridFormSchema } from './data';
import LogDetail from './detail.vue';

const detailVisible = ref(false);
const detailRow = ref<any>(null);

const { Grid } = useCrudGrid({
  columns: useColumns,
  defaultSortBy: '',
  fetchPage: (params) =>
    getPage({
      ...params,
      desc: 'created_at',
    }),
  formOptions: {
    schema: useGridFormSchema(),
  },
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
