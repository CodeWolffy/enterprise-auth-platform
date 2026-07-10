<script setup lang="ts">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElMessage,
  ElMessageBox,
  ElRow,
  ElStatistic,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getAssignedRoles, getPage } from '#/api/upms/user';
import { PERMS } from '#/constants/permissions';
import { useAuthStore } from '#/store/auth';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

import { useColumns, useGridFormSchema } from './data';

const Form = defineAsyncComponent(() => import('./form.vue'));
const UserDialogs = defineAsyncComponent(() => import('./info/dialogs.vue'));

const authStore = useAuthStore();

const formRef = ref();
const formMounted = ref(false);
const loginLogVisible = ref(false);
const operationLogVisible = ref(false);
const resetPwdVisible = ref(false);
const activeUser = ref<any>(null);
const pageStats = reactive({
  total: 0,
  enabled: 0,
  disabled: 0,
  avgRoles: 0,
});

const detailVisible = ref(false);
const detailData = ref<any>(null);
const detailRoles = ref<string[]>([]);

const DATA_SCOPE_LABELS: Record<string, string> = {
  ALL: '全部数据',
  DEPT_AND_CHILD: '部门及子部门',
  DEPT: '本部门',
  SELF: '仅本人',
  CUSTOM: '自定义',
};

const statData = computed(() => pageStats);

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
          });
          const records = response?.records ?? [];
          const enabled = records.filter((user: any) => user.enabled).length;
          pageStats.total = response?.total ?? 0;
          pageStats.enabled = enabled;
          pageStats.disabled = records.length - enabled;
          pageStats.avgRoles =
            records.length === 0
              ? 0
              : Number(
                  (
                    records.reduce(
                      (sum: number, user: any) =>
                        sum + (user.roles?.length ?? 0),
                      0,
                    ) / records.length
                  ).toFixed(1),
                );
          return { list: records, total: pageStats.total };
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

function openLoginLog(row: any) {
  activeUser.value = row;
  loginLogVisible.value = true;
}

function openResetPwd(row: any) {
  activeUser.value = row;
  resetPwdVisible.value = true;
}

async function openDetail(row: any) {
  detailVisible.value = true;
  detailData.value = row;
  detailRoles.value = [];
  try {
    const roles = await getAssignedRoles(row.id);
    detailRoles.value = Array.isArray(roles)
      ? roles.map((role: any) =>
          typeof role === 'string' ? role : role?.code || role?.name || '',
        )
      : [];
  } catch {
    detailRoles.value = row.roles ?? [];
  }
}

async function onDelete(row: any) {
  if (authStore.snapshot?.username === row.username) {
    ElMessage.warning('不能删除当前登录用户');
    return;
  }
  try {
    await ElMessageBox.confirm('此操作将删除该用户，是否继续?', '提示', {
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
</script>

<template>
  <Page auto-content-height>
    <ElRow :gutter="16" class="mb-4">
      <ElCol :span="6">
        <ElCard shadow="hover">
          <ElStatistic :value="statData.total" title="用户总数" />
        </ElCard>
      </ElCol>
      <ElCol :span="6">
        <ElCard shadow="hover">
          <ElStatistic :value="statData.enabled" title="当前页启用" />
        </ElCard>
      </ElCol>
      <ElCol :span="6">
        <ElCard shadow="hover">
          <ElStatistic :value="statData.disabled" title="当前页停用" />
        </ElCard>
      </ElCol>
      <ElCol :span="6">
        <ElCard shadow="hover">
          <ElStatistic
            :precision="1"
            :value="statData.avgRoles"
            title="当前页平均角色数"
          />
        </ElCard>
      </ElCol>
    </ElRow>

    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />
    <UserDialogs
      v-model:login-log-visible="loginLogVisible"
      v-model:operation-log-visible="operationLogVisible"
      v-model:reset-pwd-visible="resetPwdVisible"
      :active-user="activeUser"
    />

    <Grid>
      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.user.add"
          type="primary"
          @click="openForm()"
        >
          <Plus class="size-5" />
          新增
        </ElButton>
      </template>

      <template #roles="{ row }">
        <ElTag v-for="role in row.roles" :key="role" class="mr-1" size="small">
          {{ role }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.enabled ? 'success' : 'info'">
          {{ row.enabled ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #operation="{ row }">
        <ElButton
          v-access:code="PERMS.upms.user.edit"
          link
          type="primary"
          @click="openDetail(row)"
        >
          详情
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.user.edit"
          link
          type="primary"
          @click="openForm(row)"
        >
          修改
        </ElButton>
        <ElButton link type="primary" @click="openLoginLog(row)">
          日志
        </ElButton>
        <ElButton link type="primary" @click="openResetPwd(row)">
          改密
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.user.del"
          link
          type="danger"
          @click="onDelete(row)"
        >
          删除
        </ElButton>
      </template>
    </Grid>

    <ElDrawer v-model="detailVisible" size="500px" title="用户详情">
      <ElDescriptions v-if="detailData" :column="1" border>
        <ElDescriptionsItem label="用户ID">
          {{ detailData.id }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="用户名">
          {{ detailData.username }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="显示名称">
          {{ detailData.displayName || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="手机号">
          {{ detailData.mobile || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="邮箱">
          {{ detailData.email || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="部门">
          {{ detailData.deptName || detailData.deptId || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="数据权限">
          <ElTag size="small">
            {{
              DATA_SCOPE_LABELS[detailData.dataScopeType] ||
              detailData.dataScopeType ||
              '-'
            }}
          </ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="角色">
          <div class="flex flex-wrap gap-1">
            <ElTag
              v-for="role in detailRoles"
              :key="role"
              effect="plain"
              size="small"
              type="success"
            >
              {{ role }}
            </ElTag>
            <span v-if="detailRoles.length === 0">-</span>
          </div>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          <ElTag :type="detailData.enabled ? 'success' : 'info'">
            {{ detailData.enabled ? '启用' : '停用' }}
          </ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="创建时间">
          {{ formatDateTime(detailData.createdAt) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="最后登录">
          {{ formatDateTime(detailData.lastLoginAt) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="最后登录IP">
          {{ detailData.lastLoginIp || '-' }}
        </ElDescriptionsItem>
      </ElDescriptions>
    </ElDrawer>
  </Page>
</template>
