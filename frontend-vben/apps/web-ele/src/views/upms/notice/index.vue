<script setup lang="ts">
import { PERMS } from '#/constants/permissions';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { defineAsyncComponent, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  ElButton,
  ElDrawer,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getById, getPage } from '#/api/upms/notice';
import RichTextViewer from '#/components/rich-text-viewer/index.vue';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

import { useColumns, useGridFormSchema } from './data';

const Form = defineAsyncComponent(() => import('./form.vue'));

const formRef = ref();
const formMounted = ref(false);
const detailDrawer = ref(false);
const detailData = ref<any>(null);

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
            sortBy: 'createdAt',
            sortDirection: 'desc',
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

async function viewDetail(row: any) {
  try {
    detailData.value = await getById(row.id);
    detailDrawer.value = true;
  } catch (error: any) {
    ElMessage.error(error?.message || '加载详情失败');
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm('此操作将删除该公告，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });
    await delObj(row.id);
    ElMessage.success('删除成功');
    onRefresh();
  } catch {
    // Cancelled confirmations require no further action.
  }
}

function workflowStatus(row: any) {
  if (row.workflowStatus === 'PUBLISHED') {
    return { label: '已发布', type: 'success' as const };
  }
  if (row.workflowStatus === 'SCHEDULED') {
    return { label: '已排期', type: 'warning' as const };
  }
  return { label: '草稿', type: 'info' as const };
}
</script>

<template>
  <Page auto-content-height>
    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />

    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.notice.add"
          type="primary"
          @click="openForm()"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #workflowStatus="{ row }">
        <ElTag :type="workflowStatus(row).type">
          {{ workflowStatus(row).label }}
        </ElTag>
      </template>

      <template #publishTime="{ row }">
        {{ formatDateTime(row.publishTime) }}
      </template>

      <template #operation="{ row }">
        <ElButton link type="primary" @click="viewDetail(row)"> 详情 </ElButton>
        <ElButton
          v-access:code="PERMS.upms.notice.edit"
          link
          type="primary"
          @click="openForm(row)"
        >
          修改
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.notice.del"
          link
          type="danger"
          @click="onDelete(row)"
        >
          删除
        </ElButton>
      </template>
    </Grid>

    <ElDrawer v-model="detailDrawer" title="公告详情" size="600px">
      <div v-if="detailData" style="padding: 0 16px">
        <h2 style="margin: 0 0 16px; font-size: 20px">
          {{ detailData.noticeTitle }}
        </h2>
        <div class="notice-meta">
          <div>
            发布状态:
            <ElTag :type="workflowStatus(detailData).type" size="small">
              {{ workflowStatus(detailData).label }}
            </ElTag>
          </div>
          <div style="margin-top: 4px">
            发布时间: {{ formatDateTime(detailData.publishTime) }}
          </div>
          <div style="margin-top: 4px">
            创建人: {{ detailData.createdBy || '-' }}
          </div>
        </div>
        <RichTextViewer :content="detailData.noticeContent" />
      </div>
    </ElDrawer>
  </Page>
</template>

<style scoped>
.notice-meta {
  padding-bottom: 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #64748b;
  border-bottom: 1px solid #e5e7eb;
}
</style>
