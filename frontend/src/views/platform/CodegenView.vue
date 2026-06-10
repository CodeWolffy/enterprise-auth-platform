<template>
  <div class="panel-stack codegen-page">
    <section class="dashboard-grid">
      <article class="stat-card codegen-stat codegen-stat--primary">
        <span class="eyebrow">Tables</span>
        <strong>{{ tablePage.total }}</strong>
        <span>当前库可生成表</span>
      </article>
      <article class="stat-card codegen-stat">
        <span class="eyebrow">Preview</span>
        <strong>{{ previewResult?.files.length ?? 0 }}</strong>
        <span>本次预览文件数</span>
      </article>
      <article class="stat-card codegen-stat">
        <span class="eyebrow">Selected</span>
        <strong>{{ selectedFilePaths.length }}</strong>
        <span>勾选文件数</span>
      </article>
      <article class="stat-card codegen-stat">
        <span class="eyebrow">Generated</span>
        <strong>{{ generatedFiles.length }}</strong>
        <span>最近生成文件数</span>
      </article>
    </section>

    <el-tabs v-model="activeTab" class="codegen-tabs">
      <el-tab-pane label="代码生成" name="generate">
        <section class="dashboard-panel codegen-console">
          <div class="panel-head">
            <div>
              <span class="eyebrow">P2 代码生成</span>
              <h3>CRUD 生成器</h3>
              <p class="muted-line">从 MySQL 元数据生成完整 CRUD：后端接口、请求模型、前端 API、类型和管理页面；默认落盘到隔离目录且不覆盖已有文件。</p>
            </div>
            <div class="panel-actions">
              <el-select v-model="selectedDataSourceId" class="datasource-select" placeholder="选择数据源" :loading="metadataLoading" @change="handleDataSourceChange">
                <el-option v-for="source in dataSources" :key="source.id" :label="source.name" :value="source.id">
                  <div class="datasource-option">
                    <span>{{ source.name }}</span>
                    <small>{{ source.jdbcUrl === 'LOCAL' ? '当前应用库' : source.host || source.dbName || source.jdbcUrl }}</small>
                    <el-tag v-if="source.external" size="small" :type="source.externalAuthorized ? 'success' : 'warning'" effect="plain">
                      {{ source.externalAuthorized ? '已授权' : '待授权' }}
                    </el-tag>
                  </div>
                </el-option>
              </el-select>
              <el-button
                v-if="selectedDataSource?.external && !selectedDataSource.externalAuthorized"
                v-permission="'codegen:write'"
                type="warning"
                :loading="authorizingDataSource"
                @click="authorizeSelectedDataSource"
              >确认授权</el-button>
              <el-button :disabled="!selectedDataSourceId || selectedDataSourceLocked" :loading="testingDataSource" @click="testSelectedDataSource">测试连接</el-button>
              <el-button v-permission="'codegen:write'" :disabled="selectedTableNames.length === 0 || !selectedDataSourceId || selectedDataSourceLocked" :loading="importingTables" @click="importSelectedTables">导入配置</el-button>
              <el-button :loading="importedLoading" @click="loadImportedTables">已导入配置</el-button>
              <el-button :loading="loading" @click="loadTables">刷新表</el-button>
              <el-button v-permission="'codegen:write'" type="primary" :disabled="!canGenerate" :loading="generating" @click="generateFiles">生成到隔离目录</el-button>
            </div>
          </div>

          <el-alert
            class="datasource-auth-alert"
            :title="dataSourceAuthAlert"
            :type="selectedDataSourceLocked ? 'warning' : selectedDataSource?.external ? 'info' : 'success'"
            show-icon
            :closable="false"
          />
          <el-alert
            class="datasource-auth-alert"
            title="推荐操作顺序：选择数据源 → 勾选表 → 导入配置 → 字段配置 → 生成预览 → 生成或下载产物。字段配置修改后必须重新预览。"
            type="info"
            show-icon
            :closable="false"
          />

          <AdvancedSearch @search="applySearch" @reset="resetSearch">
            <el-form-item label="关键字">
              <el-input v-model="query.keyword" placeholder="表名或表注释" clearable />
            </el-form-item>
          </AdvancedSearch>

          <div class="codegen-layout">
            <div class="codegen-table-card">
              <el-table v-loading="loading" :data="tablePage.records" stripe highlight-current-row @current-change="selectTable" @selection-change="handleTableSelectionChange">
                <el-table-column type="selection" width="44" />
                <el-table-column prop="tableName" label="表名" min-width="190" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="table-name-cell">
                      <strong>{{ row.tableName }}</strong>
                      <small>{{ row.tableComment || '无表注释' }}</small>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="engine" label="引擎" width="90" />
                <el-table-column prop="tableRows" label="估算行数" width="110" />
                <template #empty>
                  <el-empty description="暂无可生成数据表" />
                </template>
              </el-table>
              <div class="footer-bar compact-footer">
                <span>共 {{ tablePage.total }} 张表</span>
                <el-pagination
                  background
                  layout="prev, pager, next"
                  :current-page="query.page"
                  :page-size="query.size"
                  :total="tablePage.total"
                  @current-change="handlePageChange"
                />
              </div>
            </div>

            <div class="codegen-config-card">
              <el-form label-position="top" :model="form">
                <el-form-item label="目标表">
                  <el-input v-model="form.tableName" readonly placeholder="从左侧选择数据表" />
                </el-form-item>
                <el-form-item label="模块名">
                  <el-input v-model="form.moduleName" placeholder="例如 userProfile" @input="markPreviewStale" />
                </el-form-item>
                <el-form-item label="类名">
                  <el-input v-model="form.className" placeholder="例如 UserProfile" @input="markPreviewStale" />
                </el-form-item>
                <el-form-item label="包名">
                  <el-input v-model="form.packageName" placeholder="com.enterprise.auth.platform.generated" @input="markPreviewStale" />
                </el-form-item>
                <div class="scope-row">
                  <el-checkbox v-model="form.includeBackend" @change="markPreviewStale">后端</el-checkbox>
                  <el-checkbox v-model="form.includeFrontend" @change="markPreviewStale">前端</el-checkbox>
                  <el-checkbox v-model="form.overwrite" v-permission="'codegen:write'">允许覆盖</el-checkbox>
                  <el-checkbox v-model="form.autoRegister" v-permission="'codegen:write'" @change="markPreviewStale">自动注册菜单权限</el-checkbox>
                </div>
                <el-alert
                  class="safety-alert"
                  :title="safetyAlert"
                  :type="form.overwrite ? 'warning' : 'info'"
                  show-icon
                  :closable="false"
                />
                <el-button type="primary" :disabled="!canPreview" :loading="previewing" @click="previewFiles">生成预览</el-button>
              </el-form>

              <section v-if="tableDetail" class="column-panel">
                <div class="column-panel-head">
                  <span class="eyebrow">Columns</span>
                  <el-button size="small" :disabled="!selectedImportedTable" :loading="columnConfigLoading" @click="openColumnConfig">
                    字段配置
                  </el-button>
                </div>
                <p v-if="!selectedImportedTable" class="column-config-hint">导入当前表配置后，可编辑字段生成参数。</p>
                <div class="column-list">
                  <span v-for="column in tableDetail.columns" :key="column.columnName" class="column-pill" :class="{ 'column-pill--system': isSystemColumn(column.columnName) }">
                    {{ column.columnName }} · {{ column.javaType }}
                    <small v-if="column.primaryKey">PK</small>
                    <small v-else-if="isSystemColumn(column.columnName)">系统字段</small>
                  </span>
                </div>
              </section>
              <section v-if="importedTablePage.records.length" class="imported-panel">
                <div class="column-panel-head">
                  <span class="eyebrow">Imported Configs</span>
                  <small>{{ importedTablePage.total }} 个配置</small>
                </div>
                <div class="imported-list">
                  <button
                    v-for="item in importedTablePage.records"
                    :key="item.id"
                    class="imported-item"
                    :class="{ 'imported-item--active': item.tableName === form.tableName }"
                    type="button"
                    @click="openImportedTable(item)"
                  >
                    <strong>{{ item.tableName }}</strong>
                    <small>{{ item.columnCount ?? 0 }} 个字段 · {{ item.className || '未配置类名' }}</small>
                  </button>
                </div>
              </section>
            </div>
          </div>
        </section>

        <section v-if="previewResult" class="dashboard-panel code-preview-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Preview</span>
              <h3>生成预览</h3>
              <p class="muted-line">生成产物仅展示相对路径，后端输出位置由服务端托管。</p>
            </div>
            <div class="preview-actions">
              <el-tag :type="previewStale ? 'warning' : 'success'" effect="plain">
                {{ previewStale ? '配置已变更，请重新预览' : '预览可生成' }}
              </el-tag>
              <el-button size="small" :disabled="!activePreviewPath" @click="copyText(activePreviewPath, '预览路径已复制')">复制当前路径</el-button>
              <el-button v-permission="'codegen:download'" size="small" type="primary" :disabled="!canDownload" :loading="downloading" @click="downloadZip">下载产物</el-button>
            </div>
          </div>

          <div class="file-scope-bar">
            <span class="muted-inline">覆盖范围：</span>
            <el-button size="small" text @click="selectAllFiles(true)">全选</el-button>
            <el-button size="small" text @click="selectAllFiles(false)">全不选</el-button>
            <span class="muted-inline">已勾选 {{ selectedFilePaths.length }} / {{ previewResult.files.length }} 个文件</span>
          </div>

          <el-tabs v-model="activePreviewPath" type="border-card" class="preview-tabs">
            <el-tab-pane v-for="file in previewResult.files" :key="file.path" :label="shortPath(file.path)" :name="file.path">
              <template #label>
                <el-checkbox v-model="selectedFilePaths" :value="file.path" :label="file.path" class="file-scope-checkbox">
                  <span class="file-label-text">{{ shortPath(file.path) }}</span>
                </el-checkbox>
              </template>
              <div class="preview-path">{{ file.path }}</div>
              <pre>{{ file.content }}</pre>
            </el-tab-pane>
          </el-tabs>
        </section>

        <section v-if="generatedFiles.length" class="dashboard-panel generated-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Generated</span>
              <h3>最近生成</h3>
            </div>
            <el-tag v-if="generatedResources.length" effect="plain" type="success">已注册 {{ generatedResources.length }} 个权限</el-tag>
          </div>
          <div class="generated-list">
            <el-tag v-for="file in generatedFiles" :key="file" effect="plain" class="generated-file" @click="copyText(file, '生成路径已复制')">{{ file }}</el-tag>
          </div>
          <div v-if="generatedResources.length" class="generated-resources">
            <el-tag v-for="key in generatedResources" :key="key" effect="plain" type="success" class="generated-resource">{{ key }}</el-tag>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="自定义模板" name="template">
        <section class="dashboard-panel template-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Templates</span>
              <h3>生成模板</h3>
              <p class="muted-line">用 <code>&#123;&#123;className&#125;&#125;</code> 等占位符编写代码生成模板，按路径匹配覆盖默认实现。</p>
            </div>
            <el-button v-permission="'codegen:write'" type="primary" @click="openTemplateDialog()">新增模板</el-button>
          </div>
          <AdvancedSearch @search="applyTemplateSearch" @reset="resetTemplateSearch">
            <el-form-item label="关键字">
              <el-input v-model="templateQuery.keyword" placeholder="模板名称、路径、描述" clearable />
            </el-form-item>
            <el-form-item label="分类">
              <el-select v-model="templateQuery.templateCategory" clearable style="width: 160px">
                <el-option v-for="item in templateCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </AdvancedSearch>
          <el-table v-loading="templateLoading" :data="templatePage.records" stripe>
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column label="分类" width="110">
              <template #default="{ row }">
                <el-tag effect="plain">{{ templateCategoryLabel(row.templateCategory) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="language" label="语言" width="100" />
            <el-table-column prop="pathPattern" label="路径匹配" min-width="220" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="内置" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.builtin" type="info" effect="plain">内置</el-tag>
                <el-tag v-else effect="plain">自定义</el-tag>
              </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="200">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTemplateDialog(row)">编辑</el-button>
                <el-button v-permission="'codegen:write'" :disabled="row.builtin" link type="danger" @click="removeTemplate(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无模板" />
            </template>
          </el-table>
          <div class="footer-bar">
            <span>共 {{ templatePage.total }} 条模板</span>
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="templateQuery.page"
              :page-size="templateQuery.size"
              :total="templatePage.total"
              @current-change="handleTemplatePageChange"
            />
          </div>
        </section>

        <el-dialog v-model="templateDialogVisible" :title="editingTemplate?.id ? '编辑模板' : '新增模板'" width="720px">
          <el-form v-if="editingTemplate" label-position="top" :model="editingTemplate">
            <el-form-item label="名称" required>
              <el-input v-model="editingTemplate.name" :disabled="editingTemplate.builtin" />
            </el-form-item>
            <el-form-item label="语言" required>
              <el-select v-model="editingTemplate.language" :disabled="editingTemplate.builtin" placeholder="选择语言">
                <el-option value="java" label="Java" />
                <el-option value="typescript" label="TypeScript" />
                <el-option value="vue" label="Vue" />
              </el-select>
            </el-form-item>
            <el-form-item label="分类" required>
              <el-select v-model="editingTemplate.templateCategory" :disabled="editingTemplate.builtin" placeholder="选择分类">
                <el-option v-for="item in templateCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="路径匹配" required>
              <el-input v-model="editingTemplate.pathPattern" :disabled="editingTemplate.builtin" placeholder="支持关键字或正则，例如 Controller.java" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="editingTemplate.description" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="模板内容" required>
              <el-input v-model="editingTemplate.content" type="textarea" :rows="14" spellcheck="false" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="templateDialogVisible = false">取消</el-button>
            <el-button type="primary" :disabled="editingTemplate?.builtin" :loading="templateSaving" @click="saveTemplate">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="columnConfigDrawerVisible" size="82%" title="字段生成配置" destroy-on-close>
      <template v-if="tableConfigDetail">
        <div class="config-drawer-head">
          <div>
            <span class="eyebrow">{{ tableConfigDetail.table.tableName }}</span>
            <h3>{{ tableConfigDetail.table.className || tableConfigDetail.table.tableName }}</h3>
            <p class="muted-line">调整字段名、Java 类型、查询方式、表单控件和字典类型后，重新预览即可影响生成结果。</p>
          </div>
          <div class="panel-actions">
            <el-button @click="columnConfigDrawerVisible = false">关闭</el-button>
            <el-button type="primary" :loading="savingColumnConfig" @click="saveColumnConfig">保存字段配置</el-button>
          </div>
        </div>
        <el-table :data="editableColumns" stripe class="column-config-table">
          <el-table-column prop="columnName" label="字段" min-width="150" fixed />
          <el-table-column label="注释" min-width="170">
            <template #default="{ row }">
              <el-input v-model="row.columnComment" placeholder="字段注释" />
            </template>
          </el-table-column>
          <el-table-column label="Java 字段" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.javaField" placeholder="javaField" />
            </template>
          </el-table-column>
          <el-table-column label="Java 类型" min-width="170">
            <template #default="{ row }">
              <el-select v-model="row.javaType" filterable allow-create default-first-option>
                <el-option value="String" label="String" />
                <el-option value="Long" label="Long" />
                <el-option value="Integer" label="Integer" />
                <el-option value="java.math.BigDecimal" label="BigDecimal" />
                <el-option value="java.time.LocalDateTime" label="LocalDateTime" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="列表" width="76" align="center">
            <template #default="{ row }"><el-switch v-model="row.list" /></template>
          </el-table-column>
          <el-table-column label="查询" width="76" align="center">
            <template #default="{ row }"><el-switch v-model="row.query" /></template>
          </el-table-column>
          <el-table-column label="必填" width="76" align="center">
            <template #default="{ row }"><el-switch v-model="row.required" :disabled="row.primaryKey" /></template>
          </el-table-column>
          <el-table-column label="控件" min-width="130">
            <template #default="{ row }">
              <el-select v-model="row.htmlType">
                <el-option value="input" label="输入框" />
                <el-option value="textarea" label="文本域" />
                <el-option value="number" label="数字" />
                <el-option value="select" label="下拉" />
                <el-option value="datetime" label="日期时间" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="查询方式" min-width="120">
            <template #default="{ row }">
              <el-select v-model="row.queryType" :disabled="!row.query">
                <el-option value="EQ" label="等于" />
                <el-option value="LIKE" label="包含" />
                <el-option value="BETWEEN" label="区间" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="字典类型" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.dictType" placeholder="可选" />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="暂无字段配置" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import {
  authorizeCodegenDataSource,
  createCodegenTemplate,
  deleteCodegenTemplate,
  downloadCodegen,
  generateCodegen,
  getCodegenImportedTable,
  getCodegenTable,
  importCodegenTables,
  previewCodegen,
  queryCodegenDataSourceTables,
  queryCodegenDataSources,
  queryCodegenImportedTables,
  queryCodegenTables,
  queryCodegenTemplates,
  testCodegenDataSource,
  updateCodegenImportedTableColumns,
  updateCodegenTemplate,
} from '@/api/modules'
import type {
  CodegenDataSourceView,
  CodegenImportedTablePage,
  CodegenImportedTableView,
  CodegenPreviewResult,
  CodegenRequest,
  CodegenTableConfigDetailView,
  CodegenTableDetailView,
  CodegenTablePage,
  CodegenTemplatePage,
  CodegenTemplateView,
} from '@/types/codegen'

const activeTab = ref<'generate' | 'template'>('generate')

const loading = ref(false)
const previewing = ref(false)
const generating = ref(false)
const downloading = ref(false)
const tableDetail = ref<CodegenTableDetailView | null>(null)
const previewResult = ref<CodegenPreviewResult | null>(null)
const previewStale = ref(false)
const activePreviewPath = ref('')
const generatedFiles = ref<string[]>([])
const generatedResources = ref<string[]>([])
const selectedFilePaths = ref<string[]>([])
const dataSources = ref<CodegenDataSourceView[]>([])
const selectedDataSourceId = ref<number | null>(null)
const metadataLoading = ref(false)
const testingDataSource = ref(false)
const authorizingDataSource = ref(false)
const importingTables = ref(false)
const importedLoading = ref(false)
const selectedTableNames = ref<string[]>([])
const importedTablePage = ref<CodegenImportedTablePage>({ total: 0, page: 1, size: 10, records: [] })
const columnConfigDrawerVisible = ref(false)
const columnConfigLoading = ref(false)
const savingColumnConfig = ref(false)
const tableConfigDetail = ref<CodegenTableConfigDetailView | null>(null)

const query = reactive({
  keyword: '',
  page: 1,
  size: 10,
})

const form = reactive({
  tableName: '',
  moduleName: '',
  className: '',
  packageName: 'com.enterprise.auth.platform.generated',
  includeBackend: true,
  includeFrontend: true,
  overwrite: false,
  autoRegister: false,
})

const tablePage = ref<CodegenTablePage>({ total: 0, page: 1, size: 10, records: [] })
const canPreview = computed(() => Boolean(form.tableName) && (form.includeBackend || form.includeFrontend))
const selectedValidFilePaths = computed(() => {
  const previewPaths = new Set(previewResult.value?.files.map((file) => file.path) ?? [])
  return selectedFilePaths.value.filter((path) => previewPaths.has(path))
})
const canGenerate = computed(() => Boolean(previewResult.value) && !previewStale.value && selectedValidFilePaths.value.length > 0 && !generating.value)
const canDownload = computed(() => Boolean(previewResult.value) && selectedValidFilePaths.value.length > 0 && !downloading.value)
const selectedImportedTable = computed(() => importedTablePage.value.records.find((item) => item.tableName === form.tableName) ?? null)
const selectedDataSource = computed(() => dataSources.value.find((item) => item.id === selectedDataSourceId.value) ?? null)
const selectedDataSourceLocked = computed(() => Boolean(selectedDataSource.value?.external && !selectedDataSource.value.externalAuthorized))
const dataSourceAuthAlert = computed(() => {
  const source = selectedDataSource.value
  if (!source) {
    return '请选择数据源后再读取数据表。'
  }
  if (!source.external) {
    return '当前应用库已默认授权，仍只允许读取白名单内的数据表。'
  }
  if (!source.externalAuthorized) {
    return '外部数据源尚未显式授权，不能读取表结构、导入配置或测试连接。'
  }
  return source.authorizationNote || '外部数据源已完成显式授权，但连接执行器仍按后端开关控制。'
})
const editableColumns = computed(() => tableConfigDetail.value?.columns ?? [])
const safetyAlert = computed(() => {
  const segments = [form.overwrite ? '已启用覆盖：生成时会替换隔离目录中的同名文件。' : '安全模式：生成时遇到同名文件会停止，不会覆盖。']
  if (form.autoRegister) {
    segments.push('自动注册：完成后会在平台资源树新建菜单与 API 资源，并授予 ADMIN 角色。')
  }
  return segments.join(' ')
})

void initializeCodegenPage()

async function initializeCodegenPage() {
  await loadDataSources()
  await Promise.all([loadTables(), loadImportedTables()])
}

async function loadDataSources() {
  metadataLoading.value = true
  try {
    dataSources.value = await queryCodegenDataSources()
    if (!selectedDataSourceId.value) {
      selectedDataSourceId.value = dataSources.value[0]?.id ?? null
    }
  } finally {
    metadataLoading.value = false
  }
}

async function loadTables() {
  loading.value = true
  try {
    if (selectedDataSourceLocked.value) {
      tablePage.value = { total: 0, page: query.page, size: query.size, records: [] }
      return
    }
    if (selectedDataSourceId.value) {
      tablePage.value = await queryCodegenDataSourceTables(selectedDataSourceId.value, {
        keyword: query.keyword || undefined,
        page: query.page,
        size: query.size,
      })
    } else {
      tablePage.value = await queryCodegenTables({
        keyword: query.keyword || undefined,
        page: query.page,
        size: query.size,
      })
    }
  } finally {
    loading.value = false
  }
}

function applySearch() {
  query.page = 1
  void loadTables()
}

function resetSearch() {
  query.keyword = ''
  query.page = 1
  void loadTables()
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage
  await loadTables()
}

async function selectTable(row?: { tableName: string }) {
  if (!row) {
    return
  }
  form.tableName = row.tableName
  form.moduleName = toCamel(stripPrefix(row.tableName), false)
  form.className = toCamel(stripPrefix(row.tableName), true)
  previewResult.value = null
  previewStale.value = false
  activePreviewPath.value = ''
  selectedFilePaths.value = []
  generatedFiles.value = []
  generatedResources.value = []
  tableDetail.value = await getCodegenTable(row.tableName)
}

function handleTableSelectionChange(rows: Array<{ tableName: string }>) {
  selectedTableNames.value = rows.map((row) => row.tableName)
}

async function handleDataSourceChange() {
  query.page = 1
  selectedTableNames.value = []
  tableDetail.value = null
  form.tableName = ''
  await loadTables()
}

async function testSelectedDataSource() {
  if (!selectedDataSourceId.value) {
    return
  }
  testingDataSource.value = true
  try {
    const result = await testCodegenDataSource(selectedDataSourceId.value)
    if (result.success) {
      ElMessage.success(result.message)
    } else {
      ElMessage.warning(result.message)
    }
  } finally {
    testingDataSource.value = false
  }
}

async function authorizeSelectedDataSource() {
  const source = selectedDataSource.value
  if (!source || !source.external) {
    return
  }
  const note = `已确认 ${source.name} 属于当前租户授权范围，仅用于代码生成元数据读取。`
  await ElMessageBox.confirm(
    '确认后会记录外部数据源已授权。当前版本仍不会直接启用外部连接执行器。',
    '外部数据源授权确认',
    { type: 'warning' },
  )
  authorizingDataSource.value = true
  try {
    const updated = await authorizeCodegenDataSource(source.id, note)
    dataSources.value = dataSources.value.map((item) => (item.id === updated.id ? updated : item))
    ElMessage.success('外部数据源授权状态已更新')
    await loadTables()
  } finally {
    authorizingDataSource.value = false
  }
}

async function importSelectedTables() {
  if (!selectedDataSourceId.value || selectedTableNames.value.length === 0) {
    return
  }
  importingTables.value = true
  try {
    const imported = await importCodegenTables({
      dataSourceId: selectedDataSourceId.value,
      tableNames: selectedTableNames.value,
      packageName: form.packageName,
      author: 'system',
    })
    ElMessage.success(`已导入 ${imported.length} 张表配置`)
    await loadImportedTables()
  } finally {
    importingTables.value = false
  }
}

async function loadImportedTables() {
  importedLoading.value = true
  try {
    importedTablePage.value = await queryCodegenImportedTables({ page: 1, size: 10 })
  } finally {
    importedLoading.value = false
  }
}

async function openImportedTable(table: CodegenImportedTableView) {
  await selectTable({ tableName: table.tableName })
  await openColumnConfig(table)
}

async function openColumnConfig(table = selectedImportedTable.value) {
  if (!table) {
    ElMessage.warning('请先导入当前表配置')
    return
  }
  columnConfigLoading.value = true
  try {
    tableConfigDetail.value = await getCodegenImportedTable(table.id)
    columnConfigDrawerVisible.value = true
  } finally {
    columnConfigLoading.value = false
  }
}

async function saveColumnConfig() {
  if (!tableConfigDetail.value) {
    return
  }
  savingColumnConfig.value = true
  try {
    tableConfigDetail.value = await updateCodegenImportedTableColumns(tableConfigDetail.value.table.id, editableColumns.value)
    markPreviewStale()
    await getCodegenTable(tableConfigDetail.value.table.tableName).then((detail) => {
      if (detail.table.tableName === form.tableName) {
        tableDetail.value = detail
      }
    })
    ElMessage.success('字段配置已保存，请重新生成预览')
  } finally {
    savingColumnConfig.value = false
  }
}

async function previewFiles() {
  previewing.value = true
  try {
    previewResult.value = await previewCodegen(toPayload())
    previewStale.value = false
    selectedFilePaths.value = previewResult.value.files.map((file) => file.path)
    activePreviewPath.value = previewResult.value.files[0]?.path ?? ''
    ElMessage.success('生成预览已刷新')
  } finally {
    previewing.value = false
  }
}

async function generateFiles() {
  if (previewStale.value) {
    ElMessage.warning('配置已变更，请重新生成预览')
    return
  }
  if (selectedFilePaths.value.length === 0 || selectedValidFilePaths.value.length === 0) {
    ElMessage.warning('请至少选择一个生成文件')
    return
  }
  const message = form.overwrite
    ? '已启用覆盖，确认替换隔离目录中的同名文件？'
    : '确认生成代码到隔离目录？遇到同名文件会停止，不会覆盖。'
  await ElMessageBox.confirm(message, '生成确认', { type: form.overwrite ? 'warning' : 'info' })
  generating.value = true
  try {
    const result = await generateCodegen(toPayload())
    generatedFiles.value = result.files
    generatedResources.value = result.registeredResourceKeys ?? []
    ElMessage.success(`已生成 ${result.files.length} 个文件${generatedResources.value.length ? `，注册 ${generatedResources.value.length} 个权限` : ''}`)
  } finally {
    generating.value = false
  }
}

async function downloadZip() {
  if (!previewResult.value) {
    return
  }
  downloading.value = true
  try {
    const blob = await downloadCodegen(toPayload())
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = `${previewResult.value.moduleName}-${previewResult.value.className}.zip`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1500)
    ElMessage.success('已开始下载')
  } finally {
    downloading.value = false
  }
}

