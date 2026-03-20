<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Records</span>
        <strong>{{ page.total }}</strong>
        <span>当前条件下的审计记录总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Events</span>
        <strong>{{ eventTypeCount }}</strong>
        <span>当前页覆盖的事件类型数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Operators</span>
        <strong>{{ operatorCount }}</strong>
        <span>当前页涉及的操作人数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Requests</span>
        <strong>{{ requestCount }}</strong>
        <span>当前页涉及的请求数</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Audit</span>
          <h3>安全审计</h3>
        </div>
        <div class="panel-actions">
          <el-button @click="exportCurrentPage">导出当前页</el-button>
          <el-button type="primary" plain @click="createAsyncExport">异步导出</el-button>
        </div>
      </div>

      <el-form inline class="toolbar-inline" @submit.prevent="doSearch">
        <el-form-item label="租户">
          <el-input v-model="query.tenantId" placeholder="按租户编码搜索" clearable />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-input v-model="query.eventType" placeholder="例如 USER_UPDATED" clearable />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operator" placeholder="按操作人搜索" clearable />
        </el-form-item>
        <el-form-item label="请求 ID">
          <el-input v-model="query.requestId" placeholder="按请求 ID 搜索" clearable />
        </el-form-item>
        <el-form-item label="客户端 IP">
          <el-input v-model="query.clientIp" placeholder="按客户端 IP 搜索" clearable />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="page.records" stripe>
        <el-table-column prop="type" label="事件类型" min-width="160" />
        <el-table-column prop="operator" label="操作人" min-width="120" />
        <el-table-column prop="tenantId" label="租户" min-width="120" />
        <el-table-column prop="requestId" label="请求 ID" min-width="180" />
        <el-table-column prop="clientIp" label="客户端 IP" min-width="140" />
        <el-table-column prop="occurredAt" label="发生时间" min-width="180" />
        <el-table-column label="事件明细" min-width="220">
          <template #default="{ row }">
            <el-popover placement="left" :width="420" trigger="click">
              <template #reference>
                <el-button link type="primary">查看明细</el-button>
              </template>
              <pre class="json-pre">{{ JSON.stringify(row.details, null, 2) }}</pre>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>

      <div class="footer-bar">
        <span>共 {{ page.total }} 条记录</span>
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

    <section class="dashboard-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow">Export Tasks</span>
            <h3>导出任务历史</h3>
            <p class="muted-line">当前保留策略：完成后保留 {{ exportPolicy.retentionDays }} 天，单租户最多保留 {{ exportPolicy.maxTasks }} 条任务。</p>
          </div>
          <div class="panel-actions">
            <el-button @click="cleanupExportTasks">清理历史</el-button>
        </div>
      </div>

      <el-form inline class="toolbar-inline" @submit.prevent="loadExportTasks">
        <el-form-item label="租户">
          <el-input v-model="exportQuery.tenantId" placeholder="按租户编码搜索" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="exportQuery.status" placeholder="全部" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="执行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="发起人">
          <el-input v-model="exportQuery.operator" placeholder="按发起人搜索" clearable />
        </el-form-item>
        <el-form-item label="完成时间上限">
          <el-date-picker
            v-model="cleanupCompletedBefore"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="用于批量清理"
            clearable
          />
        </el-form-item>
        <el-form-item label="保留天数">
          <el-input-number v-model="exportPolicy.retentionDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="最多保留任务数">
          <el-input-number v-model="exportPolicy.maxTasks" :min="1" :max="5000" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyExportSearch">筛选</el-button>
          <el-button @click="resetExportSearch">重置</el-button>
          <el-button type="success" plain @click="saveRetentionPolicy">保存保留策略</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="exportTasks.records" stripe>
        <el-table-column prop="id" label="任务 ID" width="100" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="进度" min-width="220">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="row.progressPercent" :status="row.status === 'FAILED' ? 'exception' : undefined" />
              <span class="progress-text">{{ row.progressStage }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="发起人" min-width="120" />
        <el-table-column prop="recordCount" label="记录数" width="100" />
        <el-table-column label="保留期" min-width="180">
          <template #default="{ row }">
            <span>{{ row.expiresAt ? `保留至 ${row.expiresAt.replace('T', ' ')}` : '执行中不计保留期' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="requestedAt" label="发起时间" min-width="180" />
        <el-table-column prop="completedAt" label="完成时间" min-width="180" />
        <el-table-column prop="errorMessage" label="失败原因" min-width="180" />
        <el-table-column fixed="right" label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'SUCCESS'" @click="downloadTask(row.id)">下载</el-button>
            <el-button link type="danger" @click="removeTask(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="footer-bar">
        <span>共 {{ exportTasks.total }} 条导出任务</span>
        <el-pagination
          background
          layout="sizes, prev, pager, next"
          :current-page="exportQuery.page"
          :page-size="exportQuery.size"
          :page-sizes="[10, 20, 50]"
          :total="exportTasks.total"
          @current-change="handleExportPageChange"
          @size-change="handleExportSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cleanupAuditExportTasks,
  createAuditExportTask,
  deleteAuditExportTask,
  downloadAuditExportTask,
  exportAuditEvents,
  queryAuditEvents,
  queryAuditExportPolicy,
  queryAuditExportTasks,
  updateAuditExportPolicy,
} from '@/api/platform'
import type { AuditExportPolicy, AuditExportTask, AuditPage } from '@/types/auth'

const query = reactive({
  tenantId: '',
  eventType: '',
  operator: '',
  requestId: '',
  clientIp: '',
  page: 1,
  size: 20,
})

const dateRange = ref<[string, string] | null>(null)

const page = ref<AuditPage>({
  total: 0,
  page: 1,
  size: 20,
  records: [],
})
const exportTasks = ref<{ total: number; page: number; size: number; records: AuditExportTask[] }>({
  total: 0,
  page: 1,
  size: 10,
  records: [],
})
const exportQuery = reactive({
  tenantId: '',
  status: '',
  operator: '',
  page: 1,
  size: 10,
})
const cleanupCompletedBefore = ref<string | null>(null)
const exportPolicy = reactive<AuditExportPolicy>({
  retentionDays: 7,
  maxTasks: 100,
})

const eventTypeCount = computed(() => new Set(page.value.records.map((item) => item.type)).size)
const operatorCount = computed(() => new Set(page.value.records.map((item) => item.operator)).size)
const requestCount = computed(() => new Set(page.value.records.map((item) => item.requestId).filter(Boolean)).size)

void load()

function doSearch() {
  query.page = 1
  void load()
}

function resetSearch() {
  query.tenantId = ''
  query.eventType = ''
  query.operator = ''
  query.requestId = ''
  query.clientIp = ''
  dateRange.value = null
  query.page = 1
  void load()
}

async function load() {
  page.value = await queryAuditEvents({
    ...currentQueryParams(),
    page: query.page,
    size: query.size,
  })
  Object.assign(exportPolicy, await queryAuditExportPolicy())
  await loadExportTasks()
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage
  await load()
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize
  query.page = 1
  await load()
}

async function handleExportPageChange(nextPage: number) {
  exportQuery.page = nextPage
  await loadExportTasks()
}

async function handleExportSizeChange(nextSize: number) {
  exportQuery.size = nextSize
  exportQuery.page = 1
  await loadExportTasks()
}

async function exportCurrentPage() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('导出前请先选择开始和结束时间，且时间范围不能超过 31 天')
    return
  }
  const blob = await exportAuditEvents(currentQueryParams())
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `audit-export-${Date.now()}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出审计记录')
}

async function createAsyncExport() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('异步导出前请先选择开始和结束时间，且时间范围不能超过 31 天')
    return
  }
  await createAuditExportTask(currentQueryParams())
  ElMessage.success('导出任务已创建，请在下方历史列表查看状态')
  exportQuery.page = 1
  await loadExportTasks()
}

async function downloadTask(taskId: number) {
  const blob = await downloadAuditExportTask(taskId)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `audit-export-task-${taskId}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function loadExportTasks() {
  exportTasks.value = await queryAuditExportTasks({
    tenantId: exportQuery.tenantId || undefined,
    status: exportQuery.status || undefined,
    operator: exportQuery.operator || undefined,
    page: exportQuery.page,
    size: exportQuery.size,
  })
}

async function applyExportSearch() {
  exportQuery.page = 1
  await loadExportTasks()
}

async function resetExportSearch() {
  exportQuery.tenantId = ''
  exportQuery.status = ''
  exportQuery.operator = ''
  exportQuery.page = 1
  cleanupCompletedBefore.value = null
  await loadExportTasks()
}

async function removeTask(taskId: number) {
  await ElMessageBox.confirm('删除导出任务后将无法再次下载该文件，是否继续？', '删除确认', { type: 'warning' })
  await deleteAuditExportTask(taskId)
  ElMessage.success('导出任务已删除')
  await loadExportTasks()
}

async function cleanupExportTasks() {
  if (!cleanupCompletedBefore.value) {
    ElMessage.warning('请先选择完成时间上限，再执行批量清理')
    return
  }
  await ElMessageBox.confirm('批量清理会删除满足条件的导出任务历史，是否继续？', '清理确认', { type: 'warning' })
  const affected = await cleanupAuditExportTasks({
    tenantId: exportQuery.tenantId || undefined,
    status: exportQuery.status || undefined,
    completedBefore: cleanupCompletedBefore.value,
  })
  ElMessage.success(`已清理 ${affected} 条导出任务`)
  exportQuery.page = 1
  await loadExportTasks()
}

async function saveRetentionPolicy() {
  Object.assign(exportPolicy, await updateAuditExportPolicy(exportPolicy))
  ElMessage.success('导出保留策略已更新')
}

function currentQueryParams() {
  return {
    tenantId: query.tenantId || undefined,
    eventType: query.eventType || undefined,
    operator: query.operator || undefined,
    requestId: query.requestId || undefined,
    clientIp: query.clientIp || undefined,
    occurredFrom: dateRange.value?.[0] || undefined,
    occurredTo: dateRange.value?.[1] || undefined,
  }
}
</script>

<style scoped lang="scss">
.panel-actions {
  display: flex;
  gap: 12px;
}

.footer-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.json-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.progress-cell {
  display: grid;
  gap: 6px;
}

.progress-text {
  font-size: 12px;
  color: #64748b;
}

.muted-line {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}
</style>
