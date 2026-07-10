<script lang="ts" setup>
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
import { delObj, getPage, refresh } from '#/api/upms/dict';
import { PERMS } from '#/constants/permissions';
import { useDictStore } from '#/store/dict';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

import { useColumns, useGridFormSchema } from './data';

const Form = defineAsyncComponent(() => import('./form.vue'));
const DictValue = defineAsyncComponent(() => import('../dict-value/index.vue'));

const formRef = ref();
const formMounted = ref(false);
const dictId = ref('');
const currentDict = ref<any>(null);
const dictValueDrawer = ref(false);

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
    rowConfig: { keyField: 'id' },
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

async function refreshCache() {
  await refresh();
  useDictStore().cleanDict();
  ElMessage.success('刷新成功');
}

function openValues(row: any) {
  currentDict.value = row;
  dictId.value = row.id;
  dictValueDrawer.value = true;
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm('此操作将删除该字典，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });
    await delObj(row.id);
    useDictStore().cleanDict();
    ElMessage.success('删除成功');
    onRefresh();
  } catch {
    // Cancelled confirmations require no further action.
  }
}
</script>

<template>
  <Page auto-content-height>
    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />

    <Grid>
      <template #toolbar-actions>
        <ElButton @click="refreshCache">刷新缓存</ElButton>
      </template>

      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.dict.add"
          type="primary"
          @click="openForm()"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.enabled ? 'success' : 'danger'">
          {{ row.enabled ? '正常' : '停用' }}
        </ElTag>
      </template>

      <template #updatedAt="{ row }">
        {{ formatDateTime(row.updatedAt) }}
      </template>

      <template #operation="{ row }">
        <ElButton
          v-access:code="PERMS.upms.dict.edit"
          link
          type="primary"
          @click="openForm(row)"
        >
          修改
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.dict.del"
          link
          type="danger"
          @click="onDelete(row)"
        >
          删除
        </ElButton>
        <ElButton link type="primary" @click="openValues(row)">
          键值列表
        </ElButton>
      </template>
    </Grid>

    <ElDrawer v-model="dictValueDrawer" direction="rtl" size="46%">
      <template #header>
        <div>
          <h4 class="m-0">字典键值</h4>
          <span class="text-xs text-gray-500">
            {{ currentDict?.dictType }} / {{ currentDict?.description }}
          </span>
        </div>
      </template>
      <DictValue
        v-if="dictValueDrawer"
        :dict-id="String(dictId)"
        :dict-type="currentDict?.dictType ?? ''"
      />
    </ElDrawer>
  </Page>
</template>
