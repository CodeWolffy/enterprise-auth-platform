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
              <el-button :loading="loading" @click="loadTables">刷新表</el-button>
              <el-button v-permission="'codegen:write'" type="primary" :disabled="!canGenerate" :loading="generating" @click="generateFiles">生成到隔离目录</el-button>
            </div>
          </div>

          <AdvancedSearch @search="applySearch" @reset="resetSearch">
            <el-form-item label="关键字">
              <el-input v-model="query.keyword" placeholder="表名或表注释" clearable />
            </el-form-item>
          </AdvancedSearch>

          <div class="codegen-layout">
            <div class="codegen-table-card">
              <el-table v-loading="loading" :data="tablePage.records" stripe highlight-current-row @current-change="selectTable">
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
                  <el-checkbox v-permission="'codegen:write'" v-model="form.overwrite">允许覆盖</el-checkbox>
                  <el-checkbox v-permission="'codegen:write'" v-model="form.autoRegister" @change="markPreviewStale">自动注册菜单权限</el-checkbox>
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
                <span class="eyebrow">Columns</span>
                <div class="column-list">
                  <span v-for="column in tableDetail.columns" :key="column.columnName" class="column-pill" :class="{ 'column-pill--system': isSystemColumn(column.columnName) }">
                    {{ column.columnName }} · {{ column.javaType }}
                    <small v-if="column.primaryKey">PK</small>
                    <small v-else-if="isSystemColumn(column.columnName)">系统字段</small>
                  </span>
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
              <p class="muted-line">输出根目录：{{ previewResult.generatedRoot }}</p>
            </div>
            <div class="preview-actions">
              <el-tag :type="previewStale ? 'warning' : 'success'" effect="plain">
                {{ previewStale ? '配置已变更，请重新预览' : '预览可生成' }}
              </el-tag>
              <el-button size="small" :disabled="!activePreviewPath" @click="copyText(activePreviewPath, '预览路径已复制')">复制当前路径</el-button>
              <el-button v-permission="'codegen:read'" size="small" type="primary" :disabled="!canDownload" :loading="downloading" @click="downloadZip">下载产物</el-button>
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
          </AdvancedSearch>
          <el-table v-loading="templateLoading" :data="templatePage.records" stripe>
            <el-table-column prop="name" label="名称" min-width="160" />
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
          <el-form label-position="top" :model="editingTemplate" v-if="editingTemplate">
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
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import {
  createCodegenTemplate,
  deleteCodegenTemplate,
  downloadCodegen,
  generateCodegen,
  getCodegenTable,
  previewCodegen,
  queryCodegenTables,
  queryCodegenTemplates,
  updateCodegenTemplate,
} from '@/api/modules'
import type {
  CodegenPreviewResult,
  CodegenRequest,
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
const safetyAlert = computed(() => {
  const segments = [form.overwrite ? '已启用覆盖：生成时会替换隔离目录中的同名文件。' : '安全模式：生成时遇到同名文件会停止，不会覆盖。']
  if (form.autoRegister) {
    segments.push('自动注册：完成后会在平台资源树新建菜单与 API 资源，并授予 ADMIN 角色。')
  }
  return segments.join(' ')
})

void loadTables()

async function loadTables() {
  loading.value = true
  try {
    tablePage.value = await queryCodegenTables({
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
    })
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
const templateQuery = reactive({ keyword: '', page: 1, size: 20 })
const editingTemplate = ref<CodegenTemplateView | null>(null)

async function loadTemplates() {
  templateLoading.value = true
  try {
    templatePage.value = await queryCodegenTemplates({
      keyword: templateQuery.keyword || undefined,
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