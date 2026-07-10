<script lang="ts" setup>
import { PERMS } from '#/constants/permissions';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DeptTreeItem } from '#/api/upms/dept';

import { defineAsyncComponent, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getTreeList } from '#/api/upms/dept';
import { invokeWhenComponentReady } from '#/utils/component-ready';

import { useColumns } from './data';

const Form = defineAsyncComponent(() => import('./form.vue'));

const formRef = ref();
const formMounted = ref(false);

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
          return await getTreeList();
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
      reserve: true,
      rowField: 'id',
      transform: false,
    },
  } as VxeTableGridOptions<DeptTreeItem>,
};

const [Grid, gridApi] = useVbenVxeGrid(gridConfig as any);

const onRefresh = () => {
  gridApi.query();
};

const openForm = (row?: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
};
const onCreate = () => openForm();
const onEdit = (row: DeptTreeItem) => openForm(row);
const onDelete = (row: DeptTreeItem) => {
  ElMessageBox.confirm('此操作将删除该部门，是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确认',
    type: 'warning',
  }).then(() => {
    delObj(row.id)
      .then(() => {
        ElMessage.success('删除成功');
        onRefresh();
      })
      .catch(() => {});
  });
};
</script>

<template>
  <Page auto-content-height>
    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />

    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.dept.add"
          type="primary"
          @click="onCreate"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.enabled === 1 ? 'success' : 'info'">
          {{ row.enabled === 1 ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #operation="{ row }">
        <ElButton
          v-access:code="PERMS.upms.dept.edit"
          link
          type="primary"
          @click="onEdit(row)"
        >
          修改
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.dept.del"
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