function markPreviewStale() {
  if (previewResult.value) {
    previewStale.value = true
  }
}

async function copyText(value: string, message: string) {
  if (!value) {
    return
  }
  await navigator.clipboard.writeText(value)
  ElMessage.success(message)
}

function toPayload(): CodegenRequest {
  return {
    tableName: form.tableName,
    moduleName: form.moduleName || undefined,
    packageName: form.packageName || undefined,
    className: form.className || undefined,
    includeBackend: form.includeBackend,
    includeFrontend: form.includeFrontend,
    overwrite: form.overwrite,
    selectedFiles: selectedValidFilePaths.value,
    autoRegister: form.autoRegister,
  }
}

function selectAllFiles(selected: boolean) {
  if (!previewResult.value) {
    return
  }
  selectedFilePaths.value = selected ? previewResult.value.files.map((file) => file.path) : []
}

function stripPrefix(value: string) {
  if (value.startsWith('sys_')) {
    return value.slice(4)
  }
  if (value.startsWith('wf_')) {
    return value.slice(3)
  }
  return value
}

function toCamel(value: string, upperFirst: boolean) {
  const result = value
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('') || 'Generated'
  return upperFirst ? result : result.charAt(0).toLowerCase() + result.slice(1)
}

function shortPath(path: string) {
  const parts = path.split('/')
  return parts.slice(-2).join('/')
}

