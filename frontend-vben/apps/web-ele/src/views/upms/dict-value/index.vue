<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getList } from '#/api/upms/dict-value';
import { useDictStore } from '#/store/dict';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

const props = defineProps({
  dictId: {
    type: String,
    default: '',
  },
  dictType: {
    type: String,
    default: '',
  },
});

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));

const state = reactive({
  queryParams: {},
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const formMounted = ref(false);

const initPage = async () => {
  if (props.dictId) {
    loading.value = true;
    await getList(props.dictId)
      .then((response) => {
        state.tableData = response ?? [];
        loading.value = false;
      })
      .catch(() => {
        loading.value = false;
      });
  }
};

const openForm = (row: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
};

/** 新增按钮 */
const add = () => {
  openForm({ dictId: props.dictId, dictType: props.dictType });
};

/** 修改按钮 */
const edit = (row: any) => openForm(row);

/** 删除按钮 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该字典键值，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        useDictStore().removeDict(props.dictType);
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

initPage();
</script>

<template>
  <div class="layout-padding-auto layout-padding-view">
    <!-- 工具栏 -->
    <div class="hx-table-toolbar">
      <div>
        <ElButton
          type="primary"
          v-access:code="'upms:sysdict:add'"
          @click="add"
          :icon="Plus"
        >
          新增
        </ElButton>
      </div>
      <RightToolbar
        :search-btn="false"
        :refresh-btn="true"
        @search="showSearch = !showSearch"
        @refresh="initPage"
      />
    </div>
    <Form v-if="formMounted" ref="formRef" @init-page="initPage" />
    <!-- 列表 -->
    <ElTable v-loading="loading" :data="state.tableData" border>
      <ElTableColumn prop="dictLabel" label="字典标签" align="center" />
      <ElTableColumn prop="dictValue" label="字典键值" align="center" />
      <ElTableColumn prop="showClass" label="回显样式" align="center" width="110">
        <template #default="scope">
          <ElTag :type="scope.row.showClass === 'default' ? '' : scope.row.showClass">
            {{ scope.row.showClass || 'default' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="remarks" label="备注" align="center" />

      <ElTableColumn prop="enabled" label="状态" align="center">
        <template #default="scope">
          <ElTag :type="scope.row.enabled ? 'success' : 'danger'">
            {{ scope.row.enabled ? '正常' : '停用' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="sort" label="排序" align="center" />
      <ElTableColumn label="更新时间" width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.updatedAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="180" align="center">
        <template #default="scope">
          <ElButton
            link
            type="primary"
            v-access:code="'upms:sysdict:edit'"
            @click="edit(scope.row)"
            :icon="Edit"
          >
            修改
          </ElButton>
          <ElButton
            link
            type="danger"
            v-access:code="'upms:sysdict:del'"
            @click="del(scope.row.id)"
            :icon="Delete"
          >
            删除
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>