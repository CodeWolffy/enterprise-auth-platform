<script setup lang="ts">
import { Page } from '@vben/common-ui';

import { ElTag } from 'element-plus';

import { useCrudGrid } from '#/composables/useCrudGrid';
import { getPage } from '#/api/upms/sys-login-log';
import { formatDateTime } from '#/utils/datetime';
import { loginStatusMeta } from '#/utils/log-status';

import { useColumns, useGridFormSchema } from './data';

const { Grid } = useCrudGrid({
  columns: useColumns,
  defaultSortBy: '',
  fetchPage: (params) =>
    getPage({
      ...params,
      clientIp: params.ipAddr || undefined,
      desc: 'created_at',
    }),
  formOptions: {
    schema: useGridFormSchema(),
  },
});
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>

      <template #status="{ row }">
        <ElTag :type="loginStatusMeta(row.status).type">
          {{ loginStatusMeta(row.status).label }}
        </ElTag>
      </template>
    </Grid>
  </Page>
</template>
