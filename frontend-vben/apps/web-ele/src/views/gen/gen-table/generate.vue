<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { QuestionFilled } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElCol,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  downloadCode,
  generateCode,
  getTableConfig,
  getTableDetail,
  previewCode,
  saveTableColumns,
} from '#/api/gen/table';

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

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const activeTab = ref('info');

const state = reactive({
  form: {
    tableName: '',
    tableComment: '',
    tableId: 0,
    className: '',
    packageName: '',
    moduleName: '',
    businessName: '',
    functionName: '',
    functionAuthor: '',
    includeBackend: true,
    includeFrontend: true,
    overwrite: false,
    autoRegister: false,
  },
});

const columns = ref<ColumnRow[]>([]);
const tableHeight = ref(`${document.documentElement.scrollHeight - 280}px`);
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewFiles = ref<Array<{ content: string; fileName: string }>>([]);
const previewActiveFile = ref('');
const generateLoading = ref(false);
const generateResult = ref<any>(null);
const generateResultVisible = ref(false);

const rules = ref({
  packageName: [
    { required: true, message: '请输入生成包路径', trigger: 'blur' },
  ],
  moduleName: [
    { required: true, message: '请输入生成模块名', trigger: 'blur' },
  ],
  className: [{ required: true, message: '请输入实体类名称', trigger: 'blur' }],
});

function toCamel(value: string, upperFirst: boolean): string {
  if (!value) return '';
  const parts = value.toLowerCase().split('_');
  const result = parts
    .filter(Boolean)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join('');
  if (!upperFirst && result.length > 0) {
    return result.charAt(0).toLowerCase() + result.slice(1);
  }
  return result || 'Generated';
}

function stripPrefix(value: string): string {
  for (const prefix of ['sys_', 'wf_']) {
    if (value.startsWith(prefix)) return value.slice(prefix.length);
  }
  return value;
}

// 初始化页面数据
async function initPage() {
  const { tableId, tableName } = route.query;
  const tid = Number(tableId);
  const tname = typeof tableName === 'string' ? tableName : '';

  if (tid > 0) {
    // 从已导入表配置加载
    loading.value = true;
    try {
      const res: any = await getTableConfig(tid);
      const table = res.table ?? {};
      state.form.tableId = tid;
      state.form.tableName = table.tableName ?? tname;
      state.form.tableComment = table.tableComment ?? '';
      state.form.className =
        table.className ?? toCamel(stripPrefix(state.form.tableName), true);
      state.form.packageName = table.packageName ?? '';
      state.form.moduleName = table.moduleName ?? '';
      state.form.businessName = table.businessName ?? '';
      state.form.functionName = table.functionName ?? '';
      state.form.functionAuthor = table.functionAuthor ?? '';
      columns.value = res.columns ?? [];
    } finally {
      loading.value = false;
    }
  } else if (tname) {
    // 从原始表加载字段
    state.form.tableName = tname;
    state.form.className = toCamel(stripPrefix(tname), true);
    loading.value = true;
    try {
      const res: any = await getTableDetail(tname);
      state.form.tableComment = res.table?.tableComment ?? '';
      columns.value = (res.columns ?? []).map((col: any, idx: number) => ({
        id: idx,
        columnName: col.columnName ?? '',
        columnComment: col.columnComment ?? '',
        columnType: col.columnType ?? '',
        dataType: col.dataType ?? '',
        javaType: col.javaType ?? 'String',
        javaField: col.javaField ?? '',
        primaryKey: col.primaryKey ?? false,
        required: col.required ?? false,
        insert: col.insert ?? true,
        edit: col.edit ?? true,
        list: col.list ?? true,
        query: col.query ?? false,
        queryType: col.queryType ?? 'EQ',
        htmlType: col.htmlType ?? 'input',
        dictType: col.dictType ?? '',
        sort: idx,
      }));
    } finally {
      loading.value = false;
    }
  }
}

// 保存列配置
async function saveColumnConfigs() {
  if (!state.form.tableId) return;
  loading.value = true;
  try {
    await saveTableColumns(state.form.tableId, columns.value);
    ElMessage.success('字段配置已保存');
  } finally {
    loading.value = false;
  }
}

