<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/upms/sys-log';
import { formatDateTime } from '#/utils/datetime';
import { operationStatusMeta } from '#/utils/log-status';

import LogDetail from './detail.vue';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const showSearch = ref(true);
const loading = ref(false);
const detailVisible = ref(false);
const detailRow = ref<any>(null);

const state = reactive({
  queryParams: {
    operator: '',
    clientIp: '',
    status: '',
    tenantId: '',
    dateRange: [] as any[],
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'created_at',
  },
  tableData: [],
});

const initPage = async () => {
  loading.value = true;
  const params: any = {
    page: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    operator: state.queryParams.operator || undefined,
    clientIp: state.queryParams.clientIp || undefined,
    status: state.queryParams.status || undefined,
    tenantId: state.queryParams.tenantId || undefined,
    dateRange: state.queryParams.dateRange,
  };

  await getPage(params)
    .then((response: any) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

initPage();

/** 重置搜索表单 */
const resetQuery = () => {
  state.queryParams.operator = '';
  state.queryParams.clientIp = '';
  state.queryParams.status = '';
  state.queryParams.tenantId = '';
  state.queryParams.dateRange = [];
  initPage();
};

const openDetail = (row: any) => {
  detailRow.value = row;
  detailVisible.value = true;
};
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm :model="state.queryParams" :inline="true" v-show="showSearch">
        <ElFormItem label="操作用户" prop="operator">
          <ElInput
            v-model="state.queryParams.operator"
            clearable
            placeholder="请输入操作用户"
            style="width: 160px"
          />
        </ElFormItem>
        <ElFormItem label="操作地址" prop="clientIp">
          <ElInput
            v-model="state.queryParams.clientIp"
            clearable
            placeholder="请输入操作地址"
            style="width: 140px"
          />
        </ElFormItem>
        <ElFormItem label="操作状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择"
            style="width: 120px"
          >
            <ElOption label="成功" value="1" />
            <ElOption label="失败" value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="租户ID" prop="tenantId">
          <ElInput
            v-model="state.queryParams.tenantId"
            clearable
            placeholder="租户编码"
            style="width: 140px"
          />
        </ElFormItem>
        <ElFormItem label="时间范围" prop="dateRange">
          <ElDatePicker
            v-model="state.queryParams.dateRange"
            type="datetimerange"
            range-separator="-"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div></div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="operator" label="操作用户" />
        <ElTableColumn prop="eventType" label="操作类型" />
        <ElTableColumn prop="clientIp" label="操作地址" />
        <ElTableColumn prop="location" label="操作地点" />
        <ElTableColumn prop="method" label="操作方法" />
        <ElTableColumn prop="status" label="操作状态">
          <template #default="scope">
            <ElTag :type="operationStatusMeta(scope.row.status).type">
              {{ operationStatusMeta(scope.row.status).label }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="requestTime" label="请求时长">
          <template #default="scope">
            <ElTag type="info">{{ scope.row.requestTime }}ms</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.createdAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="100" fixed="right">
          <template #default="scope">
            <ElButton link type="primary" @click="openDetail(scope.row)">
              详情
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />

      <ElDialog
        v-model="detailVisible"
        title="日志详情"
        width="720px"
        destroy-on-close
      >
        <LogDetail :row="detailRow" />
      </ElDialog>
    </div>
  </div>
</template>
