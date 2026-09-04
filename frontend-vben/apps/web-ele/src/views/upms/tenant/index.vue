<script lang="ts" setup>
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
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElRow,
  ElSelect,
  ElStatistic,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getList as getMenuList } from '#/api/upms/menu';
import {
  delObj,
  getPage,
  getTenantHistory,
  getTenantHistorySummary,
  getTenantMenuList,
} from '#/api/upms/tenant';
import { getList as getPkgList } from '#/api/upms/tenant-package';
import { useCrudGrid } from '#/composables/useCrudGrid';
import { PERMS } from '#/constants/permissions';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';
import { useDict } from '#/utils/dict';

import { useColumns, useGridFormSchema } from './data';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));
const TenantMenu = defineAsyncComponent(() => import('./tenantmenu.vue'));

// 字典
const { status } = useDict('status');
const refForm = ref();
const tenantMenuRef = ref();
const formMounted = ref(false);
const menuMounted = ref(false);

// ---- 统计卡片 ----
const statData = reactive({ total: 0, enabled: 0, platform: 0 });

// ---- 详情抽屉 ----
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailData = ref<any>(null);
const detailMenuTree = ref<any[]>([]);
const pkgList = ref<any[]>([]);

const isPlatformTenant = (tenant: any) =>
  tenant?.platformLevel === true || tenant?.platformLevel === 'PLATFORM';

const flattenMenus = (nodes: any[] = [], result: any[] = []) => {
  for (const node of nodes) {
    result.push(node);
    if (node.children?.length) {
      flattenMenus(node.children, result);
    }
  }
  return result;
};

const assignedMenus = computed(() => {
  const menuMap = new Map(
    flattenMenus(detailMenuTree.value).map((menu: any) => [
      String(menu.id),
      menu,
    ]),
  );
  const ids =
    (detailData.value?.menuIds as Array<number | string> | undefined) ?? [];
  return ids.map((menuId) => {
    const menu = menuMap.get(String(menuId));
    return {
      id: String(menuId),
      label: menu?.name || `菜单 #${menuId}`,
    };
  });
});

const openDetail = async (row: any) => {
  detailVisible.value = true;
  detailLoading.value = true;
  detailData.value = row;
  try {
    const [menuIds, menuTree, pkgs] = await Promise.all([
      getTenantMenuList(row.tenantId).catch(() => []),
      getMenuList().catch(() => []),
      getPkgList().catch(() => []),
    ]);
    detailData.value = {
      ...row,
      menuIds: (menuIds as Array<number | string>) ?? [],
    };
    detailMenuTree.value = (menuTree as any[]) ?? [];
    pkgList.value = (pkgs as any[]) ?? [];
  } finally {
    detailLoading.value = false;
  }
};

const detailPackageName = computed(() => {
  if (!detailData.value?.packageCode) return '-';
  const pkg = pkgList.value.find(
    (p: any) => p.packageCode === detailData.value.packageCode,
  );
  return pkg?.packageName || detailData.value.packageCode;
});

// ---- 历史记录抽屉 ----
const historyVisible = ref(false);
const historyLoading = ref(false);
const historyData = ref<any[]>([]);
const historyTotal = ref(0);
const historySummary = ref<any>(null);
const historyQuery = reactive({
  tenantId: '',
  changeType: '',
  fieldKey: '',
  operator: '',
  current: 1,
  size: 10,
});

const openHistory = async (row: any) => {
  historyVisible.value = true;
  historyQuery.tenantId = row.tenantId;
  historyQuery.current = 1;
  historyQuery.changeType = '';
  historyQuery.fieldKey = '';
  historyQuery.operator = '';
  await loadHistory();
  await loadHistorySummary();
};

const loadHistory = async () => {
  historyLoading.value = true;
  try {
    const res: any = await getTenantHistory(historyQuery.tenantId, {
      page: historyQuery.current,
      size: historyQuery.size,
      changeType: historyQuery.changeType,
      fieldKey: historyQuery.fieldKey,
      operator: historyQuery.operator,
    });
    historyData.value = res?.records ?? [];
    historyTotal.value = res?.total ?? 0;
  } finally {
    historyLoading.value = false;
  }
};

const loadHistorySummary = async () => {
  try {
    const res: any = await getTenantHistorySummary(historyQuery.tenantId);
    historySummary.value = res;
  } catch {
    historySummary.value = null;
  }
};

const resetHistoryQuery = () => {
  historyQuery.changeType = '';
  historyQuery.fieldKey = '';
  historyQuery.operator = '';
  historyQuery.current = 1;
  loadHistory();
};