function isSystemColumn(columnName: string) {
  return ['id', 'tenant_id', 'created_by', 'updated_by', 'deleted', 'created_at', 'updated_at'].includes(columnName.toLowerCase())
}

function templateCategoryLabel(value?: string | null) {
  return templateCategoryOptions.find((item) => item.value === value)?.label || value || '未分类'
}

watch(activeTab, (value) => {
  if (value === 'template') {
    void loadTemplates()
  }
})

// 模板管理
const templateLoading = ref(false)
const templateSaving = ref(false)
const templateDialogVisible = ref(false)
const templatePage = ref<CodegenTemplatePage>({ total: 0, page: 1, size: 20, records: [] })
const templateQuery = reactive({ keyword: '', templateCategory: '', page: 1, size: 20 })
const templateCategoryOptions = [
  { value: 'backend', label: '后端' },
  { value: 'frontend', label: '前端' },
  { value: 'api', label: '前端 API' },
  { value: 'type', label: '前端类型' },
  { value: 'view', label: '页面视图' },
] as const
const editingTemplate = ref<CodegenTemplateView | null>(null)

async function loadTemplates() {
  templateLoading.value = true
  try {
    templatePage.value = await queryCodegenTemplates({
      keyword: templateQuery.keyword || undefined,
      templateCategory: templateQuery.templateCategory || undefined,
      page: templateQuery.page,
      size: templateQuery.size,
    })
  } finally {
    templateLoading.value = false
  }
}

