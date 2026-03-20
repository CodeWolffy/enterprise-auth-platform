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
        </div>
      </div>

      <el-table :data="exportTasks.records" stripe>
        <el-table-column prop="id" label="任务 ID" width="100" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="operator" label="发起人" min-width="120" />
        <el-table-column prop="recordCount" label="记录数" width="100" />
        <el-table-column prop="requestedAt" label="发起时间" min-width="180" />
        <el-table-column prop="completedAt" label="完成时间" min-width="180" />
        <el-table-column prop="errorMessage" label="失败原因" min-width="180" />
        <el-table-column fixed="right" label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'SUCCESS'" @click="downloadTask(row.id)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAuditExportTask, downloadAuditExportTask, exportAuditEvents, queryAuditEvents, queryAuditExportTasks } from '@/api/platform'
import type { AuditExportTask, AuditPage } from '@/types/auth'

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
  exportTasks.value = await queryAuditExportTasks({
    tenantId: query.tenantId || undefined,
    page: 1,
    size: 10,
  })
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
  exportTasks.value = await queryAuditExportTasks({
    tenantId: query.tenantId || undefined,
    page: 1,
    size: 10,
  })
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
</style>
