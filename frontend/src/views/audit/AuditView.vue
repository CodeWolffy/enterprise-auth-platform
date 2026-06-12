<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">记录</span>
        <strong>{{ page.total }}</strong>
        <span>当前筛选条件下的审计记录总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">事件</span>
        <strong>{{ eventTypeCount }}</strong>
        <span>当前页覆盖的事件类型数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">操作人</span>
        <strong>{{ operatorCount }}</strong>
        <span>当前页涉及的操作人数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">请求</span>
        <strong>{{ requestCount }}</strong>
        <span>当前页涉及的请求数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">审计</span>
          <h3>安全审计</h3>
        </div>
        <div class="panel-actions">
          <el-button v-permission="'upms:audit:export'" data-testid="audit-export-current" @click="exportCurrentPage">导出当前查询</el-button>
          <el-button v-permission="'upms:audit:export'" type="primary" plain data-testid="audit-export-async" @click="createAsyncExport">异步导出</el-button>
        </div>
      </div>

      <AdvancedSearch @search="doSearch" @reset="resetSearch">
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
            data-testid="audit-date-range"
            clearable
          />
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="auditTablePrefs.density" size="small">
          <el-radio-button value="compact">紧凑</el-radio-button>
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="comfortable">宽松</el-radio-button>
        </el-radio-group>
        <el-popover placement="bottom-end" width="260" trigger="click">
          <template #reference>
            <el-button size="small">列显示</el-button>
          </template>
          <div class="column-chooser">
            <el-checkbox
              v-for="item in auditTablePrefs.columns"
              :key="item.key"
              :model-value="auditTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => auditTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="auditTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-table
        :data="page.records"
        stripe
        :class="`table-density-${auditTablePrefs.density}`"
        @header-dragend="onAuditHeaderDragEnd"
      >
        <el-table-column v-if="auditTablePrefs.visibleColumnMap.type" column-key="type" prop="type" label="事件类型" min-width="180" :width="auditTablePrefs.getColumnWidth('type')" />
        <el-table-column v-if="auditTablePrefs.visibleColumnMap.operator" column-key="operator" prop="operator" label="操作人" min-width="120" :width="auditTablePrefs.getColumnWidth('operator')" />
        <el-table-column v-if="auditTablePrefs.visibleColumnMap.tenantId" column-key="tenantId" prop="tenantId" label="租户" min-width="120" :width="auditTablePrefs.getColumnWidth('tenantId')" />
        <el-table-column v-if="auditTablePrefs.visibleColumnMap.requestId" column-key="requestId" prop="requestId" label="请求 ID" min-width="180" :width="auditTablePrefs.getColumnWidth('requestId')" />
        <el-table-column v-if="auditTablePrefs.visibleColumnMap.clientIp" column-key="clientIp" prop="clientIp" label="客户端 IP" min-width="140" :width="auditTablePrefs.getColumnWidth('clientIp')" />
        <el-table-column v-if="auditTablePrefs.visibleColumnMap.occurredAt" column-key="occurredAt" label="发生时间" min-width="180" :width="auditTablePrefs.getColumnWidth('occurredAt')">
          <template #default="{ row }">
            {{ formatDateTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column v-if="auditTablePrefs.visibleColumnMap.details" column-key="details" label="事件明细" min-width="220" :width="auditTablePrefs.getColumnWidth('details')">
          <template #default="{ row }">
            <el-popover placement="left" :width="420" trigger="click">
              <template #reference>
                <el-button link type="primary">查看明细</el-button>
              </template>
              <pre class="json-pre">{{ JSON.stringify(row.details, null, 2) }}</pre>
            </el-popover>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无审计记录" />
        </template>
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

    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">任务</span>
        <strong>{{ exportTasks.total }}</strong>
        <span>当前筛选条件下的导出任务总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">成功</span>
        <strong>{{ exportSuccessCount }}</strong>
        <span>当前页导出成功任务</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">已归档</span>
        <strong>{{ exportArchivedCount }}</strong>
        <span>当前页已归档任务</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">即将到期</span>
        <strong>{{ exportExpiringSoonCount }}</strong>
        <span>当前页 24 小时内到期任务</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">导出任务</span>
          <h3>导出任务历史</h3>
          <p class="muted-line">
            当前保留策略：完成后保留 {{ exportPolicy.retentionDays }} 天，单租户最多保留 {{ exportPolicy.maxTasks }} 条任务。
          </p>
        </div>
        <div class="panel-actions">
          <el-button v-permission="'upms:audit:edit'" @click="archiveExportTasksByFilter">批量归档</el-button>
          <el-button v-permission="'upms:audit:del'" @click="cleanupExportTasks">批量清理</el-button>
        </div>
      </div>

      <AdvancedSearch @search="applyExportSearch" @reset="resetExportSearch">
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
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="发起人">
          <el-input v-model="exportQuery.operator" placeholder="按发起人搜索" clearable />
        </el-form-item>
        <el-form-item label="完成时间上限">
          <el-date-picker
            v-model="cleanupCompletedBefore"
            type="datetime"
            placeholder="用于归档或清理"
            clearable
          />
        </el-form-item>
        <el-form-item label="保留天数">
          <el-input-number v-model="exportPolicy.retentionDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="最大任务数">
          <el-input-number v-model="exportPolicy.maxTasks" :min="1" :max="5000" />
        </el-form-item>
        <template #extra-actions>
          <el-button v-permission="'upms:audit:edit'" type="success" plain @click="saveRetentionPolicy">保存保留策略</el-button>
        </template>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="exportTablePrefs.density" size="small">
          <el-radio-button value="compact">紧凑</el-radio-button>
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="comfortable">宽松</el-radio-button>
        </el-radio-group>
        <el-popover placement="bottom-end" width="260" trigger="click">
          <template #reference>
            <el-button size="small">列显示</el-button>
          </template>
          <div class="column-chooser">
            <el-checkbox
              v-for="item in exportTablePrefs.columns"
              :key="item.key"
              :model-value="exportTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => exportTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="exportTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-table
        :data="exportTasks.records"
        stripe
        :class="`table-density-${exportTablePrefs.density}`"
        @header-dragend="onExportHeaderDragEnd"
      >
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.id" column-key="id" prop="id" label="任务 ID" width="100" :min-width="100" :resizable="true" />
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.status" column-key="status" prop="status" label="状态" width="100" :min-width="100" :resizable="true" />
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.progress" column-key="progress" label="进度" min-width="220" :width="exportTablePrefs.getColumnWidth('progress')">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="row.progressPercent" :status="row.status === 'FAILED' ? 'exception' : undefined" />
              <span class="progress-text">{{ row.progressStage }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.operator" column-key="operator" prop="operator" label="发起人" min-width="120" :width="exportTablePrefs.getColumnWidth('operator')" />
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.recordCount" column-key="recordCount" prop="recordCount" label="记录数" width="100" :min-width="100" :resizable="true" />
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.archiveResult" column-key="archiveResult" label="归档结果" min-width="210" :width="exportTablePrefs.getColumnWidth('archiveResult')">
          <template #default="{ row }">
            <el-tag :type="row.archived ? 'info' : row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" effect="plain">
              {{ archiveResultSummary(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.expiryHint" column-key="expiryHint" label="到期提示" min-width="240" :width="exportTablePrefs.getColumnWidth('expiryHint')">
          <template #default="{ row }">
            <div class="retention-cell">
              <span>{{ expiryHint(row) }}</span>
              <div class="retention-tags">
                <el-tag v-if="row.retentionExpired" type="danger" effect="plain">已过保留期</el-tag>
                <el-tag v-else-if="row.archivable" type="warning" effect="plain">建议归档</el-tag>
                <el-tag v-if="row.archived" type="info" effect="plain">已归档</el-tag>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.requestedAt" column-key="requestedAt" label="发起时间" min-width="180" :width="exportTablePrefs.getColumnWidth('requestedAt')">
          <template #default="{ row }">
            {{ formatDateTime(row.requestedAt) }}
          </template>
        </el-table-column>
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.completedAt" column-key="completedAt" label="完成时间" min-width="180" :width="exportTablePrefs.getColumnWidth('completedAt')">
          <template #default="{ row }">
            {{ formatDateTime(row.completedAt) }}
          </template>
        </el-table-column>
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.errorMessage" column-key="errorMessage" prop="errorMessage" label="失败原因" min-width="180" show-overflow-tooltip :width="exportTablePrefs.getColumnWidth('errorMessage')" />
        <el-table-column v-if="exportTablePrefs.visibleColumnMap.actions" column-key="actions" fixed="right" label="操作" width="340" :min-width="240" :resizable="true">
          <template #default="{ row }">
            <el-button link type="primary" data-testid="audit-task-detail" @click="openTaskDetail(row)">详情</el-button>
            <el-button v-permission="'upms:audit:download'" link type="primary" :disabled="row.status !== 'SUCCESS'" @click="downloadTask(row.id)">下载</el-button>
            <el-button v-permission="'upms:audit:edit'" link type="warning" data-testid="audit-task-archive" :disabled="!row.archivable" @click="archiveTask(row.id)">归档</el-button>
            <el-button v-permission="'upms:audit:edit'" link type="warning" :disabled="row.status !== 'FAILED'" @click="retryTask(row.id)">重试</el-button>
            <el-button v-permission="'upms:audit:del'" link type="danger" data-testid="audit-task-delete" @click="removeTask(row.id)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无导出任务" />
        </template>
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

    <el-drawer v-model="taskDetailVisible" title="导出任务详情" size="640px">
      <template v-if="detailTask">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务 ID">{{ detailTask.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailTask.status }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detailTask.operator }}</el-descriptions-item>
          <el-descriptions-item label="记录数">{{ detailTask.recordCount }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ detailTask.progressPercent }}% / {{ detailTask.progressStage }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ formatDateTime(detailTask.requestedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(detailTask.completedAt) }}</el-descriptions-item>
          <el-descriptions-item label="到期时间">{{ formatDateTime(detailTask.expiresAt) }}</el-descriptions-item>
          <el-descriptions-item label="失败原因">{{ detailTask.errorMessage || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-block drawer-section drawer-section--overview">
          <div class="eyebrow">归档结果说明</div>
          <el-alert :title="archiveDetailSummary(detailTask)" :type="detailTask.archived ? 'info' : detailTask.status === 'FAILED' ? 'error' : 'success'" :closable="false" style="margin-top: 8px" />
        </div>

        <div class="detail-block drawer-section drawer-section--guide">
          <div class="eyebrow">到期处理提示</div>
          <el-alert :title="expiryHint(detailTask)" :type="detailTask.retentionExpired ? 'error' : detailTask.archivable ? 'warning' : 'info'" :closable="false" style="margin-top: 8px" />
          <p class="muted-line" style="margin-top: 10px">
            建议：成功任务在保留期结束前归档，过期任务按策略统一清理，失败任务优先重试或重新发起导出。
          </p>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import { useTablePreferences } from '@/composables/useTablePreferences'
import { formatDateTime } from '@/utils/datetime'
import {
  archiveAuditExportTask,
  archiveAuditExportTasks,
  cleanupAuditExportTasks,
  createAuditExportTask,
  deleteAuditExportTask,
  downloadAuditExportTask,
  exportAuditEvents,
  queryAuditEvents,
  queryAuditExportPolicy,
  queryAuditExportTasks,
  retryAuditExportTask,
  updateAuditExportPolicy,
} from '@/api/modules'
import type { AuditExportPolicy, AuditExportTask, AuditPage } from '@/types/audit'

const query = reactive({
  tenantId: '',
  eventType: '',
  operator: '',
  requestId: '',
  clientIp: '',
  page: 1,
  size: 20,
})

const dateRange = ref<[Date, Date] | null>(null)

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

const cleanupCompletedBefore = ref<Date | null>(null)
const taskDetailVisible = ref(false)
const detailTask = ref<AuditExportTask | null>(null)

const exportPolicy = reactive<AuditExportPolicy>({
  retentionDays: 7,
  maxTasks: 100,
})

const eventTypeCount = computed(() => new Set(page.value.records.map((item) => item.type)).size)
const operatorCount = computed(() => new Set(page.value.records.map((item) => item.operator)).size)
const requestCount = computed(() => new Set(page.value.records.map((item) => item.requestId).filter(Boolean)).size)
const exportSuccessCount = computed(() => exportTasks.value.records.filter((item) => item.status === 'SUCCESS').length)
const exportArchivedCount = computed(() => exportTasks.value.records.filter((item) => item.archived).length)
const exportExpiringSoonCount = computed(() =>
  exportTasks.value.records.filter((item) => !item.archived && item.expiresAt && daysUntil(item.expiresAt) !== null && (daysUntil(item.expiresAt) as number) <= 1).length,
)

const auditTablePrefs = useTablePreferences('eap.table.audit.events', [
  { key: 'type', label: '事件类型', width: 180 },
  { key: 'operator', label: '操作人', width: 120 },
  { key: 'tenantId', label: '租户', width: 120 },
  { key: 'requestId', label: '请求ID', width: 180 },
  { key: 'clientIp', label: '客户端IP', width: 140 },
  { key: 'occurredAt', label: '发生时间', width: 180 },
  { key: 'details', label: '事件明细', width: 220 },
])

const exportTablePrefs = useTablePreferences('eap.table.audit.exports', [
  { key: 'id', label: '任务ID', width: 100 },
  { key: 'status', label: '状态', width: 100 },
  { key: 'progress', label: '进度', width: 220 },
  { key: 'operator', label: '发起人', width: 120 },
  { key: 'recordCount', label: '记录数', width: 100 },
  { key: 'archiveResult', label: '归档结果', width: 210 },
  { key: 'expiryHint', label: '到期提示', width: 240 },
  { key: 'requestedAt', label: '发起时间', width: 180 },
  { key: 'completedAt', label: '完成时间', width: 180 },
  { key: 'errorMessage', label: '失败原因', width: 180 },
  { key: 'actions', label: '操作', width: 340 },
])

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
    ElMessage.warning('导出前请先选择开始和结束时间，且时间范围不能超过 31 天。')
    return
  }
  const blob = await exportAuditEvents(currentQueryParams())
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `audit-export-${Date.now()}.xlsx`
  anchor.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出审计记录')
}

async function createAsyncExport() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('异步导出前请先选择开始和结束时间，且时间范围不能超过 31 天。')
    return
  }
  await createAuditExportTask(currentQueryParams())
  ElMessage.success('导出任务已创建，请在下方历史列表查看状态。')
  exportQuery.page = 1
  await loadExportTasks()
}

async function downloadTask(taskId: number) {
  const blob = await downloadAuditExportTask(taskId)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `audit-export-task-${taskId}.xlsx`
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

async function retryTask(taskId: number) {
  await retryAuditExportTask(taskId)
  ElMessage.success('已重新创建导出任务')
  exportQuery.page = 1
  await loadExportTasks()
}

async function archiveTask(taskId: number) {
  await archiveAuditExportTask(taskId)
  ElMessage.success('导出任务已归档，文件内容已转为仅保留元数据')
  await loadExportTasks()
}

async function archiveExportTasksByFilter() {
  if (!cleanupCompletedBefore.value) {
    ElMessage.warning('请先选择完成时间上限，再执行批量归档。')
    return
  }
  await ElMessageBox.confirm('批量归档会移除导出文件内容，仅保留任务元数据与审计轨迹，是否继续？', '归档确认', {
    type: 'warning',
  })
  const affected = await archiveAuditExportTasks({
    tenantId: exportQuery.tenantId || undefined,
    status: exportQuery.status || undefined,
    completedBeforeEpochMs: cleanupCompletedBefore.value.getTime(),
  })
  ElMessage.success(`已归档 ${affected} 条导出任务`)
  exportQuery.page = 1
  await loadExportTasks()
}

async function cleanupExportTasks() {
  if (!cleanupCompletedBefore.value) {
    ElMessage.warning('请先选择完成时间上限，再执行批量清理。')
    return
  }
  await ElMessageBox.confirm('批量清理会删除满足条件的导出任务历史，是否继续？', '清理确认', { type: 'warning' })
  const affected = await cleanupAuditExportTasks({
    tenantId: exportQuery.tenantId || undefined,
    status: exportQuery.status || undefined,
    completedBeforeEpochMs: cleanupCompletedBefore.value.getTime(),
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
    fromEpochMs: dateRange.value?.[0]?.getTime(),
    toEpochMs: dateRange.value?.[1]?.getTime(),
  }
}

function archiveResultSummary(task: AuditExportTask) {
  if (task.archived) {
    return '已归档（仅保留元数据）'
  }
  if (task.status === 'FAILED') {
    return '导出失败'
  }
  if (task.status === 'SUCCESS') {
    return '导出成功（可下载）'
  }
  if (task.status === 'RUNNING') {
    return '执行中'
  }
  return '待处理'
}

function archiveDetailSummary(task: AuditExportTask) {
  if (task.archived) {
    return '该任务已完成归档，下载文件内容已移除，仅保留任务元数据、审计轨迹和保留策略信息。'
  }
  if (task.status === 'SUCCESS') {
    return '该任务导出成功，当前仍可下载。建议在不再需要文件内容后归档，降低存储成本。'
  }
  if (task.status === 'FAILED') {
    return '该任务执行失败，未生成可下载文件。可按失败原因调整筛选条件后重试。'
  }
  return '任务尚未结束，归档结果需待任务完成后确认。'
}

function expiryHint(task: AuditExportTask) {
  if (task.archived) {
    return '任务已归档，不再受导出文件保留期影响。'
  }
  if (task.retentionExpired) {
    return '已超过保留期，建议尽快执行归档或清理。'
  }
  if (task.expiresAt) {
    const days = daysUntil(task.expiresAt)
    if (days === null) {
      return `预计到期时间：${formatTime(task.expiresAt)}`
    }
    if (days <= 1) {
      return `将在 24 小时内到期（${formatTime(task.expiresAt)}），请尽快处理。`
    }
    return `距离到期约 ${days} 天（${formatTime(task.expiresAt)}）。`
  }
  if (task.status === 'FAILED') {
    return '失败任务通常无可下载文件，可按治理策略定期清理。'
  }
  return task.retentionSummary || '任务完成后将按当前保留策略自动进入到期窗口。'
}

function daysUntil(epochMs: number) {
  if (!Number.isFinite(epochMs)) {
    return null
  }
  const diff = epochMs - Date.now()
  return Math.ceil(diff / (24 * 60 * 60 * 1000))
}

function formatTime(epochMs: number) {
  return formatDateTime(epochMs)
}

function openTaskDetail(task: AuditExportTask) {
  detailTask.value = task
  taskDetailVisible.value = true
}

function onAuditHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  auditTablePrefs.setColumnWidth(key, newWidth)
}

function onExportHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  exportTablePrefs.setColumnWidth(key, newWidth)
}
</script>