const formatHistoryTime = (value?: null | string) => formatDateTime(value);

async function fetchTenantPage(params: any) {
  const response: any = await getPage(params);
  const records = response?.records ?? [];
  statData.total = response?.total ?? 0;
  statData.enabled = records.filter(
    (tenant: any) => String(tenant.tenantStatus) === '1',
  ).length;
  statData.platform = records.filter((tenant: any) =>
    isPlatformTenant(tenant),
  ).length;
  return response;
}

const {
  Grid,
  onRefresh: initPage,
  onDelete,
} = useCrudGrid({
  columns: useColumns,
  fetchPage: fetchTenantPage,
  deleteApi: delObj,
  rowKey: 'tenantId',
  deleteConfirmMessage: '确定要删除该租户吗？此操作不可恢复。',
  formOptions: {
    schema: useGridFormSchema(),
  },
});

/** 新增按钮 */
const openForm = (row?: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(refForm, (form: any) => form.initForm(row));
};

const add = () => openForm();

/** 修改按钮 */
const edit = (row: any) => openForm(row);

/** 删除租户 */
const del = (row: any) => {
  onDelete(
    row,
    `确定要删除租户「${row.name || row.tenantId}」吗？此操作不可恢复。`,
  );
};

const upMenu = (id: string) => {
  menuMounted.value = true;
  void invokeWhenComponentReady(tenantMenuRef, (menu: any) =>
    menu.initMenu(id),
  );
};
</script>

