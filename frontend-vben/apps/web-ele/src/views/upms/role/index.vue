<script setup lang="ts">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { defineAsyncComponent, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getPage, queryRoleImpact } from '#/api/upms/sys-role';
import { PERMS } from '#/constants/permissions';
import { invokeWhenComponentReady } from '#/utils/component-ready';

import { useColumns, useGridFormSchema } from './data';

const Form = defineAsyncComponent(() => import('./form.vue'));
const RoleMenu = defineAsyncComponent(() => import('./rolemenu.vue'));

const DATA_SCOPE_LABELS: Record<string, string> = {
  ALL: '全部',
  CUSTOM: '自定义',
  DEPT: '本部门',
  DEPT_AND_CHILDREN: '本部门及以下',
  SELF: '仅本人',
};

const formRef = ref();
const roleMenuRef = ref();
const formMounted = ref(false);
const menuMounted = ref(false);

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

function onRefresh() {
  gridApi.query();
}

function openForm(row?: any) {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
}

function onAuth(row: any) {
  menuMounted.value = true;
  void invokeWhenComponentReady(roleMenuRef, (menu: any) => {
    void menu.initRoleMenu(row);
  });
}

async function doDelete(id: number | string) {
  await delObj(id);
  ElMessage.success('删除成功');
  onRefresh();
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm('此操作将删除该角色，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });

    const impact: any = await queryRoleImpact(row.id).catch(() => null);
    const userCount = impact?.assignedUserCount ?? 0;
    const menuCount = impact?.assignedMenuCount ?? 0;
    if (userCount > 0 || menuCount > 0) {
      const details = [];
      if (userCount > 0) details.push(`${userCount} 个用户引用`);
      if (menuCount > 0) details.push(`${menuCount} 个菜单授权`);
      await ElMessageBox.confirm(
        `该角色存在关联引用（${details.join('、')}），删除后相关用户将失去此角色权限。是否继续？`,
        '删除影响提示',
        {
          cancelButtonText: '取消',
          confirmButtonText: '强制删除',
          type: 'warning',
        },
      );
    }
    await doDelete(row.id);
  } catch {
    // Cancelled confirmations require no further action.
  }
}
</script>

<template>
  <Page auto-content-height>
    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />
    <RoleMenu v-if="menuMounted" ref="roleMenuRef" @init-page="onRefresh" />

    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.role.add"
          type="primary"
          @click="openForm()"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #dataScope="{ row }">
        <ElTag>
          {{ DATA_SCOPE_LABELS[row.dataScopeType] ?? row.dataScopeType }}
        </ElTag>
      </template>

      <template #operation="{ row }">
        <ElButton
          v-if="row.code !== 'ADMIN'"
          v-access:code="PERMS.upms.role.edit"
          link
          type="primary"
          @click="openForm(row)"
        >
          修改
        </ElButton>
        <ElButton
          v-if="row.code !== 'ADMIN'"
          v-access:code="PERMS.upms.role.del"
          link
          type="danger"
          @click="onDelete(row)"
        >
          删除
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.role.edit"
          link
          type="primary"
          @click="onAuth(row)"
        >
          分配菜单
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
