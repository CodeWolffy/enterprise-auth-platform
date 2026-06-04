<template>
  <div class="panel-stack file-page">
    <section class="dashboard-grid">
      <article class="stat-card file-stat file-stat--primary">
        <span class="eyebrow">Files</span>
        <strong>{{ page.total }}</strong>
        <span>当前可见文件</span>
      </article>
      <article class="stat-card file-stat">
        <span class="eyebrow">Storage</span>
        <strong>{{ formatBytes(totalVisibleBytes) }}</strong>
        <span>当前页存储量</span>
      </article>
      <article class="stat-card file-stat">
        <span class="eyebrow">Public</span>
        <strong>{{ publicCount }}</strong>
        <span>当前页公开文件</span>
      </article>
      <article class="stat-card file-stat">
        <span class="eyebrow">MinIO</span>
        <strong>{{ minioCount }}</strong>
        <span>当前页 MinIO 对象</span>
      </article>
    </section>

    <section class="dashboard-panel file-console">
      <div class="panel-head">
        <div>
          <span class="eyebrow">对象存储</span>
          <h3>文件管理</h3>
          <p class="muted-line">文件元数据统一走平台权限，文件内容由 MinIO/本地路由服务承载。</p>
        </div>
        <div class="panel-actions">
          <el-upload
            v-permission="'file:write'"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleUploadSelected"
          >
            <el-button type="primary" :loading="uploading">上传文件</el-button>
          </el-upload>
        </div>
      </div>

      <AdvancedSearch @search="applySearch" @reset="resetSearch">
        <el-form-item label="文件名">
          <el-input v-model="query.keyword" placeholder="按原始文件名搜索" clearable />
        </el-form-item>
        <el-form-item label="内容类型">
          <el-select v-model="query.contentType" placeholder="全部" clearable style="width: 180px">
            <el-option label="全部" value="" />
            <el-option label="PNG 图片" value="image/png" />
            <el-option label="JPEG 图片" value="image/jpeg" />
            <el-option label="PDF 文档" value="application/pdf" />
          </el-select>
        </el-form-item>
        <el-form-item label="存储类型">
          <el-select v-model="query.storageType" placeholder="全部" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="MinIO" value="MINIO" />
            <el-option label="本地" value="LOCAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="query.visibility" placeholder="全部" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="公开" value="PUBLIC" />
            <el-option label="租户" value="TENANT" />
            <el-option label="归属人" value="OWNER" />
            <el-option label="私有" value="PRIVATE" />
          </el-select>
        </el-form-item>
        <el-form-item v-permission="'file:write'" label="上传可见性">
          <el-select v-model="uploadVisibility" style="width: 160px">
            <el-option label="归属人" value="OWNER" />
            <el-option label="租户" value="TENANT" />
            <el-option label="公开" value="PUBLIC" />
          </el-select>
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="tablePrefs.density" size="small">
          <el-radio-button value="compact">紧凑</el-radio-button>
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="comfortable">宽松</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="loadFiles">刷新</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="page.records"
        stripe
        :class="`table-density-${tablePrefs.density}`"
        @header-dragend="onHeaderDragEnd"
      >
        <el-table-column column-key="originalName" prop="originalName" label="文件名" min-width="220" :width="tablePrefs.getColumnWidth('originalName')" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="file-name-cell">
              <span class="file-badge">{{ fileBadge(row.contentType) }}</span>
              <div>
                <strong>{{ row.originalName }}</strong>
                <small>{{ row.fileKey }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column column-key="contentType" prop="contentType" label="类型" width="160" :min-width="140" />
        <el-table-column column-key="size" label="大小" width="120" :min-width="110">
          <template #default="{ row }">{{ formatBytes(row.size) }}</template>
        </el-table-column>
        <el-table-column column-key="storageType" label="存储" width="110" :min-width="100">
          <template #default="{ row }">
            <el-tag effect="plain" :type="row.storageType === 'MINIO' ? 'success' : 'info'">{{ row.storageType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column column-key="visibility" label="可见性" width="110" :min-width="100">
          <template #default="{ row }">
            <el-tag :type="visibilityTag(row.visibility)" effect="plain">{{ visibilityText(row.visibility) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column column-key="tenantId" prop="tenantId" label="租户" width="130" :min-width="120" />
        <el-table-column column-key="createdAt" label="创建时间" min-width="180" :width="tablePrefs.getColumnWidth('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="220" :min-width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadFile(row)">下载</el-button>
            <el-button v-if="row.url" link type="primary" @click="copyUrl(row.url)">复制链接</el-button>
            <el-button v-permission="'file:write'" link type="danger" @click="removeFile(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无文件" />
        </template>
      </el-table>

      <div class="footer-bar">
        <span>共 {{ page.total }} 个文件</span>
        <el-pagination
          background
          layout="sizes, prev, pager, next"
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="[20, 50, 100]"
          :total="page.total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { UploadFile, TagProps } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import { deleteStorageFile, downloadStorageFile, queryFiles, uploadStorageFile } from '@/api/modules'
import type { FileMetadataView, FilePage, FileVisibility } from '@/types/file'
import { formatDateTime } from '@/utils/datetime'
import { useTablePreferences } from '@/composables/useTablePreferences'

const loading = ref(false)
const uploading = ref(false)
const uploadVisibility = ref<FileVisibility>('OWNER')
const query = reactive({
  keyword: '',
  contentType: '',
  storageType: '',
  visibility: '',
  page: 1,
  size: 20,
})
const page = ref<FilePage>({ total: 0, page: 1, size: 20, records: [] })
const tablePrefs = useTablePreferences('eap.table.files', [
  { key: 'originalName', label: '文件名', width: 260 },
  { key: 'createdAt', label: '创建时间', width: 180 },
])

const totalVisibleBytes = computed(() => page.value.records.reduce((total, item) => total + (item.size || 0), 0))
const publicCount = computed(() => page.value.records.filter((item) => item.visibility === 'PUBLIC').length)
const minioCount = computed(() => page.value.records.filter((item) => item.storageType === 'MINIO').length)

void loadFiles()

async function loadFiles() {
  loading.value = true
  try {
    page.value = await queryFiles({
      keyword: query.keyword || undefined,
      contentType: query.contentType || undefined,
      storageType: query.storageType || undefined,
      visibility: query.visibility || undefined,
      page: query.page,
      size: query.size,
    })
  } finally {
    loading.value = false
  }
}

function applySearch() {
  query.page = 1
  void loadFiles()
}

function resetSearch() {
  query.keyword = ''
  query.contentType = ''
  query.storageType = ''
  query.visibility = ''
  query.page = 1
  void loadFiles()
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage
  await loadFiles()
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize
  query.page = 1
  await loadFiles()
}

async function handleUploadSelected(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) {
    return
  }
  uploading.value = true
  try {
    await uploadStorageFile(raw, uploadVisibility.value)
    ElMessage.success('文件已上传')
    query.page = 1
    await loadFiles()
  } finally {
    uploading.value = false
  }
}

async function downloadFile(file: FileMetadataView) {
  const blob = await downloadStorageFile(file.fileKey)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = file.originalName || file.fileKey
  anchor.click()
  URL.revokeObjectURL(url)
}

async function removeFile(file: FileMetadataView) {
  await ElMessageBox.confirm(`确认删除文件「${file.originalName}」？`, '删除确认', { type: 'warning' })
  await deleteStorageFile(file.fileKey)
  ElMessage.success('文件已删除')
  await loadFiles()
}

async function copyUrl(url: string) {
  await navigator.clipboard.writeText(url)
  ElMessage.success('公开链接已复制')
}

function onHeaderDragEnd(newWidth: number, _oldWidth: number, column: { columnKey?: string | null }) {
  if (column.columnKey) {
    tablePrefs.setColumnWidth(column.columnKey, newWidth)
  }
}

function fileBadge(contentType: string) {
  if (contentType.includes('image')) {
    return 'IMG'
  }
  if (contentType.includes('pdf')) {
    return 'PDF'
  }
  return 'FILE'
}

function visibilityText(visibility: string) {
  return ({ PUBLIC: '公开', TENANT: '租户', OWNER: '归属人', PRIVATE: '私有' } as Record<string, string>)[visibility] ?? visibility
}

function visibilityTag(visibility: string): TagProps['type'] {
  if (visibility === 'PUBLIC') {
    return 'success'
  }
  if (visibility === 'PRIVATE') {
    return 'danger'
  }
  if (visibility === 'TENANT') {
    return 'warning'
  }
  return 'info'
}

function formatBytes(value?: number | null) {
  const bytes = value ?? 0
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  }
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}
</script>

<style scoped lang="scss">
.file-page {
  position: relative;
}

.file-stat--primary {
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.14), rgba(20, 184, 166, 0.1)),
    var(--bg-card);
}

.muted-line {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.file-console {
  min-height: 560px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;

  strong,
  small {
    display: block;
  }

  small {
    margin-top: 3px;
    color: var(--text-soft);
    font-size: 12px;
  }
}

.file-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #1677ff, #14b8a6);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
</style>