<template>
  <Page auto-content-height>
    <div>
      <!-- 统计卡片 -->
      <ElRow :gutter="16" style="margin-bottom: 16px">
        <ElCol :span="8">
          <ElCard shadow="hover">
            <ElStatistic title="租户总数" :value="statData.total" />
          </ElCard>
        </ElCol>
        <ElCol :span="8">
          <ElCard shadow="hover">
            <ElStatistic title="启用租户" :value="statData.enabled" />
          </ElCard>
        </ElCol>
        <ElCol :span="8">
          <ElCard shadow="hover">
            <ElStatistic title="平台级租户" :value="statData.platform" />
          </ElCard>
        </ElCol>
      </ElRow>

      <Form v-if="formMounted" ref="refForm" @init-page="initPage" />
      <TenantMenu
        v-if="menuMounted"
        ref="tenantMenuRef"
        @init-page="initPage"
      />

      <Grid>
        <template #toolbar-tools>
          <ElButton
            v-access:code="PERMS.upms.tenant.add"
            type="primary"
            @click="add"
          >
            <Plus class="size-5" />
            新增
          </ElButton>
        </template>

        <template #platformLevel="{ row }">
          <ElTag
            :type="isPlatformTenant(row) ? 'danger' : 'info'"
            effect="plain"
            size="small"
          >
            {{ isPlatformTenant(row) ? '平台级' : '业务级' }}
          </ElTag>
        </template>

        <template #contact="{ row }">
          <div class="grid gap-0.5">
            <span>{{ row.contactName || '-' }}</span>
            <span class="text-xs text-gray-400">
              {{ row.contactPhone || '-' }}
            </span>
          </div>
        </template>

        <template #expireAt="{ row }">
          {{ formatDateTime(row.expireAt) }}
        </template>

        <template #status="{ row }">
          <DictTag :options="status" :value="String(row.tenantStatus)" />
        </template>

        <template #operation="{ row }">
          <ElButton
            v-access:code="PERMS.upms.tenant.edit"
            link
            type="primary"
            @click="openDetail(row)"
          >
            详情
          </ElButton>
          <ElButton
            v-access:code="PERMS.upms.tenant.edit"
            link
            type="primary"
            @click="edit(row)"
          >
            修改
          </ElButton>
          <ElButton
            v-if="row.tenantId !== '1881232176465358849'"
            v-access:code="PERMS.upms.tenant.add"
            link
            type="primary"
            @click="upMenu(row.tenantId)"
          >
            配置菜单
          </ElButton>
          <ElButton link type="warning" @click="openHistory(row)">
            历史
          </ElButton>
          <ElButton
            v-access:code="PERMS.upms.tenant.del"
            link
            type="danger"
            @click="del(row)"
          >
            删除
          </ElButton>
        </template>
      </Grid>
    </div>

    <!-- 详情抽屉 -->
    <ElDrawer v-model="detailVisible" title="租户详情" size="600px">
      <div v-loading="detailLoading">
        <ElDescriptions :column="1" border v-if="detailData">
          <ElDescriptionsItem label="租户编码">
            {{ detailData.tenantId }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="租户名称">
            {{ detailData.name }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="租户级别">
            <ElTag
              :type="isPlatformTenant(detailData) ? 'danger' : 'info'"
              size="small"
            >
              {{ isPlatformTenant(detailData) ? '平台级' : '业务级' }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="套餐">
            {{ detailPackageName }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="官网">
            {{ detailData.website || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="地址">
            {{ detailData.address || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="联系人">
            {{ detailData.contactName || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="联系电话">
            {{ detailData.contactPhone || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="联系邮箱">
            {{ detailData.contactEmail || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="授权开始">
            {{ formatDateTime(detailData.authBeginAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="授权到期">
            {{ formatDateTime(detailData.expireAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <DictTag
              :options="status"
              :value="String(detailData.tenantStatus)"
            />
          </ElDescriptionsItem>
          <ElDescriptionsItem label="Logo">
            {{ detailData.logoUrl || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="创建时间">
            {{ formatDateTime(detailData.createTime) }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <div v-if="assignedMenus.length > 0" style="margin-top: 20px">
          <h4 style="margin-bottom: 10px">
            已分配菜单 ({{ assignedMenus.length }} 项)
          </h4>
          <div style="display: flex; flex-wrap: wrap; gap: 6px">
            <ElTag
              v-for="menu in assignedMenus"
              :key="menu.id"
              size="small"
              effect="plain"
            >
              {{ menu.label }}
            </ElTag>
          </div>
        </div>
      </div>
    </ElDrawer>

    <!-- 历史记录抽屉 -->
    <ElDrawer v-model="historyVisible" title="租户变更历史" size="800px">
      <!-- 汇总 -->
      <div
        v-if="historySummary"
        style="display: flex; gap: 12px; margin-bottom: 16px"
      >
        <ElCard shadow="never" style="flex: 1">
          <ElStatistic
            title="变更总数"
            :value="historySummary.totalChanges ?? 0"
          />
        </ElCard>
        <ElCard shadow="never" style="flex: 1">
          <ElStatistic
            title="创建事件"
            :value="historySummary.createCount ?? 0"
          />
        </ElCard>
        <ElCard shadow="never" style="flex: 1">
          <ElStatistic
            title="更新事件"
            :value="historySummary.updateCount ?? 0"
          />
        </ElCard>
      </div>

      <!-- 过滤 -->
      <ElForm :inline="true" style="margin-bottom: 12px">
        <ElFormItem label="变更类型">
          <ElSelect
            v-model="historyQuery.changeType"
            clearable
            placeholder="全部"
            style="width: 120px"
          >
            <ElOption label="创建" value="CREATE" />
            <ElOption label="更新" value="UPDATE" />
            <ElOption label="删除" value="DELETE" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="字段">
          <ElInput
            v-model="historyQuery.fieldKey"
            clearable
            placeholder="字段名"
            style="width: 140px"
          />
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput
            v-model="historyQuery.operator"
            clearable
            placeholder="操作人"
            style="width: 140px"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton
            type="primary"
            @click="
              historyQuery.current = 1;
              loadHistory();
            "
          >
            搜索
          </ElButton>
          <ElButton @click="resetHistoryQuery">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="historyLoading" :data="historyData" border stripe>
        <ElTableColumn prop="changeType" label="类型" width="90">
          <template #default="{ row }">
            <ElTag
              size="small"
              :type="
                row.changeType === 'CREATE'
                  ? 'success'
                  : row.changeType === 'DELETE'
                    ? 'danger'
                    : 'info'
              "
            >
              {{ row.changeType }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="fieldKey"
          label="字段"
          width="140"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="oldValue"
          label="旧值"
          min-width="120"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="newValue"
          label="新值"
          min-width="120"
          show-overflow-tooltip
        />
        <ElTableColumn prop="operator" label="操作人" width="120" />
        <ElTableColumn label="时间" width="170">
          <template #default="{ row }">
            {{ formatHistoryTime(row.occurredAt) }}
          </template>
        </ElTableColumn>
        <template #empty>
          <span style="color: #909399">暂无变更历史</span>
        </template>
      </ElTable>

      <Pagination
        :total="historyTotal"
        v-model:current="historyQuery.current"
        v-model:size="historyQuery.size"
        @change="loadHistory"
      />
    </ElDrawer>
  </Page>
</template>
