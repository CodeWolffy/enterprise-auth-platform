<script setup lang="ts">
import { PERMS } from '#/constants/permissions';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getList } from '#/api/upms/online-user';
import { formatDateTime } from '#/utils/datetime';

const state = reactive({
  tableData: [] as any[],
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 20,
  },
});
const showSearch = ref(true);
const loading = ref(false);
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const initPage = async () => {
  loading.value = true;
  const params = {
    scope: 'all',
    page: state.page.currentPage,
    size: state.page.pageSize,
  };
  await getList(params)
    .then((response: any) => {
      if (Array.isArray(response)) {
        state.tableData = response;
        state.page.total = response.length;
      } else {
        state.tableData = response.records || [];
        state.page.total = response.total || 0;
      }
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

// 强退用户
const forced = (sessionId: string) => {
  ElMessageBox.confirm('此操作将强退该用户, 是否继续?', '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      delObj(sessionId)
        .then(() => {
          ElMessage.success('强退成功');
          initPage();
        })
        .catch(() => {});
    })
    .catch(() => {});
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
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
        <ElTableColumn prop="sessionId" label="会话ID" width="180" />
        <ElTableColumn prop="username" label="登录用户" />
        <ElTableColumn prop="tenantId" label="租户编码" />
        <ElTableColumn prop="activeTenantId" label="活跃租户" />
        <ElTableColumn prop="clientIp" label="登录IP" width="140" />

        <ElTableColumn
          prop="loginLocation"
          label="登录地址"
          min-width="120"
          show-overflow-tooltip
        />
        <ElTableColumn prop="device" label="设备标识" show-overflow-tooltip />
        <ElTableColumn label="签发时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.issuedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="过期时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.expiresAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="最后访问" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.lastAccessAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="active" label="状态" width="90" align="center">
          <template #default="scope">
            <ElTag :type="scope.row.active ? 'success' : 'danger'">
              {{ scope.row.active ? '有效' : '失效' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" align="center" fixed="right" width="150">
          <template #default="scope">
            <ElButton
              v-if="!scope.row.currentSession"
              link
              type="danger"
              v-access:code="PERMS.upms.onlineUser.forced"
              @click="forced(scope.row.sessionId)"
              :icon="Delete"
            >
              强退用户
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
    </div>
  </div>
</template>
