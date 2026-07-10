<script setup lang="ts">
import { PERMS } from '#/constants/permissions';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DataSourceView } from '#/api/codegen';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  ElButton,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  authorizeDataSource,
  deleteDataSource,
  getDataSources,
  testDataSource,
} from '#/api/codegen';

import { useColumns, useGridFormSchema } from './data';
import DataSourceForm from './form.vue';

const dialogVisible = ref(false);
const currentRow = ref<DataSourceView | null>(null);
const formRef = ref<null | { resetForm?: () => void }>(null);

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
          const rows = await getDataSources();
          const keyword = String(formValues.keyword ?? '')
            .trim()
            .toLowerCase();
          const filtered = keyword
            ? rows.filter((row) =>
                [row.name, row.dbName, row.host].some((value) =>
                  value?.toLowerCase().includes(keyword),
                ),
              )
            : rows;
          const start = (page.currentPage - 1) * page.pageSize;
          return {
            list: filtered.slice(start, start + page.pageSize),
            total: filtered.length,
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
  } as VxeTableGridOptions<DataSourceView>,
});

function openCreate() {
  currentRow.value = null;
  dialogVisible.value = true;
}

function openEdit(row: DataSourceView) {
  currentRow.value = row;
  dialogVisible.value = true;
}

async function onDelete(row: DataSourceView) {
  try {
    await ElMessageBox.confirm(`确认删除数据源「${row.name}」？`, '提示', {
      type: 'warning',
    });
    await deleteDataSource(row.id);
    ElMessage.success('已删除');
    gridApi.query();
  } catch {
    // Cancelled confirmations require no further action.
  }
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
