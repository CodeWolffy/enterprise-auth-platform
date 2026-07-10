<script lang="ts" setup name="genTable">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DataSourceView } from '#/api/codegen';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getDataSources } from '#/api/codegen';
import {
  deleteImportedTable,
  getDataSourceTables,
  getImportedTables,
  getTableConfig,
  importTables,
  saveTableColumns,
} from '#/api/gen/table';
import { PERMS } from '#/constants/permissions';

import {
  useImportedColumns,
  useImportedFormSchema,
  useSourceColumns,
  useSourceFormSchema,
} from './data';

const router = useRouter();

type TableRow = {
  createdAt?: string;
  engine?: string;
  tableComment: string;
  tableName: string;
  tableRows?: number;
  updatedAt?: string;
};
type ImportedRow = {
  businessName: string;
  className: string;
  columnCount: number;
  dataSourceId: number;
  functionAuthor: string;
  functionName: string;
  id: number;
  moduleName: string;
  packageName: string;
  tableComment: string;
  tableName: string;
  updatedAt?: string;
};
type ColumnRow = {
  columnComment: string;
  columnName: string;
  columnType: string;
  dataType: string;
  dictType: string;
  edit: boolean;
  htmlType: string;
  id: number;
  insert: boolean;
  javaField: string;
  javaType: string;
  list: boolean;
  primaryKey: boolean;
  query: boolean;
  queryType: string;
  required: boolean;
  sort: number;
};

const activeTab = ref('source');
const dsList = ref<DataSourceView[]>([]);
const selectedDsId = ref<null | number>(null);
const currentSourceRows = ref<TableRow[]>([]);

const columnDialogVisible = ref(false);
const columnDialogLoading = ref(false);
const columnTableId = ref(0);
const columnTableName = ref('');
const columns = ref<ColumnRow[]>([]);

const dsOptions = computed(() =>
  dsList.value
    .filter((dataSource) => dataSource.enabled)
    .map((dataSource) => ({ label: dataSource.name, value: dataSource.id })),
);

const [SourceGrid, sourceGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useSourceFormSchema(),
    submitOnChange: false,
  },
  gridOptions: {
    columns: useSourceColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: { enabled: true, pageSize: 20 },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          if (!selectedDsId.value) return { list: [], total: 0 };
          const response: any = await getDataSourceTables(selectedDsId.value, {
            keyword: formValues.keyword || undefined,
            page: page.currentPage,
            size: page.pageSize,
          });
          currentSourceRows.value = response?.records ?? [];
          return {
            list: currentSourceRows.value,
            total: response?.total ?? 0,
          };
        },
      },
    },
    rowConfig: { keyField: 'tableName' },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      search: true,
      zoom: false,
    },
  } as VxeTableGridOptions<TableRow>,
});

const [ImportedGrid, importedGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useImportedFormSchema(),
    submitOnChange: false,
  },
  gridOptions: {
    columns: useImportedColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: { enabled: true, pageSize: 20 },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const response: any = await getImportedTables({
            keyword: formValues.keyword || undefined,
            page: page.currentPage,
            size: page.pageSize,
          });
          return {
            list: response?.records ?? [],
            total: response?.total ?? 0,
          };
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      refresh: true,
      refreshOptions: { code: 'query' },
      search: true,
      zoom: false,
    },
  } as VxeTableGridOptions<ImportedRow>,
});

async function loadDataSources() {
  try {
    dsList.value = await getDataSources();
    const firstDataSource = dsList.value.find((item) => item.enabled);
    if (firstDataSource && !selectedDsId.value) {
      selectedDsId.value = firstDataSource.id;
    }
    await sourceGridApi.query();
  } catch {
    ElMessage.error('数据源加载失败');
  }
}