function applyTemplateSearch() {
  templateQuery.page = 1
  void loadTemplates()
}

function resetTemplateSearch() {
  templateQuery.keyword = ''
  templateQuery.templateCategory = ''
  templateQuery.page = 1
  void loadTemplates()
}

async function handleTemplatePageChange(nextPage: number) {
  templateQuery.page = nextPage
  await loadTemplates()
}

function openTemplateDialog(template?: CodegenTemplateView) {
  if (template) {
    editingTemplate.value = { ...template }
  } else {
    editingTemplate.value = {
      name: '',
      language: 'java',
      templateCategory: 'backend',
      pathPattern: '',
      content: '',
      description: '',
      builtin: false,
    }
  }
  templateDialogVisible.value = true
}

async function saveTemplate() {
  if (!editingTemplate.value) {
    return
  }
  const payload: CodegenTemplateView = {
    name: editingTemplate.value.name.trim(),
    language: editingTemplate.value.language,
    pathPattern: editingTemplate.value.pathPattern.trim(),
    content: editingTemplate.value.content,
    description: editingTemplate.value.description?.trim() || undefined,
  }
  if (!payload.name || !payload.pathPattern || !payload.content) {
    ElMessage.warning('名称、路径匹配、模板内容均为必填')
    return
  }
  templateSaving.value = true
  try {
    if (editingTemplate.value.id) {
      await updateCodegenTemplate(editingTemplate.value.id, payload)
      ElMessage.success('模板已更新')
    } else {
      await createCodegenTemplate(payload)
      ElMessage.success('模板已创建')
    }
    templateDialogVisible.value = false
    await loadTemplates()
  } finally {
    templateSaving.value = false
  }
}

