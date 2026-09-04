<script setup lang="ts">
import type { DataSourceView } from '#/api/codegen';
import type {
  CrudGridPageResponse,
  CrudGridQueryParams,
} from '#/composables/useCrudGrid';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { ElButton, ElDialog, ElMessage, ElTag } from 'element-plus';

import {
  authorizeDataSource,
  deleteDataSource,
  getDataSources,
  testDataSource,
} from '#/api/codegen';
import { useCrudGrid } from '#/composables/useCrudGrid';
import { PERMS } from '#/constants/permissions';

import { useColumns, useGridFormSchema } from './data';
import DataSourceForm from './form.vue';

const dialogVisible = ref(false);
const currentRow = ref<DataSourceView | null>(null);
const formRef = ref<null | { resetForm?: () => void }>(null);

type DataSourceQuery = {
  keyword?: string;
};

async function fetchDataSourcePage(
  params: CrudGridQueryParams<DataSourceQuery>,
): Promise<CrudGridPageResponse<DataSourceView>> {
  // The data-source API is list-only; keep the existing client-side paging.
  const rows = await getDataSources();
  const keyword = String(params.keyword ?? '')
    .trim()
    .toLowerCase();
  const filtered = keyword
    ? rows.filter((row) =>
        [row.name, row.dbName, row.host].some((value) =>
          String(value ?? '')
            .toLowerCase()
            .includes(keyword),
        ),
      )
    : rows;
  const currentPage = Number(params.page ?? 1);
  const pageSize = Number(params.size ?? 10);
  const start = (currentPage - 1) * pageSize;
  return {
    records: filtered.slice(start, start + pageSize),
    total: filtered.length,
  };
}

const {
  Grid,
  gridApi,
  onDelete: baseOnDelete,
} = useCrudGrid<DataSourceView, DataSourceQuery, number>({
  columns: useColumns(),
  deleteApi: deleteDataSource,
  deleteConfirmMessage: '确认删除该数据源？',
  deleteSuccessMessage: '已删除',
  fetchPage: fetchDataSourcePage,
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: false,
  },
  pageSize: 10,
  rowKey: 'id',
});

function openCreate() {
  currentRow.value = null;
  dialogVisible.value = true;
}

function openEdit(row: DataSourceView) {
  currentRow.value = row;
  dialogVisible.value = true;
}

function onDelete(row: DataSourceView) {
  return baseOnDelete(row, `确认删除数据源「${row.name}」？`);
}

async function onTest(row: DataSourceView) {
  const result = await testDataSource(row.id);
  ElMessage[result.success ? 'success' : 'warning'](
    result.message || '测试完成',
  );
}

async function onAuthorize(row: DataSourceView) {
  await authorizeDataSource(row.id, `已确认 ${row.name} 的数据源授权。`);
  ElMessage.success('已授权');
  gridApi.query();
}

function onDialogClosed() {
  formRef.value?.resetForm?.();
  currentRow.value = null;
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.gen.datasource.add"
          type="primary"
          @click="openCreate"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #sourceType="{ row }">
        <ElTag :type="row.external ? 'warning' : 'success'">
          {{ row.external ? '外部' : '本地' }}
        </ElTag>
      </template>

      <template #authorization="{ row }">
        <ElTag
          v-if="row.external"
          :type="row.externalAuthorized ? 'success' : 'info'"
        >
          {{ row.externalAuthorized ? '已授权' : '待授权' }}
        </ElTag>
        <span v-else class="text-gray-400">-</span>
      </template>

      <template #operation="{ row }">
        <ElButton
          v-access:code="PERMS.gen.datasource.edit"
          link
          type="primary"
          @click="openEdit(row)"
        >
          修改
        </ElButton>
        <ElButton
          v-access:code="PERMS.gen.datasource.get"
          link
          type="primary"
          @click="onTest(row)"
        >
          测试
        </ElButton>
        <ElButton
          v-if="row.external && !row.externalAuthorized"
          v-access:code="PERMS.gen.datasource.edit"
          link
          type="warning"
          @click="onAuthorize(row)"
        >
          授权
        </ElButton>
        <ElButton
          v-access:code="PERMS.gen.datasource.del"
          link
          type="danger"
          @click="onDelete(row)"
        >
          删除
        </ElButton>
      </template>
    </Grid>

    <ElDialog
      v-model="dialogVisible"
      :title="currentRow ? '修改数据源' : '新增数据源'"
      width="720px"
      @closed="onDialogClosed"
    >
      <DataSourceForm
        ref="formRef"
        :model-value="currentRow"
        @close="dialogVisible = false"
        @saved="
          dialogVisible = false;
          gridApi.query();
        "
      />
    </ElDialog>
  </Page>
</template>