async function doImport(row: TableRow) {
  const dataSourceId = selectedDsId.value;
  if (!dataSourceId) {
    ElMessage.warning('请选择数据源');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认导入表「${row.tableName}」及其字段配置？`,
      '导入确认',
      { type: 'info' },
    );
    await importTables({ dataSourceId, tableNames: [row.tableName] });
    ElMessage.success(`表「${row.tableName}」已导入`);
    importedGridApi.query();
  } catch {
    // Cancelled confirmations or failed imports require no extra action.
  }
}

async function doBatchImport() {
  const dataSourceId = selectedDsId.value;
  if (!dataSourceId) {
    ElMessage.warning('请选择数据源');
    return;
  }
  if (currentSourceRows.value.length === 0) {
    ElMessage.warning('当前页没有可导入的数据表');
    return;
  }
  try {
    await ElMessageBox.confirm('确认导入当前页全部数据源表？', '批量导入确认', {
      type: 'info',
    });
    await importTables({
      dataSourceId,
      tableNames: currentSourceRows.value.map((table) => table.tableName),
    });
    ElMessage.success(`已批量导入 ${currentSourceRows.value.length} 张表`);
    importedGridApi.query();
  } catch {
    // Cancelled confirmations or failed imports require no extra action.
  }
}

async function openColumnConfig(row: ImportedRow) {
  columnTableId.value = row.id;
  columnTableName.value = row.tableName;
  columnDialogVisible.value = true;
  columnDialogLoading.value = true;
  try {
    const response: any = await getTableConfig(row.id);
    columns.value = response.columns ?? [];
  } finally {
    columnDialogLoading.value = false;
  }
}

async function saveColumnConfig() {
  columnDialogLoading.value = true;
  try {
    await saveTableColumns(columnTableId.value, columns.value);
    ElMessage.success('字段配置已保存');
  } finally {
    columnDialogLoading.value = false;
  }
}

function openGenerate(row: ImportedRow) {
  router.push({
    path: '/platform/codegen/generate',
    query: { tableId: String(row.id), tableName: row.tableName },
  });
}

async function deleteImported(row: ImportedRow) {
  try {
    await ElMessageBox.confirm(
      `确认删除表「${row.tableName}」的导入配置？`,
      '提示',
      { type: 'warning' },
    );
    await deleteImportedTable(row.id);
    ElMessage.success(`表「${row.tableName}」的导入配置已删除`);
    importedGridApi.query();
  } catch {
    // Cancelled confirmations require no further action.
  }
}

function handleDsChange() {
  currentSourceRows.value = [];
  sourceGridApi.reload();
}

function onTabChange(name: number | string) {
  if (name === 'source') sourceGridApi.query();
  else importedGridApi.query();
}

onMounted(() => {
  void loadDataSources();
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-3 flex items-center gap-3">
      <span class="whitespace-nowrap font-semibold">数据源：</span>
      <ElSelect
        v-model="selectedDsId"
        class="w-[280px]"
        @change="handleDsChange"
      >
        <ElOption
          v-for="dataSource in dsOptions"
          :key="dataSource.value"
          :label="dataSource.label"
          :value="dataSource.value"
        />
      </ElSelect>
    </div>

    <ElTabs v-model="activeTab" @tab-change="onTabChange">
      <ElTabPane label="数据源表" name="source">
        <SourceGrid>
          <template #toolbar-tools>
            <ElButton
              v-access:code="PERMS.gen.table.add"
              :disabled="currentSourceRows.length === 0"
              type="success"
              @click="doBatchImport"
            >
              批量导入当前页
            </ElButton>
          </template>

          <template #operation="{ row }">
            <ElButton
              v-access:code="PERMS.gen.table.add"
              link
              type="primary"
              @click="doImport(row)"
            >
              导入配置
            </ElButton>
          </template>
        </SourceGrid>
      </ElTabPane>

      <ElTabPane label="已导入表" name="imported">
        <ImportedGrid>
          <template #operation="{ row }">
            <ElButton
              v-access:code="PERMS.gen.table.edit"
              link
              type="primary"
              @click="openColumnConfig(row)"
            >
              配置字段
            </ElButton>
            <ElButton
              v-access:code="PERMS.gen.table.download"
              link
              type="success"
              @click="openGenerate(row)"
            >
              生成代码
            </ElButton>
            <ElButton
              v-access:code="PERMS.gen.table.del"
              link
              type="danger"
              @click="deleteImported(row)"
            >
              删除
            </ElButton>
          </template>
        </ImportedGrid>
      </ElTabPane>
    </ElTabs>

    <ElDialog
      v-model="columnDialogVisible"
      :title="`字段配置 - ${columnTableName}`"
      top="40px"
      width="95%"
    >
      <div v-loading="columnDialogLoading">
        <ElTable :data="columns" border max-height="60vh">
          <ElTableColumn label="序号" type="index" width="60" align="center" />
          <ElTableColumn prop="columnName" label="字段名" width="150" />
          <ElTableColumn label="注释" min-width="140">
            <template #default="scope">
              <ElInput
                v-model="scope.row.columnComment"
                placeholder="字段注释"
                size="small"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="columnType" label="物理类型" width="130" />
          <ElTableColumn label="Java类型" width="120">
            <template #default="scope">
              <ElSelect v-model="scope.row.javaType" size="small">
                <ElOption label="String" value="String" />
                <ElOption label="Long" value="Long" />
                <ElOption label="Integer" value="Integer" />
                <ElOption label="Double" value="Double" />
                <ElOption label="BigDecimal" value="java.math.BigDecimal" />
                <ElOption label="Instant" value="java.time.Instant" />
                <ElOption label="LocalDate" value="java.time.LocalDate" />
                <ElOption label="LocalTime" value="java.time.LocalTime" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn label="Java属性" width="130">
            <template #default="scope">
              <ElInput v-model="scope.row.javaField" size="small" />
            </template>
          </ElTableColumn>
          <ElTableColumn
            v-for="flag in [
              { field: 'insert', label: '插入' },
              { field: 'edit', label: '编辑' },
              { field: 'list', label: '列表' },
              { field: 'query', label: '查询' },
            ]"
            :key="flag.field"
            :label="flag.label"
            align="center"
            width="65"
          >
            <template #default="scope">
              <ElTag
                :type="scope.row[flag.field] ? 'success' : 'info'"
                class="cursor-pointer"
                @click="scope.row[flag.field] = !scope.row[flag.field]"
              >
                {{ scope.row[flag.field] ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="查询方式" width="110">
            <template #default="scope">
              <ElSelect v-model="scope.row.queryType" size="small">
                <ElOption label="=" value="EQ" />
                <ElOption label="!=" value="NE" />
                <ElOption label=">" value="GT" />
                <ElOption label=">=" value="GTE" />
                <ElOption label="<" value="LT" />
                <ElOption label="<=" value="LTE" />
                <ElOption label="LIKE" value="LIKE" />
                <ElOption label="BETWEEN" value="BETWEEN" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn label="必填" align="center" width="65">
            <template #default="scope">
              <ElTag
                :type="scope.row.required ? 'danger' : 'info'"
                class="cursor-pointer"
                @click="scope.row.required = !scope.row.required"
              >
                {{ scope.row.required ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="显示类型" width="110">
            <template #default="scope">
              <ElSelect v-model="scope.row.htmlType" size="small">
                <ElOption label="文本框" value="input" />
                <ElOption label="文本域" value="textarea" />
                <ElOption label="下拉框" value="select" />
                <ElOption label="单选框" value="radio" />
                <ElOption label="复选框" value="checkbox" />
                <ElOption label="日期控件" value="datetime" />
                <ElOption label="数字" value="number" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn label="字典类型" width="140">
            <template #default="scope">
              <ElInput
                v-model="scope.row.dictType"
                placeholder="字典编码"
                size="small"
              />
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <template #footer>
        <ElButton @click="columnDialogVisible = false">关闭</ElButton>
        <ElButton
          :loading="columnDialogLoading"
          type="primary"
          @click="saveColumnConfig"
        >
          保存配置
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
