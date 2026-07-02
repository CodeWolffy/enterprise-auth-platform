<script setup lang="ts">
import { defineAsyncComponent, reactive, ref, computed } from 'vue';

import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElStatistic,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getAssignedRoles, getPage } from '#/api/upms/user';
import { useAuthStore } from '#/store/auth';
import { formatDateTime } from '#/utils/datetime';
import { invokeWhenComponentReady } from '#/utils/component-ready';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const UserDialogs = defineAsyncComponent(() => import('./info/dialogs.vue'));

const authStore = useAuthStore();

const state = reactive({
  queryParams: { username: '', mobile: '', email: '', enabled: '' },
  page: { total: 0, currentPage: 1, pageSize: 10 },
  tableData: [] as any[],
});
const loading = ref(false);
const formRef = ref();
const formMounted = ref(false);
const loginLogVisible = ref(false);
const operationLogVisible = ref(false);
const resetPwdVisible = ref(false);
const activeUser = ref<any>(null);

// ---- 统计卡片 ----
const statData = computed(() => {
  const all = state.tableData;
  const enabled = all.filter((u: any) => u.enabled);
  const disabled = all.filter((u: any) => !u.enabled);
  const avgRoles = all.length
    ? Number(
        (
          all.reduce((sum: number, u: any) => sum + (u.roles?.length ?? 0), 0) /
          all.length
        ).toFixed(1),
      )
    : 0;
  return {
    total: state.page.total,
    enabled: enabled.length,
    disabled: disabled.length,
    avgRoles,
  };
});

// ---- 详情抽屉 ----
const detailVisible = ref(false);
const detailData = ref<any>(null);
const detailRoles = ref<string[]>([]);

const openDetail = async (row: any) => {
  detailVisible.value = true;
  detailData.value = row;
  detailRoles.value = [];
  try {
    const roles = await getAssignedRoles(row.id);
    detailRoles.value = Array.isArray(roles)
      ? roles.map((r: any) => (typeof r === 'string' ? r : r?.code || r?.name || ''))
      : [];
  } catch {
    detailRoles.value = row.roles ?? [];
  }
};

