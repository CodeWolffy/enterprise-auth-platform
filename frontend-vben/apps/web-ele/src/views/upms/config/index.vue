<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage, refresh } from '#/api/upms/config';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));

const state = reactive({
  queryParams: {
    category: '',
    keyword: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    sortBy: 'createdAt',
    sortDirection: 'desc',
  },
  tableData: [] as any[],
});

const showSearch = ref(true);
const loading = ref(false);
const refForm = ref();
const formMounted = ref(false);
const queryRef = ref();

const configTypeMap: Record<string, { label: string; type: 'primary' | 'warning' }> = {
  business: { label: '业务参数', type: 'primary' },
  system: { label: '系统参数', type: 'warning' },
};

const initPage = async () => {
  loading.value = true;
  const params = {
    page: state.page.currentPage,
    size: state.page.pageSize,
    sortBy: state.page.sortBy,
    sortDirection: state.page.sortDirection,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response: any) => {
      state.tableData = response?.records ?? [];
      state.page.total = response?.total ?? 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

const openForm = (row?: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(refForm, (form: any) => form.initForm(row));
};

const resetQuery = () => {
  queryRef.value?.resetFields();
  state.page.currentPage = 1;
  initPage();
};

const refreshCache = () => {
  refresh().then(() => {
    ElMessage.success('刷新成功');
  });
};

const del = (row: any) => {
  if (row.builtin) {
    ElMessage.warning('内置参数不允许删除');
    return;
  }
  ElMessageBox.confirm('此操作将删除该参数，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(row.id).then(() => {
      ElMessage.success('删除成功');
      initPage();
    });
  });
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm
        v-show="showSearch"
        ref="queryRef"
        :inline="true"
        :model="state.queryParams"
      >
        <ElFormItem label="参数分类" prop="category">
          <ElInput
            v-model="state.queryParams.category"
            clearable
            placeholder="如 auth/system"
          />
        </ElFormItem>
        <ElFormItem label="关键字" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="参数键/名称/值/备注"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton :icon="Search" type="primary" @click="initPage">
            搜索
          </ElButton>
          <ElButton :icon="Refresh" @click="resetQuery"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>

      <div class="hx-table-toolbar">
        <div>
          <ElButton
            v-access:code="'upms:sysconfig:add'"
            :icon="Plus"
            type="primary"
            @click="openForm()"
          >
            新增
          </ElButton>
          <ElButton :icon="Refresh" @click="refreshCache"> 刷新缓存 </ElButton>
        </div>
        <RightToolbar
          :refresh-btn="true"
          :search-btn="true"
          @refresh="initPage"
          @search="showSearch = !showSearch"
        />
      </div>

      <Form v-if="formMounted" ref="refForm" @init-page="initPage" />

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="configKey" label="参数键" min-width="220" />
        <ElTableColumn prop="configName" label="参数名称" min-width="160" />
        <ElTableColumn prop="category" label="分类" width="120" />
        <ElTableColumn prop="configType" label="类型" width="110">
          <template #default="scope">
            <ElTag :type="configTypeMap[scope.row.configType]?.type ?? 'primary'">
              {{ configTypeMap[scope.row.configType]?.label ?? scope.row.configType }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="configValue" label="参数值" min-width="220" show-overflow-tooltip />
        <ElTableColumn prop="enabled" label="状态" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.enabled ? 'success' : 'danger'">
              {{ scope.row.enabled ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="builtin" label="内置" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.builtin ? 'warning' : 'info'">
              {{ scope.row.builtin ? '是' : '否' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <ElTableColumn label="更新时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.updatedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-access:code="'upms:sysconfig:edit'"
              :icon="Edit"
              link
              type="primary"
              @click="openForm(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              v-access:code="'upms:sysconfig:del'"
              :disabled="scope.row.builtin"
              :icon="Delete"
              link
              type="danger"
              @click="del(scope.row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <Pagination
        v-model:limit="state.page.pageSize"
        v-model:page="state.page.currentPage"
        :total="state.page.total"
        @pagination="initPage"
      />
    </div>
  </div>
</template>
