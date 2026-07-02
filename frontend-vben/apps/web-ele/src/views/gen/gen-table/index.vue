<script lang="ts" setup name="genTable">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  Delete,
  Download,
  Edit,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import { getDataSources, type DataSourceView } from '#/api/codegen';
import {
  getDataSourceTables,
  getImportedTables,
  getTableConfig,
  importTables,
  saveTableColumns,
} from '#/api/gen/table';

const router = useRouter();

type TableRow = { tableName: string; tableComment: string; engine?: string; tableRows?: number; createdAt?: number; updatedAt?: number };
type ImportedRow = { id: number; dataSourceId: number; tableName: string; tableComment: string; className: string; packageName: string; moduleName: string; businessName: string; functionName: string; functionAuthor: string; columnCount: number; updatedAt?: number };
type ColumnRow = { id: number; columnName: string; columnComment: string; columnType: string; dataType: string; javaType: string; javaField: string; primaryKey: boolean; required: boolean; insert: boolean; edit: boolean; list: boolean; query: boolean; queryType: string; htmlType: string; dictType: string; sort: number };

const activeTab = ref('source');
const loading = ref(false);
const dsList = ref<DataSourceView[]>([]);
const selectedDsId = ref<number | null>(null);
const tableKeyword = ref('');
const importKeyword = ref('');

// 数据源表
const sourceTables = ref<TableRow[]>([]);
const sourceTotal = ref(0);
const sourcePage = ref(1);
const sourceSize = ref(20);

// 已导入表
const importedTables = ref<ImportedRow[]>([]);
const importTotal = ref(0);
const importPage = ref(1);
const importSize = ref(20);

// 列配置编辑弹窗
const columnDialogVisible = ref(false);
const columnDialogLoading = ref(false);
const columnTableId = ref(0);
const columnTableName = ref('');
const columns = ref<ColumnRow[]>([]);

const dsOptions = computed(() =>
  dsList.value.filter((ds) => ds.enabled).map((ds) => ({ label: ds.name, value: ds.id })),
);

const asImportedRow = (row: unknown) => row as ImportedRow;

// 初始化数据源列表
async function loadDataSources() {
  try {
    dsList.value = await getDataSources();
    const firstDataSource = dsList.value[0];
    if (firstDataSource && !selectedDsId.value) {
      selectedDsId.value = firstDataSource.id;
      await loadSourceTables();
    }
  } catch {
    ElMessage.error('数据源加载失败');
  }
}

