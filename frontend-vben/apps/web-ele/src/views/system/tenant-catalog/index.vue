<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TenantPackageView } from '#/types/tenant';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getList } from '#/api/upms/tenant-package';

import { useColumns } from './data';
import Detail from './modules/detail.vue';
import Form from './modules/form.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [DetailDrawer, detailDrawerApi] = useVbenDrawer({
  connectedComponent: Detail,
  destroyOnClose: true,
});

const gridConfig = {
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async () => {
          return await getList();
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      zoom: false,
    },
  } as VxeTableGridOptions,
};

const [Grid, gridApi] = useVbenVxeGrid(gridConfig as any);

function onCreate() {
  formDrawerApi.setData({}).open();
}

function onEdit(row: TenantPackageView) {
  formDrawerApi.setData(row).open();
}

function onDetail(row: TenantPackageView) {
  detailDrawerApi.setData(row).open();
}

function onRefresh() {
  gridApi.query();
}

async function onDelete(row: TenantPackageView) {
  if ((row.referencedTenantCount ?? 0) > 0) {
    ElMessage.warning('该套餐仍被租户引用，不能删除');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除套餐 ${row.packageName} 吗？`,
      '删除确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await delObj(row.id);
  ElMessage.success('套餐已删除');
  onRefresh();
}

function appKeys(value?: null | string) {
  if (!value?.trim()) return [];
  return value
    .split(/[,;\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}
</script>

<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <DetailDrawer />

    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="'upms:tenantpackage:add'"
          type="primary"
          @click="onCreate"
        >
          <Plus class="size-5" />
          新增套餐
        </ElButton>
      </template>

      <template #appKey="{ row }">
        <div class="flex flex-wrap gap-1">
          <ElTag v-for="key in appKeys(row.appKey)" :key="key" effect="plain">
            {{ key }}
          </ElTag>
          <span
            v-if="appKeys(row.appKey).length === 0"
            class="text-muted-foreground"
          >
            未配置
          </span>
        </div>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === '0' ? 'success' : 'info'">
          {{ row.status === '0' ? '正常' : '停用' }}
        </ElTag>
      </template>

      <template #referencedTenantCount="{ row }">
        {{ row.referencedTenantCount ?? 0 }}
      </template>

      <template #operation="{ row }">
        <ElButton link type="primary" @click="onDetail(row)"> 详情 </ElButton>
        <ElButton
          v-access:code="'upms:tenantpackage:edit'"
          link
          type="primary"
          @click="onEdit(row)"
        >
          编辑
        </ElButton>
        <ElButton
          v-access:code="'upms:tenantpackage:del'"
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
