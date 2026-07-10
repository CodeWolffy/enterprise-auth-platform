<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { defineAsyncComponent, ref } from 'vue';

import { Plus } from '@vben/icons';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getList } from '#/api/upms/dict-value';
import { PERMS } from '#/constants/permissions';
import { useDictStore } from '#/store/dict';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

import { useColumns } from './data';

const props = withDefaults(
  defineProps<{
    dictId?: string;
    dictType?: string;
  }>(),
  {
    dictId: '',
    dictType: '',
  },
);

const Form = defineAsyncComponent(() => import('./form.vue'));

const formRef = ref();
const formMounted = ref(false);

const [Grid, gridApi] = useVbenVxeGrid({
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
          return props.dictId ? await getList(props.dictId) : [];
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
});

function onRefresh() {
  gridApi.query();
}

function openForm(row: any) {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
}

function onCreate() {
  openForm({ dictId: props.dictId, dictType: props.dictType });
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm('此操作将删除该字典键值，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });
    await delObj(row.id);
    useDictStore().removeDict(props.dictType);
    ElMessage.success('删除成功');
    onRefresh();
  } catch {
    // Cancelled confirmations require no further action.
  }
}
</script>

<template>
  <div class="h-full">
    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />

    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.dict.add"
          type="primary"
          @click="onCreate"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #showClass="{ row }">
        <ElTag :type="row.showClass === 'default' ? '' : row.showClass">
          {{ row.showClass || 'default' }}
        </ElTag>
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
      </template>
    </Grid>
  </div>
</template>