// 加载数据源表
async function loadSourceTables() {
  if (!selectedDsId.value) return;
  loading.value = true;
  try {
    const res: any = await getDataSourceTables(selectedDsId.value, {
      keyword: tableKeyword.value || undefined,
      page: sourcePage.value,
      size: sourceSize.value,
    });
    sourceTables.value = res.records ?? [];
    sourceTotal.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
}

// 加载已导入表
async function loadImportedTables() {
  loading.value = true;
  try {
    const res: any = await getImportedTables({
      keyword: importKeyword.value || undefined,
      page: importPage.value,
      size: importSize.value,
    });
    importedTables.value = res.records ?? [];
    importTotal.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
}

// 导入表配置
async function doImport(tableName: string) {
  try {
    await ElMessageBox.confirm(`确认导入表「${tableName}」及其字段配置？`, '导入确认', { type: 'info' });
  } catch {
    return;
  }
  loading.value = true;
  try {
    await importTables({
      dataSourceId: selectedDsId.value!,
      tableNames: [tableName],
    });
    ElMessage.success(`表「${tableName}」已导入`);
  } catch {
    ElMessage.error(`表「${tableName}」导入失败`);
  } finally {
    loading.value = false;
  }
}

// 批量导入
async function doBatchImport() {
  try {
    await ElMessageBox.confirm('确认导入当前所有数据源表？', '批量导入确认', { type: 'info' });
  } catch {
    return;
  }
  loading.value = true;
  try {
    await importTables({
      dataSourceId: selectedDsId.value!,
      tableNames: sourceTables.value.map((t) => t.tableName),
    });
    ElMessage.success(`已批量导入 ${sourceTables.value.length} 张表`);
  } catch {
    ElMessage.error('批量导入失败');
  } finally {
    loading.value = false;
  }
}

// 打开列配置
async function openColumnConfig(row: ImportedRow) {
  columnTableId.value = row.id;
  columnTableName.value = row.tableName;
  columnDialogVisible.value = true;
  columnDialogLoading.value = true;
  try {
    const res: any = await getTableConfig(row.id);
    columns.value = res.columns ?? [];
  } finally {
    columnDialogLoading.value = false;
  }
}

// 保存列配置
async function saveColumnConfig() {
  columnDialogLoading.value = true;
  try {
    await saveTableColumns(columnTableId.value, columns.value);
    ElMessage.success('字段配置已保存');
  } finally {
    columnDialogLoading.value = false;
  }
}

// 打开生成页面
function openGenerate(row: ImportedRow) {
  router.push({
    path: '/platform/codegen/generate',
    query: { tableId: String(row.id), tableName: row.tableName },
  });
}

// 删除导入表配置
async function deleteImported(row: ImportedRow) {
  try {
    await ElMessageBox.confirm(`确认删除表「${row.tableName}」的导入配置？`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  // 后端没有直接删除导入表的 API，暂通过逻辑标记实现
  ElMessage.info('删除功能需通过后端扩展');
}

function handleDsChange() {
  sourcePage.value = 1;
  sourceTables.value = [];
  sourceTotal.value = 0;
  void loadSourceTables();
}

function onTabChange(name: string | number) {
  if (name === 'source') {
    void loadSourceTables();
  } else {
    void loadImportedTables();
  }
}

loadDataSources();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 数据源选择 -->
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px">
        <span style="font-weight: 600; white-space: nowrap">数据源：</span>
        <ElSelect
          v-model="selectedDsId"
          style="width: 280px"
          @change="handleDsChange"
        >
          <ElOption
            v-for="ds in dsOptions"
            :key="ds.value"
            :label="ds.label"
            :value="ds.value"
          />
        </ElSelect>
      </div>

      <ElTabs v-model="activeTab" @tab-change="onTabChange">
        <!-- Tab 1：数据源表（可导入） -->
        <ElTabPane label="数据源表" name="source">
          <ElForm :inline="true">
            <ElFormItem label="表名称">
              <ElInput
                v-model="tableKeyword"
                clearable
                placeholder="请输入表名称或表注释"
                @keyup.enter="
                  sourcePage = 1;
                  loadSourceTables();
                "
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :icon="Search" @click="sourcePage = 1; loadSourceTables()">
                搜索
              </ElButton>
              <ElButton
                :icon="Refresh"
                @click="
                  tableKeyword = '';
                  sourcePage = 1;
                  loadSourceTables();
                "
              >
                重置
              </ElButton>
              <ElButton
                type="success"
                :disabled="sourceTables.length === 0"
                @click="doBatchImport"
              >
                批量导入
              </ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable v-loading="loading" :data="sourceTables" border>
            <ElTableColumn prop="tableName" label="表名称" min-width="180" />
            <ElTableColumn prop="tableComment" label="表描述" min-width="200" show-overflow-tooltip />
            <ElTableColumn prop="engine" label="引擎" width="100" />
            <ElTableColumn prop="tableRows" label="行数" width="100" />
            <ElTableColumn label="操作" width="120" align="center" fixed="right">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  :icon="Download"
                  v-access:code="'gen:gen-table:add'"
                  @click="doImport(scope.row.tableName)"
                >
                  导入配置
                </ElButton>
              </template>
            </ElTableColumn>
            <template #empty>
              <ElEmpty description="暂无数据表" />
            </template>
          </ElTable>

          <div v-if="sourceTotal > 0" style="margin-top: 16px; text-align: right">
            <ElPagination
              v-model:current-page="sourcePage"
              v-model:page-size="sourceSize"
              :total="sourceTotal"
              :page-sizes="[10, 20, 50]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadSourceTables"
              @size-change="loadSourceTables"
            />
          </div>
        </ElTabPane>

        <!-- Tab 2：已导入表（管理配置） -->
        <ElTabPane label="已导入表" name="imported">
          <ElForm :inline="true">
            <ElFormItem label="关键字">
              <ElInput
                v-model="importKeyword"
                clearable
                placeholder="表名/注释/类名"
                @keyup.enter="
                  importPage = 1;
                  loadImportedTables();
                "
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :icon="Search" @click="importPage = 1; loadImportedTables()">
                搜索
              </ElButton>
              <ElButton
                :icon="Refresh"
                @click="
                  importKeyword = '';
                  importPage = 1;
                  loadImportedTables();
                "
              >
                重置
              </ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable v-loading="loading" :data="importedTables" border>
            <ElTableColumn prop="tableName" label="表名称" min-width="180" />
            <ElTableColumn prop="tableComment" label="表描述" min-width="180" show-overflow-tooltip />
            <ElTableColumn prop="className" label="类名" width="160" />
            <ElTableColumn prop="moduleName" label="模块" width="120" />
            <ElTableColumn prop="functionAuthor" label="作者" width="100" />
            <ElTableColumn prop="columnCount" label="字段数" width="80" align="center" />
            <ElTableColumn label="操作" width="280" align="center" fixed="right">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  :icon="Edit"
                  v-access:code="'gen:gen-table:edit'"
                  @click="openColumnConfig(asImportedRow(scope.row))"
                >
                  配置字段
                </ElButton>
                <ElButton
                  link
                  type="success"
                  :icon="Download"
                  v-access:code="'gen:gen-table:download'"
                  @click="openGenerate(asImportedRow(scope.row))"
                >
                  生成代码
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  :icon="Delete"
                  @click="deleteImported(asImportedRow(scope.row))"
                >
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
            <template #empty>
              <ElEmpty description="暂无已导入表" />
            </template>
          </ElTable>

          <div v-if="importTotal > 0" style="margin-top: 16px; text-align: right">
            <ElPagination
              v-model:current-page="importPage"
              v-model:page-size="importSize"
              :total="importTotal"
              :page-sizes="[10, 20, 50]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadImportedTables"
              @size-change="loadImportedTables"
            />
          </div>
        </ElTabPane>
      </ElTabs>
    </div>

    <!-- 列配置编辑弹窗 -->
    <ElDialog
      v-model="columnDialogVisible"
      :title="`字段配置 - ${columnTableName}`"
      width="95%"
      top="40px"
    >
      <div v-loading="columnDialogLoading">
        <ElTable :data="columns" border max-height="60vh">
          <ElTableColumn label="序号" type="index" width="60" align="center" />
          <ElTableColumn prop="columnName" label="字段名" width="150" />
          <ElTableColumn label="注释" min-width="140">
            <template #default="scope">
              <ElInput v-model="scope.row.columnComment" size="small" placeholder="字段注释" />
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
                <ElOption label="LocalDateTime" value="java.time.LocalDateTime" />
                <ElOption label="LocalDate" value="java.time.LocalDate" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn label="Java属性" width="130">
            <template #default="scope">
              <ElInput v-model="scope.row.javaField" size="small" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="插入" width="65" align="center">
            <template #default="scope">
              <ElTag
                :type="scope.row.insert ? 'success' : 'info'"
                style="cursor: pointer"
                @click="scope.row.insert = !scope.row.insert"
              >
                {{ scope.row.insert ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="编辑" width="65" align="center">
            <template #default="scope">
              <ElTag
                :type="scope.row.edit ? 'success' : 'info'"
                style="cursor: pointer"
                @click="scope.row.edit = !scope.row.edit"
              >
                {{ scope.row.edit ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="列表" width="65" align="center">
            <template #default="scope">
              <ElTag
                :type="scope.row.list ? 'success' : 'info'"
                style="cursor: pointer"
                @click="scope.row.list = !scope.row.list"
              >
                {{ scope.row.list ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="查询" width="65" align="center">
            <template #default="scope">
              <ElTag
                :type="scope.row.query ? 'success' : 'info'"
                style="cursor: pointer"
                @click="scope.row.query = !scope.row.query"
              >
                {{ scope.row.query ? '是' : '否' }}
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
          <ElTableColumn label="必填" width="65" align="center">
            <template #default="scope">
              <ElTag
                :type="scope.row.required ? 'danger' : 'info'"
                style="cursor: pointer"
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
              <ElInput v-model="scope.row.dictType" size="small" placeholder="字典编码" />
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <template #footer>
        <ElButton @click="columnDialogVisible = false">关闭</ElButton>
        <ElButton type="primary" :loading="columnDialogLoading" @click="saveColumnConfig">
          保存配置
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>
