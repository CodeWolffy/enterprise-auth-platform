<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/upms/sys-login-log';
import { formatDateTime } from '#/utils/datetime';
import { loginStatusMeta } from '#/utils/log-status';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const showSearch = ref(true);
const loading = ref(false);

const state = reactive({
  queryParams: {
    userName: '',
    ipAddr: '',
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
    userName: state.queryParams.userName || undefined,
    clientIp: state.queryParams.ipAddr || undefined,
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
  state.queryParams.userName = '';
  state.queryParams.ipAddr = '';
  state.queryParams.status = '';
  state.queryParams.tenantId = '';
  state.queryParams.dateRange = [];
  initPage();
};
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm :model="state.queryParams" :inline="true" v-show="showSearch">
        <ElFormItem label="登录用户" prop="userName">
          <ElInput
            v-model="state.queryParams.userName"
            clearable
            placeholder="请输入登录用户"
            style="width: 160px"
          />
        </ElFormItem>
        <ElFormItem label="登录地址" prop="ipAddr">
          <ElInput
            v-model="state.queryParams.ipAddr"
            clearable
            placeholder="请输入登录地址"
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
            <ElOption label="成功" value="SUCCESS" />
            <ElOption label="失败" value="FAILED" />
            <ElOption label="锁定" value="LOCKED" />
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
        <ElTableColumn prop="userName" label="登录用户" />
        <ElTableColumn prop="ipAddr" label="登录地址" />
        <ElTableColumn prop="location" label="登录地点" />
        <ElTableColumn label="登录时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.createdAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="browser" label="浏览器" />
        <ElTableColumn prop="os" label="操作系统" />
        <ElTableColumn prop="status" label="操作状态">
          <template #default="scope">
            <ElTag :type="loginStatusMeta(scope.row.status).type">
              {{ loginStatusMeta(scope.row.status).label }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="msg" label="操作描述" />
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
