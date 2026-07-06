<script setup lang="ts">
import { PERMS } from '#/constants/permissions';

import type { DataSourceView } from '#/api/codegen';

import { computed, onMounted, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  authorizeDataSource,
  deleteDataSource,
  getDataSources,
  testDataSource,
} from '#/api/codegen';

import DataSourceForm from './form.vue';

const loading = ref(false);
const allRows = ref<DataSourceView[]>([]);
const dialogVisible = ref(false);
const currentRow = ref<DataSourceView | null>(null);
const formRef = ref<null | { resetForm?: () => void }>(null);

// 搜索 & 分页（数据源总量小，前端分页即可）
const searchKeyword = ref('');
const currentPage = ref(1);
const pageSize = ref(10);

const filteredRows = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return allRows.value;
  return allRows.value.filter(
    (r) =>
      r.name?.toLowerCase().includes(kw) ||
      r.dbName?.toLowerCase().includes(kw) ||
      r.host?.toLowerCase().includes(kw),
  );
});

const total = computed(() => filteredRows.value.length);

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredRows.value.slice(start, start + pageSize.value);
});

const asDataSourceRow = (row: unknown) => row as DataSourceView;

async function load() {
  loading.value = true;
  try {
    allRows.value = await getDataSources();
  } catch {
    ElMessage.error('数据源加载失败');
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  currentPage.value = 1;
}

function resetSearch() {
  searchKeyword.value = '';
  currentPage.value = 1;
}

function openCreate() {
  currentRow.value = null;
  dialogVisible.value = true;
}

function openEdit(row: DataSourceView) {
  currentRow.value = row;
  dialogVisible.value = true;
}

async function onDelete(row: DataSourceView) {
  await ElMessageBox.confirm(`确认删除数据源「${row.name}」？`, '提示', {
    type: 'warning',
  });
  await deleteDataSource(row.id);
  ElMessage.success('已删除');
  await load();
}

async function onTest(row: DataSourceView) {
  const result = await testDataSource(row.id);
  ElMessage[result.success ? 'success' : 'warning'](
    result.message || '测试完成',
  );
}

async function onAuthorize(row: DataSourceView) {
  await authorizeDataSource(row.id, `已确认 ${row.name} 的数据源授权。`);
  ElMessage.success('已授权');
  await load();
}

function onDialogClosed() {
  formRef.value?.resetForm?.();
  currentRow.value = null;
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索栏 -->
      <ElForm :inline="true" v-show="true">
        <ElFormItem label="名称/库名/主机">
          <ElInput
            v-model="searchKeyword"
            clearable
            placeholder="请输入关键字"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" :icon="Search" @click="handleSearch">
            搜索
          </ElButton>
          <ElButton :icon="Refresh" @click="resetSearch">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            :icon="Plus"
            v-access:code="PERMS.gen.datasource.add"
            @click="openCreate"
          >
            新增
          </ElButton>
        </div>
        <ElButton :icon="Refresh" :loading="loading" @click="load">
          刷新
        </ElButton>
      </div>

      <!-- 列表 -->
      <ElTable v-loading="loading" :data="pagedRows" border>
        <ElTableColumn prop="name" label="名称" min-width="140" />
        <ElTableColumn prop="dbName" label="数据库名称" min-width="140" />
        <ElTableColumn prop="host" label="主机" min-width="140" />
        <ElTableColumn prop="port" label="端口" width="100" />
        <ElTableColumn prop="username" label="用户名" width="120" />
        <ElTableColumn label="类型" width="90" align="center">
          <template #default="scope">
            <ElTag :type="scope.row.external ? 'warning' : 'success'">
              {{ scope.row.external ? '外部' : '本地' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="授权" width="90" align="center">
          <template #default="scope">
            <ElTag
              v-if="scope.row.external"
              :type="scope.row.externalAuthorized ? 'success' : 'info'"
            >
              {{ scope.row.externalAuthorized ? '已授权' : '待授权' }}
            </ElTag>
            <span v-else style="color: #909399">-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="300" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              :icon="Edit"
              v-access:code="PERMS.gen.datasource.edit"
              @click="openEdit(asDataSourceRow(scope.row))"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="primary"
              @click="onTest(asDataSourceRow(scope.row))"
              v-access:code="PERMS.gen.datasource.get"
            >
              测试
            </ElButton>
            <ElButton
              v-if="scope.row.external && !scope.row.externalAuthorized"
              link
              type="warning"
              @click="onAuthorize(asDataSourceRow(scope.row))"
              v-access:code="PERMS.gen.datasource.edit"
            >
              授权
            </ElButton>
            <ElButton
              link
              type="danger"
              :icon="Delete"
              @click="onDelete(asDataSourceRow(scope.row))"
              v-access:code="PERMS.gen.datasource.del"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无数据源" />
        </template>
      </ElTable>

      <!-- 分页 -->
      <div v-if="total > 0" style="margin-top: 16px; text-align: right">
        <ElPagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>

      <ElDialog
        v-model="dialogVisible"
        :title="currentRow ? '修改数据源' : '新增数据源'"
        width="720px"
        @closed="onDialogClosed"
      >
        <DataSourceForm
          ref="formRef"
          :model-value="currentRow"
          @close="dialogVisible = false"
          @saved="
            dialogVisible = false;
            load();
          "
        />
      </ElDialog>
    </div>
  </div>
</template>
