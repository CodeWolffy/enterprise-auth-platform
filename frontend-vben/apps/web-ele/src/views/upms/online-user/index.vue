<script setup lang="ts">
import type {
  CrudGridPageResponse,
  CrudGridQueryParams,
} from '#/composables/useCrudGrid';
import type { SessionPageResult, UserSessionView } from '#/types/auth-models';

import { Page } from '@vben/common-ui';

import { ElButton, ElTag } from 'element-plus';

import { delObj, getList } from '#/api/upms/online-user';
import { useCrudGrid } from '#/composables/useCrudGrid';
import { PERMS } from '#/constants/permissions';
import { formatDateTime } from '#/utils/datetime';

import { useColumns } from './data';

type SessionQuery = Record<never, never>;

type SessionListResponse = SessionPageResult | UserSessionView[];

async function fetchSessionPage(
  params: CrudGridQueryParams<SessionQuery>,
): Promise<CrudGridPageResponse<UserSessionView>> {
  const response = (await getList(params)) as SessionListResponse;
  if (Array.isArray(response)) {
    return { records: response, total: response.length };
  }
  return {
    records: response.records ?? [],
    total: response.total ?? 0,
  };
}

const { Grid, onDelete: baseOnDelete } = useCrudGrid<
  UserSessionView,
  SessionQuery,
  string
>({
  columns: useColumns(),
  defaultSortBy: '',
  deleteApi: delObj,
  deleteConfirmMessage: '此操作将强退该用户，是否继续?',
  deleteSuccessMessage: '强退成功',
  fetchPage: fetchSessionPage,
  gridOptions: {
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      search: false,
      zoom: false,
    },
  },
  pageSize: 20,
  rowKey: 'sessionId',
});

function forceOffline(row: UserSessionView) {
  return baseOnDelete(row);
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