async function removeTemplate(template: CodegenTemplateView) {
  if (!template.id) {
    return
  }
  await ElMessageBox.confirm(`确认删除模板 “${template.name}”？`, '删除确认', { type: 'warning' })
  await deleteCodegenTemplate(template.id)
  ElMessage.success('模板已删除')
  await loadTemplates()
}
</script>

<style scoped lang="scss">
.codegen-page {
  position: relative;
}

.datasource-select {
  width: 210px;
}

.datasource-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;

  small {
    color: var(--text-soft);
  }
}

.datasource-auth-alert {
  margin: 0 0 16px;
}

.column-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.column-config-hint {
  margin: 8px 0 0;
  color: var(--text-soft);
  font-size: 12px;
}

.imported-panel {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.03), rgba(20, 184, 166, 0.06));
}

.imported-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.imported-item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;

  small {
    color: var(--text-soft);
  }

  &:hover,
  &--active {
    border-color: var(--primary);
    transform: translateY(-1px);
  }
}

.config-drawer-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.column-config-table {
  :deep(.el-input),
  :deep(.el-select) {
    width: 100%;
  }
}

.codegen-tabs {
  display: block;
}

.codegen-stat--primary {
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.14), rgba(20, 184, 166, 0.1)),
    var(--bg-card);
}

