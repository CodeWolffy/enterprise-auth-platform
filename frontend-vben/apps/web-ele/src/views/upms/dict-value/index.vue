<script lang="ts" setup>
import type {
  CrudGridPageResponse,
  CrudGridQueryParams,
} from '#/composables/useCrudGrid';

import { defineAsyncComponent, ref } from 'vue';

import { Plus } from '@vben/icons';

import { ElButton, ElTag } from 'element-plus';

import { delObj, getList } from '#/api/upms/dict-value';
import { useCrudGrid } from '#/composables/useCrudGrid';
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

interface DictValueRow {
  dictId?: string;
  dictType?: string;
  dictLabel?: string;
  dictValue?: string;
  enabled?: boolean;
  id: number | string;
  remarks?: string;
  showClass?: 'danger' | 'default' | 'info' | 'primary' | 'success' | 'warning';
  sort?: number;
  updatedAt?: null | string;
}

type DictValueQuery = Record<never, never>;

async function fetchDictValuePage(
  _params: CrudGridQueryParams<DictValueQuery>,
): Promise<CrudGridPageResponse<DictValueRow>> {
  // The dict-value endpoint is list-only; preserve the drawer's unpaged view.
  const response = props.dictId ? await getList(props.dictId) : [];
  const records = Array.isArray(response) ? (response as DictValueRow[]) : [];
  return { records, total: records.length };
}

const {
  Grid,
  onDelete: baseOnDelete,
  onRefresh,
} = useCrudGrid<DictValueRow, DictValueQuery, number | string>({
  columns: useColumns(),
  deleteApi: async (id) => {
    await delObj(String(id));
    useDictStore().removeDict(props.dictType);
  },
  deleteConfirmMessage: '此操作将删除该字典键值，是否继续?',
  fetchPage: fetchDictValuePage,
  gridOptions: {
    pagerConfig: { enabled: false },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      search: false,
      zoom: false,
    },
  },
  rowKey: 'id',
});

type DictValueFormRow = Partial<DictValueRow> & {
  dictId?: string;
  dictType?: string;
};

function openForm(row?: DictValueFormRow) {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
}

function onCreate() {
  openForm({ dictId: props.dictId, dictType: props.dictType });
}

function onDelete(row: DictValueRow) {
  return baseOnDelete(row);
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
        <ElTag :type="row.showClass === 'default' ? undefined : row.showClass">
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
