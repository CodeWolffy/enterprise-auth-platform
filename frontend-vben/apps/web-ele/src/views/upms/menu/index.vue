<script lang="ts" setup>
import { PERMS } from '#/constants/permissions';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';
import { $t } from '@vben/locales';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj as deleteMenu, getList as getMenuList } from '#/api/upms/menu';

import { useColumns } from './data';
import Form from './modules/form.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
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
        query: async (_params) => {
          return await getMenuList();
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
    treeConfig: {
      parentField: 'parentId',
      rowField: 'id',
      transform: false,
      reserve: true,
    },
  } as VxeTableGridOptions,
};

const [Grid, gridApi] = useVbenVxeGrid(gridConfig as any);

function onRefresh() {
  gridApi.query();
}
function onEdit(row: any) {
  formDrawerApi.setData(row).open();
}
function onCreate() {
  formDrawerApi.setData({}).open();
}
function onAppend(row: any) {
  formDrawerApi.setData({ parentId: row.id }).open();
}

function onDelete(row: any) {
  ElMessageBox.confirm('此操作将删除该菜单，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    deleteMenu(row.id)
      .then(() => {
        ElMessage.success($t('ui.actionMessage.deleteSuccess', [row.name]));
        onRefresh();
      })
      .catch((error: any) => {
        const msg =
          error?.response?.data?.message ||
          error?.message ||
          '删除失败，请重试';
        ElMessage.error(msg);
      });
  });
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Grid>
      <template #toolbar-tools>
        <ElButton
          type="primary"
          @click="onCreate"
          v-access:code="PERMS.upms.menu.add"
        >
          <Plus class="size-5" />
          {{ $t('ui.actionTitle.create', ['菜单']) }}
        </ElButton>
      </template>
      <template #type="{ row }">
        <span>{{ row?.type === '1' ? '按钮' : '菜单' }}</span>
      </template>
      <template #title="{ row }">
        <div class="flex w-full items-center gap-1">
          <div class="size-5 flex-shrink-0">
            <IconifyIcon
              v-if="row.type === '1'"
              icon="carbon:security"
              class="size-full"
            />
            <IconifyIcon
              v-else-if="row?.icon"
              :icon="row.icon || 'carbon:circle-dash'"
              class="size-full"
            />
          </div>
          <span class="flex-auto">{{ row?.name }}</span>
        </div>
      </template>
      <template #operation="{ row }">
        <ElButton
          @click="onEdit(row)"
          link
          v-access:code="PERMS.upms.menu.edit"
          type="primary"
        >
          修改菜单
        </ElButton>
        <ElButton
          link
          @click="onDelete(row)"
          v-access:code="PERMS.upms.menu.del"
          type="primary"
        >
          删除菜单
        </ElButton>
        <ElButton
          link
          @click="onAppend(row)"
          v-access:code="PERMS.upms.menu.add"
          type="primary"
        >
          新增下级
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
<style lang="scss" scoped></style>