// 生成并下载代码
async function submitForm() {
  loading.value = true;
  try {
    const response = await downloadCode({
      tableName: state.form.tableName,
      moduleName: state.form.moduleName,
      packageName: state.form.packageName,
      className: state.form.className,
      includeBackend: state.form.includeBackend,
      includeFrontend: state.form.includeFrontend,
      overwrite: state.form.overwrite,
      autoRegister: state.form.autoRegister,
    });
    const blob =
      response instanceof Blob
        ? response
        : new Blob([response as any], { type: 'application/zip' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${state.form.moduleName || 'codegen'}.zip`;
    document.body.append(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
    ElMessage.success('代码生成成功');
  } catch {
    ElMessage.error('代码生成失败，请检查参数和后端服务');
  } finally {
    loading.value = false;
  }
}

// 预览生成结果
async function previewGenCode() {
  if (
    !state.form.packageName ||
    !state.form.moduleName ||
    !state.form.className
  ) {
    ElMessage.warning('请先填写生成包路径、模块名和实体类名称');
    return;
  }
  previewLoading.value = true;
  previewVisible.value = true;
  try {
    const res: any = await previewCode({
      tableName: state.form.tableName,
      moduleName: state.form.moduleName,
      packageName: state.form.packageName,
      className: state.form.className,
      includeBackend: state.form.includeBackend,
      includeFrontend: state.form.includeFrontend,
      autoRegister: state.form.autoRegister,
    });
    previewFiles.value = Array.isArray(res) ? res : (res?.files ?? []);
    const firstFile = previewFiles.value[0];
    if (firstFile) {
      previewActiveFile.value = firstFile.fileName;
    }
  } catch {
    ElMessage.error('预览失败，请检查参数');
  } finally {
    previewLoading.value = false;
  }
}

// 生成代码到文件系统
async function generateCodeToFile() {
  if (state.form.overwrite) {
    try {
      await ElMessageBox.confirm(
        '覆盖模式将直接覆盖已有文件，确认继续？',
        '覆盖确认',
        { type: 'warning' },
      );
    } catch {
      return;
    }
  }
  if (state.form.autoRegister) {
    try {
      await ElMessageBox.confirm(
        '自动注册菜单将在生成后自动创建菜单和权限项，确认继续？',
        '注册确认',
        { type: 'info' },
      );
    } catch {
      return;
    }
  }
  generateLoading.value = true;
  try {
    const res: any = await generateCode({
      tableName: state.form.tableName,
      moduleName: state.form.moduleName,
      packageName: state.form.packageName,
      className: state.form.className,
      includeBackend: state.form.includeBackend,
      includeFrontend: state.form.includeFrontend,
      overwrite: state.form.overwrite,
      autoRegister: state.form.autoRegister,
    });
    generateResult.value = res;
    generateResultVisible.value = true;
    ElMessage.success('代码已生成到文件系统');
  } catch {
    ElMessage.error('代码生成失败，请检查参数和后端服务');
  } finally {
    generateLoading.value = false;
  }
}

initPage();

function getPreviewContent() {
  const file = previewFiles.value.find(
    (f) => f.fileName === previewActiveFile.value,
  );
  return file?.content ?? '// 请选择文件查看预览';
}
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm
        :model="state.form"
        :rules="rules"
        label-width="150px"
        class="m-2"
      >
        <ElTabs v-model="activeTab">
          <!-- Tab 1：基本信息 -->
          <ElTabPane label="基本信息" name="info">
            <div v-loading="loading">
              <ElRow>
                <ElCol :span="12">
                  <ElFormItem prop="tableName" label="表名称">
                    <ElInput v-model="state.form.tableName" disabled />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem prop="tableComment" label="表描述">
                    <ElInput v-model="state.form.tableComment" disabled />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem prop="className" label="实体类名称">
                    <ElInput v-model="state.form.className" />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem prop="functionAuthor" label="生成作者">
                    <ElInput v-model="state.form.functionAuthor" />
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </div>
          </ElTabPane>

          <!-- Tab 2：字段信息 -->
          <ElTabPane label="字段信息" name="column">
            <div v-loading="loading">
              <div v-if="columns.length === 0" class="py-8">
                <ElEmpty
                  description="暂无字段信息，请先在数据表管理中导入表配置"
                />
              </div>
              <ElTable
                v-else
                :data="columns"
                row-key="id"
                :max-height="tableHeight"
                border
              >
                <ElTableColumn
                  label="序号"
                  type="index"
                  width="60"
                  align="center"
                />
                <ElTableColumn
                  label="字段列名"
                  prop="columnName"
                  width="150"
                  show-overflow-tooltip
                />
                <ElTableColumn label="字段描述" min-width="140">
                  <template #default="scope">
                    <ElInput
                      v-model="scope.row.columnComment"
                      size="small"
                      placeholder="字段描述"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  label="物理类型"
                  prop="columnType"
                  width="130"
                  show-overflow-tooltip
                />
                <ElTableColumn label="Java类型" width="130">
                  <template #default="scope">
                    <ElSelect v-model="scope.row.javaType" size="small">
                      <ElOption label="String" value="String" />
                      <ElOption label="Long" value="Long" />
                      <ElOption label="Integer" value="Integer" />
                      <ElOption label="Double" value="Double" />
                      <ElOption
                        label="BigDecimal"
                        value="java.math.BigDecimal"
                      />
                      <ElOption label="Instant" value="java.time.Instant" />
                      <ElOption label="LocalDate" value="java.time.LocalDate" />
                      <ElOption label="LocalTime" value="java.time.LocalTime" />
                      <ElOption label="Boolean" value="Boolean" />
                    </ElSelect>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="Java属性" width="130">
                  <template #default="scope">
                    <ElInput v-model="scope.row.javaField" size="small" />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="插入" width="60" align="center">
                  <template #default="scope">
                    <ElCheckbox
                      :model-value="scope.row.insert"
                      @change="scope.row.insert = !scope.row.insert"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="编辑" width="60" align="center">
                  <template #default="scope">
                    <ElCheckbox
                      :model-value="scope.row.edit"
                      @change="scope.row.edit = !scope.row.edit"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="列表" width="60" align="center">
                  <template #default="scope">
                    <ElCheckbox
                      :model-value="scope.row.list"
                      @change="scope.row.list = !scope.row.list"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="查询" width="60" align="center">
                  <template #default="scope">
                    <ElCheckbox
                      :model-value="scope.row.query"
                      @change="scope.row.query = !scope.row.query"
                    />
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
                <ElTableColumn label="必填" width="60" align="center">
                  <template #default="scope">
                    <ElCheckbox
                      :model-value="scope.row.required"
                      @change="scope.row.required = !scope.row.required"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="显示类型" width="120">
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
                      size="small"
                      placeholder="字典编码"
                    />
                  </template>
                </ElTableColumn>
              </ElTable>

              <div
                v-if="columns.length > 0"
                style="margin-top: 12px; text-align: right"
              >
                <ElButton
                  type="primary"
                  :disabled="!state.form.tableId"
                  :loading="loading"
                  @click="saveColumnConfigs"
                >
                  保存字段配置
                </ElButton>
              </div>
            </div>
          </ElTabPane>

          <!-- Tab 3：生成信息 -->
          <ElTabPane label="生成信息" name="gen">
            <div v-loading="loading">
              <ElRow>
                <ElCol :span="12">
                  <ElFormItem prop="packageName">
                    <template #label>
                      <span>
                        生成包路径
                        <ElTooltip
                          content="生成在哪个Java包下，例如 com.enterprise.auth.platform"
                          placement="top"
                        >
                          <ElIcon><QuestionFilled /></ElIcon>
                        </ElTooltip>
                      </span>
                    </template>
                    <ElInput
                      v-model="state.form.packageName"
                      placeholder="例如 com.enterprise.auth.platform"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem prop="moduleName">
                    <template #label>
                      <span>
                        生成模块名
                        <ElTooltip
                          content="可理解为子系统名，例如 system"
                          placement="top"
                        >
                          <ElIcon><QuestionFilled /></ElIcon>
                        </ElTooltip>
                      </span>
                    </template>
                    <ElInput
                      v-model="state.form.moduleName"
                      placeholder="例如 system"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="生成后端">
                    <ElRadioGroup v-model="state.form.includeBackend">
                      <ElRadio :value="true">是</ElRadio>
                      <ElRadio :value="false">否</ElRadio>
                    </ElRadioGroup>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="生成前端">
                    <ElRadioGroup v-model="state.form.includeFrontend">
                      <ElRadio :value="true">是</ElRadio>
                      <ElRadio :value="false">否</ElRadio>
                    </ElRadioGroup>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="覆盖已有文件">
                    <ElRadioGroup v-model="state.form.overwrite">
                      <ElRadio :value="true">是</ElRadio>
                      <ElRadio :value="false">否</ElRadio>
                    </ElRadioGroup>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="自动注册菜单">
                    <ElRadioGroup v-model="state.form.autoRegister">
                      <ElRadio :value="true">是</ElRadio>
                      <ElRadio :value="false">否</ElRadio>
                    </ElRadioGroup>
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </div>
          </ElTabPane>
        </ElTabs>
      </ElForm>

      <div class="mt-2 flex items-end justify-center" style="gap: 16px">
        <ElButton style="width: 120px" @click="router.back()">返回</ElButton>
        <ElButton
          style="width: 120px"
          @click="previewGenCode"
          :loading="previewLoading"
        >
          预览代码
        </ElButton>
        <ElButton
          style="width: 140px"
          @click="generateCodeToFile"
          :loading="generateLoading"
        >
          生成到目录
        </ElButton>
        <ElButton
          style="width: 160px"
          type="primary"
          :loading="loading"
          @click="submitForm"
        >
          生成代码并下载
        </ElButton>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <ElDialog v-model="previewVisible" title="代码预览" width="80%" top="40px">
      <div
        v-loading="previewLoading"
        style="display: flex; gap: 12px; height: 70vh"
      >
        <!-- 文件列表 -->
        <div
          style="
            width: 240px;
            padding-right: 8px;
            overflow-y: auto;
            border-right: 1px solid #ebeef5;
          "
        >
          <div
            v-for="file in previewFiles"
            :key="file.fileName"
            style="
              padding: 6px 10px;
              font-size: 13px;
              cursor: pointer;
              border-radius: 4px;
            "
            :style="{
              background:
                previewActiveFile === file.fileName ? '#ecf5ff' : 'transparent',
              color:
                previewActiveFile === file.fileName ? '#409eff' : '#606266',
              fontWeight:
                previewActiveFile === file.fileName ? '600' : 'normal',
            }"
            @click="previewActiveFile = file.fileName"
          >
            {{ file.fileName }}
          </div>
        </div>
        <!-- 代码内容 -->
        <div style="flex: 1; overflow: auto">
          <pre
            style="
              min-height: 100%;
              padding: 12px;
              margin: 0;
              font-size: 13px;
              line-height: 1.6;
              color: #e2e8f0;
              white-space: pre-wrap;
              background: #0f172a;
              border-radius: 8px;
            "
            >{{ getPreviewContent() }}</pre
          >
        </div>
      </div>
    </ElDialog>

    <!-- 生成结果弹窗 -->
    <ElDialog v-model="generateResultVisible" title="生成结果" width="600px">
      <div v-if="generateResult">
        <ElTag
          type="success"
          size="large"
          effect="dark"
          style="margin-bottom: 16px"
        >
          代码已成功生成
        </ElTag>
        <div
          v-if="generateResult.generatedFiles?.length"
          style="margin-bottom: 16px"
        >
          <h4 style="margin-bottom: 8px">
            生成文件 ({{ generateResult.generatedFiles.length }})
          </h4>
          <div
            style="
              max-height: 200px;
              padding: 8px;
              overflow-y: auto;
              border: 1px solid #ebeef5;
              border-radius: 4px;
            "
          >
            <div
              v-for="file in generateResult.generatedFiles"
              :key="file"
              style="padding: 2px 0; font-size: 13px; color: #606266"
            >
              {{ file }}
            </div>
          </div>
        </div>
        <div v-if="generateResult.registeredPermissions?.length">
          <h4 style="margin-bottom: 8px">
            注册权限 ({{ generateResult.registeredPermissions.length }})
          </h4>
          <div style="display: flex; flex-wrap: wrap; gap: 4px">
            <ElTag
              v-for="perm in generateResult.registeredPermissions"
              :key="perm"
              size="small"
              effect="plain"
            >
              {{ perm }}
            </ElTag>
          </div>
        </div>
      </div>
    </ElDialog>
  </div>
</template>