.codegen-console {
  min-height: 620px;
}

.muted-line {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.codegen-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.8fr);
  gap: 18px;
}

.codegen-table-card,
.codegen-config-card {
  min-width: 0;
}

.table-name-cell {
  display: grid;
  gap: 4px;

  small {
    color: var(--text-soft);
    font-size: 12px;
  }
}

.compact-footer {
  padding-top: 14px;
}

.scope-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 4px 0 14px;
}

.safety-alert {
  margin-bottom: 16px;
}

.column-panel {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--bg-card-muted);
}

.column-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.column-pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  color: var(--text-soft);
  font-size: 12px;

  small {
    margin-left: 6px;
    color: var(--text-muted);
  }
}

.column-pill--system {
  background: var(--bg-card-muted);
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-scope-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 8px 14px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  background: var(--bg-card-muted);
  margin-bottom: 12px;
}

.file-scope-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.file-label-text {
  font-size: 12px;
  color: var(--text-soft);
}

.preview-tabs {
  :deep(.el-tabs__content) {
    padding: 0;
  }
}

.preview-path {
  padding: 12px 14px;
  border-bottom: 1px solid var(--line);
  color: var(--text-soft);
  font-size: 12px;
}

pre {
  margin: 0;
  padding: 16px;
  max-height: 520px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.6;
}

.generated-panel {
  display: block;
}

.generated-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.generated-file {
  cursor: pointer;
}

.generated-resources {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.generated-resource {
  font-family: var(--font-mono);
  font-size: 12px;
}

.template-panel {
  min-height: 480px;
}

.footer-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0 0;
  gap: 12px;
}

@media (max-width: 1080px) {
  .codegen-layout {
    grid-template-columns: 1fr;
  }
}
</style>