const DATA_SCOPE_LABELS: Record<string, string> = {
  ALL: '全部数据',
  DEPT_AND_CHILD: '部门及子部门',
  DEPT: '本部门',
  SELF: '仅本人',
  CUSTOM: '自定义',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response: any = await getPage({
      page: state.page.currentPage,
      size: state.page.pageSize,
      username: state.queryParams.username,
      mobile: state.queryParams.mobile,
      email: state.queryParams.email,
      enabled: state.queryParams.enabled,
    });
    state.tableData = response?.records ?? [];
    state.page.total = response?.total ?? 0;
  } finally {
    loading.value = false;
  }
};
const resetQuery = () => {
  state.queryParams.username = '';
  state.queryParams.mobile = '';
  state.queryParams.email = '';
  state.queryParams.enabled = '';
  state.page.currentPage = 1;
  initPage();
};
const openForm = (row?: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
};
const add = () => openForm();
const edit = (row: any) => openForm(row);
const openLoginLog = (row: any) => {
  activeUser.value = row;
  loginLogVisible.value = true;
};
const openResetPwd = (row: any) => {
  activeUser.value = row;
  resetPwdVisible.value = true;
};
const del = (row: any) => {
  // 自我保护：不能删除自己
  if (authStore.snapshot?.username === row.username) {
    ElMessage.warning('不能删除当前登录用户');
    return;
  }
  ElMessageBox.confirm('此操作将删除该用户，是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确认',
    type: 'warning',
  }).then(() => {
    delObj(row.id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 统计卡片 -->
      <ElRow :gutter="16" style="margin-bottom: 16px">
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="用户总数" :value="statData.total" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="启用" :value="statData.enabled" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="停用" :value="statData.disabled" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="平均角色数" :value="statData.avgRoles" :precision="1" />
          </ElCard>
        </ElCol>
      </ElRow>

      <ElForm :inline="true" :model="state.queryParams">
        <ElFormItem label="用户名">
          <ElInput
            v-model="state.queryParams.username"
            clearable
            placeholder="用户名"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="手机号">
          <ElInput
            v-model="state.queryParams.mobile"
            clearable
            placeholder="手机号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="邮箱">
          <ElInput
            v-model="state.queryParams.email"
            clearable
            placeholder="邮箱"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect
            v-model="state.queryParams.enabled"
            clearable
            placeholder="全部"
            style="width: 100px"
          >
            <ElOption label="启用" value="true" />
            <ElOption label="停用" value="false" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton :icon="Search" type="primary" @click="initPage">
            搜索
          </ElButton>
          <ElButton :icon="Refresh" @click="resetQuery"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>

      <div class="hx-table-toolbar" style="margin-bottom: 12px">
        <ElButton
          v-access:code="'upms:sysuser:add'"
          :icon="Plus"
          type="primary"
          @click="add"
        >
          新增
        </ElButton>
      </div>

      <Form v-if="formMounted" ref="formRef" @init-page="initPage" />

      <UserDialogs
        v-model:login-log-visible="loginLogVisible"
        v-model:operation-log-visible="operationLogVisible"
        v-model:reset-pwd-visible="resetPwdVisible"
        :active-user="activeUser"
      />

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn label="用户名" prop="username" />
        <ElTableColumn label="显示名称" prop="displayName" />
        <ElTableColumn label="手机号" prop="mobile" />
        <ElTableColumn label="邮箱" prop="email" show-overflow-tooltip />
        <ElTableColumn label="部门" prop="deptName" show-overflow-tooltip />
        <ElTableColumn label="角色" show-overflow-tooltip>
          <template #default="scope">
            <ElTag
              v-for="r in scope.row.roles"
              :key="r"
              size="small"
              style="margin-right: 4px"
            >
              {{ r }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.enabled ? 'success' : 'info'">
              {{ scope.row.enabled ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" fixed="right" label="操作" width="320">
          <template #default="scope">
            <ElButton
              v-access:code="'upms:sysuser:edit'"
              :icon="View"
              link
              type="primary"
              @click="openDetail(scope.row)"
            >
              详情
            </ElButton>
            <ElButton
              v-access:code="'upms:sysuser:edit'"
              :icon="Edit"
              link
              type="primary"
              @click="edit(scope.row)"
            >
              修改
            </ElButton>
            <ElButton link type="primary" @click="openLoginLog(scope.row)">日志</ElButton>
            <ElButton link type="primary" @click="openResetPwd(scope.row)">改密</ElButton>
            <ElButton
              v-access:code="'upms:sysuser:del'"
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
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />
    </div>

    <!-- 用户详情抽屉 -->
    <ElDrawer v-model="detailVisible" title="用户详情" size="500px">
      <ElDescriptions :column="1" border v-if="detailData">
        <ElDescriptionsItem label="用户ID">{{ detailData.id }}</ElDescriptionsItem>
        <ElDescriptionsItem label="用户名">{{ detailData.username }}</ElDescriptionsItem>
        <ElDescriptionsItem label="显示名称">{{ detailData.displayName || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="手机号">{{ detailData.mobile || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="邮箱">{{ detailData.email || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="部门">{{ detailData.deptName || detailData.deptId || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="数据权限">
          <ElTag size="small">{{ DATA_SCOPE_LABELS[detailData.dataScopeType] || detailData.dataScopeType || '-' }}</ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="角色">
          <div style="display: flex; flex-wrap: wrap; gap: 4px">
            <ElTag
              v-for="r in detailRoles"
              :key="r"
              size="small"
              type="success"
              effect="plain"
            >
              {{ r }}
            </ElTag>
            <span v-if="!detailRoles.length">-</span>
          </div>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          <ElTag :type="detailData.enabled ? 'success' : 'info'">
            {{ detailData.enabled ? '启用' : '停用' }}
          </ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="创建时间">{{ formatDateTime(detailData.createdAt) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="最后登录">{{ formatDateTime(detailData.lastLoginAt) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="最后登录IP">{{ detailData.lastLoginIp || '-' }}</ElDescriptionsItem>
      </ElDescriptions>
    </ElDrawer>
  </div>
</template>
