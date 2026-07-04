<script lang="ts" setup>
import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import {
  Delete,
  Edit,
  Plus,
  Refresh,
  Search,
  View,
} from '@element-plus/icons-vue';
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

import { getList as getMenuList } from '#/api/upms/menu';
import {
  delObj,
  getPage,
  getTenantHistory,
  getTenantHistorySummary,
  getTenantMenuList,
} from '#/api/upms/tenant';
import { getList as getPkgList } from '#/api/upms/tenant-package';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
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
const queryRef = ref();
const state = reactive({
  queryParams: {
    keyword: '',
    platformLevel: '',
    tenantStatus: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [] as any[],
});
const showSearch = ref(true);
const loading = ref(false);
const refForm = ref();
const tenantMenuRef = ref();
const formMounted = ref(false);
const menuMounted = ref(false);

// ---- 统计卡片 ----
const statData = computed(() => {
  const all = state.tableData;
  const enabled = all.filter((t: any) => String(t.tenantStatus) === '1');
  const platformLevel = all.filter((t: any) => isPlatformTenant(t));
  return {
    total: state.page.total,
    enabled: enabled.length,
    platform: platformLevel.length,
  };
});

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

const initPage = async () => {
  loading.value = true;
  const params = {
    page: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

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
  ElMessageBox.confirm(
    `确定要删除租户「${row.name || row.tenantId}」吗？此操作不可恢复。`,
    '删除确认',
    {
      cancelButtonText: '取消',
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  ).then(() => {
    delObj(row.tenantId)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

initPage();

/** 重置搜索表单 */
const resetQuery = () => {
  queryRef.value.resetFields();
};

const upMenu = (id: string) => {
  menuMounted.value = true;
  void invokeWhenComponentReady(tenantMenuRef, (menu: any) =>
    menu.initMenu(id),
  );
};
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
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

      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="租户名称" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="请输入租户编码或名称"
          />
        </ElFormItem>
        <ElFormItem label="租户级别" prop="platformLevel">
          <ElSelect
            v-model="state.queryParams.platformLevel"
            clearable
            placeholder="全部"
            style="width: 140px"
          >
            <ElOption label="平台级" value="PLATFORM" />
            <ElOption label="业务级" value="BUSINESS" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态" prop="tenantStatus">
          <ElSelect
            v-model="state.queryParams.tenantStatus"
            clearable
            placeholder="全部"
            style="width: 120px"
          >
            <ElOption label="启用" value="1" />
            <ElOption label="停用" value="0" />
          </ElSelect>
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
        <div>
          <ElButton
            type="primary"
            v-access:code="'upms:systenant:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>

      <Form v-if="formMounted" ref="refForm" @init-page="initPage" />
      <TenantMenu
        v-if="menuMounted"
        ref="tenantMenuRef"
        @init-page="initPage"
      />

      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="tenantId" label="租户编码" width="160" />
        <ElTableColumn prop="name" label="租户名称" min-width="120" />
        <ElTableColumn label="租户级别" width="100">
          <template #default="scope">
            <ElTag
              :type="isPlatformTenant(scope.row) ? 'danger' : 'info'"
              size="small"
              effect="plain"
            >
              {{ isPlatformTenant(scope.row) ? '平台级' : '业务级' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="联系人" min-width="150">
          <template #default="scope">
            <div style="display: grid; gap: 2px">
              <span>{{ scope.row.contactName || '-' }}</span>
              <span style="font-size: 12px; color: #909399">{{
                scope.row.contactPhone || '-'
              }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="授权到期" width="170">
          <template #default="scope">
            {{ formatDateTime(scope.row.expireAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="tenantStatus" label="状态" width="90">
          <template #default="scope">
            <DictTag
              :options="status"
              :value="String(scope.row.tenantStatus)"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="320" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'upms:systenant:edit'"
              :icon="View"
              @click="openDetail(scope.row)"
            >
              详情
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'upms:systenant:edit'"
              :icon="Edit"
              @click="edit(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="primary"
              v-if="scope.row.tenantId !== '1881232176465358849'"
              v-access:code="'upms:systenant:add'"
              :icon="Edit"
              @click="upMenu(scope.row.tenantId)"
            >
              配置菜单
            </ElButton>
            <ElButton link type="warning" @click="openHistory(scope.row)">
              历史
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'upms:systenant:del'"
              :icon="Delete"
              @click="del(scope.row)"
            >
              删除
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
  </div>
</template>
