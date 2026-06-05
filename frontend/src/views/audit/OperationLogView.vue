<template>
  <div class="panel-stack operation-log-page">
    <section class="dashboard-grid">
      <article class="stat-card op-card--primary">
        <span class="eyebrow">记录</span>
        <strong>{{ page.total }}</strong>
        <span>当前筛选条件下的操作日志总数</span>
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
          <span class="eyebrow">Operation Log</span>
          <h3>操作日志</h3>
          <p class="muted-line">正式入口使用 operation-log 权限，CSV 导出要求时间范围并做表格公式防护。</p>
        </div>
        <div class="panel-actions">
          <el-button v-permission="'operation-log:export'" type="primary" plain @click="exportCurrentQuery">导出 CSV</el-button>
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
            clearable
          />
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="tablePrefs.density" size="small">
          <el-radio-button value="compact">紧凑</el-radio-button>
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="comfortable">宽松</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="page.records"
        stripe
        :class="`table-density-${tablePrefs.density}`"
        @header-dragend="onHeaderDragEnd"
      >
        <el-table-column column-key="type" prop="type" label="事件类型" min-width="180" :width="tablePrefs.getColumnWidth('type')" />
        <el-table-column column-key="operator" prop="operator" label="操作人" min-width="120" :width="tablePrefs.getColumnWidth('operator')" />
        <el-table-column column-key="tenantId" prop="tenantId" label="租户" min-width="120" :width="tablePrefs.getColumnWidth('tenantId')" />
        <el-table-column column-key="requestId" prop="requestId" label="请求 ID" min-width="180" :width="tablePrefs.getColumnWidth('requestId')" />
        <el-table-column column-key="clientIp" prop="clientIp" label="客户端 IP" min-width="140" :width="tablePrefs.getColumnWidth('clientIp')" />
        <el-table-column column-key="occurredAt" label="发生时间" min-width="180" :width="tablePrefs.getColumnWidth('occurredAt')">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column column-key="details" label="事件明细" min-width="220" :width="tablePrefs.getColumnWidth('details')">
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
          <el-empty description="暂无操作日志" />
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
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import { exportOperationLogs, queryOperationLogs } from '@/api/modules'
import type { AuditPage } from '@/types/audit'
import { formatDateTime } from '@/utils/datetime'
import { useTablePreferences } from '@/composables/useTablePreferences'

const MAX_EXPORT_RANGE_MS = 31 * 24 * 60 * 60 * 1000
const loading = ref(false)
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
const page = ref<AuditPage>({ total: 0, page: 1, size: 20, records: [] })
const tablePrefs = useTablePreferences('eap.table.operation-logs', [
  { key: 'type', label: '事件类型', width: 180 },
  { key: 'operator', label: '操作人', width: 120 },
  { key: 'tenantId', label: '租户', width: 120 },
  { key: 'requestId', label: '请求ID', width: 180 },
  { key: 'clientIp', label: '客户端IP', width: 140 },
  { key: 'occurredAt', label: '发生时间', width: 180 },
  { key: 'details', label: '事件明细', width: 220 },
])

const eventTypeCount = computed(() => new Set(page.value.records.map((item) => item.type)).size)
const operatorCount = computed(() => new Set(page.value.records.map((item) => item.operator)).size)
const requestCount = computed(() => new Set(page.value.records.map((item) => item.requestId).filter(Boolean)).size)

void load()

async function load() {
  loading.value = true
  try {
    page.value = await queryOperationLogs({
      ...currentQueryParams(),
      page: query.page,
      size: query.size,
    })
  } finally {
    loading.value = false
  }
}

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

async function handlePageChange(nextPage: number) {
  query.page = nextPage
  await load()
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize
  query.page = 1
  await load()
}

async function exportCurrentQuery() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('导出前请先选择开始和结束时间，且时间范围不能超过 31 天。')
    return
  }
  if (dateRange.value[1].getTime() <= dateRange.value[0].getTime()) {
    ElMessage.warning('结束时间必须晚于开始时间。')
    return
  }
  if (dateRange.value[1].getTime() - dateRange.value[0].getTime() > MAX_EXPORT_RANGE_MS) {
    ElMessage.warning('导出时间范围不能超过 31 天。')
    return
  }
  const blob = await exportOperationLogs(currentQueryParams())
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `operation-logs-${Date.now()}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出操作日志')
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

function onHeaderDragEnd(newWidth: number, _oldWidth: number, column: { columnKey?: string | null }) {
  if (column.columnKey) {
    tablePrefs.setColumnWidth(column.columnKey, newWidth)
  }
}
</script>

<style scoped lang="scss">
.op-card--primary {
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.14), rgba(245, 158, 11, 0.1)),
    var(--bg-card);
}

.muted-line {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.json-pre {
  max-height: 420px;
  margin: 0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}
</style>