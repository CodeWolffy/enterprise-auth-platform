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
        <span class="eyebrow">操作用户</span>
        <strong>{{ operatorCount }}</strong>
        <span>当前页涉及的操作用户数</span>
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
          <p class="muted-line">查询系统内关键操作记录，用于审计与排障。</p>
        </div>
        <div class="panel-actions">
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <AdvancedSearch @search="doSearch" @reset="resetSearch">
        <el-form-item label="租户">
          <el-input v-model="query.tenantId" placeholder="按租户编码搜索" clearable />
        </el-form-item>
        <el-form-item label="操作标题">
          <el-input v-model="query.eventType" placeholder="例如 退出登录" clearable />
        </el-form-item>
        <el-form-item label="操作用户">
          <el-input v-model="query.operator" placeholder="按操作用户搜索" clearable />
        </el-form-item>
        <el-form-item label="请求 ID">
          <el-input v-model="query.requestId" placeholder="按请求 ID 搜索" clearable />
        </el-form-item>
        <el-form-item label="操作地址">
          <el-input v-model="query.clientIp" placeholder="按操作地址搜索" clearable />
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
      </div>

      <el-table
        v-loading="loading"
        :data="page.records"
        stripe
        :class="`table-density-${tablePrefs.density}`"
        @header-dragend="onHeaderDragEnd"
      >
        <el-table-column column-key="operator" prop="operator" label="操作用户" min-width="120" :width="tablePrefs.getColumnWidth('operator')" />
        <el-table-column column-key="eventType" prop="eventType" label="操作标题" min-width="180" :width="tablePrefs.getColumnWidth('eventType')" />
        <el-table-column column-key="clientIp" prop="clientIp" label="操作地址" min-width="140" :width="tablePrefs.getColumnWidth('clientIp')" />
        <el-table-column column-key="location" prop="location" label="操作地点" min-width="140" :width="tablePrefs.getColumnWidth('location')">
          <template #default="{ row }">{{ locationLabel(row) }}</template>
        </el-table-column>
        <el-table-column column-key="method" prop="method" label="操作方法" min-width="100" :width="tablePrefs.getColumnWidth('method')" />
        <el-table-column column-key="status" label="操作状态" min-width="100" :width="tablePrefs.getColumnWidth('status')">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column column-key="requestTime" label="请求时长" min-width="100" :width="tablePrefs.getColumnWidth('requestTime')">
          <template #default="{ row }">{{ requestTimeLabel(row.requestTime) }}</template>
        </el-table-column>
        <el-table-column column-key="createdAt" label="创建时间" min-width="180" :width="tablePrefs.getColumnWidth('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column column-key="operation" label="操作" min-width="100" :width="tablePrefs.getColumnWidth('operation')" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="操作详情" width="720px" destroy-on-close>
      <pre v-if="detailRow" class="json-pre">{{ detailJson(detailRow) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import { queryOperationLogs } from '@/api/modules/log'
import type { LogPage, OperationLogRecord } from '@/types/log'
import { formatDateTime } from '@/utils/datetime'
import { useTablePreferences } from '@/composables/useTablePreferences'

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
const page = ref<LogPage<OperationLogRecord>>({ total: 0, page: 1, size: 20, records: [] })
const detailVisible = ref(false)
const detailRow = ref<OperationLogRecord | null>(null)
const tablePrefs = useTablePreferences('eap.table.operation-logs', [
  { key: 'operator', label: '操作用户', width: 120 },
  { key: 'eventType', label: '操作标题', width: 180 },
  { key: 'clientIp', label: '操作地址', width: 140 },
  { key: 'location', label: '操作地点', width: 140 },
  { key: 'method', label: '操作方法', width: 100 },
  { key: 'status', label: '操作状态', width: 100 },
  { key: 'requestTime', label: '请求时长', width: 100 },
  { key: 'createdAt', label: '创建时间', width: 180 },
  { key: 'operation', label: '操作', width: 100 },
])

const eventTypeCount = computed(() => new Set(page.value.records.map((item) => item.eventType)).size)
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

function statusLabel(status?: string) {
  if (status === '1' || status === 'SUCCESS') return '成功'
  if (status === '0' || status === 'FAILED') return '失败'
  return status || '-'
}

function statusTagType(status?: string) {
  if (status === '1' || status === 'SUCCESS') return 'success'
  if (status === '0' || status === 'FAILED') return 'danger'
  return 'info'
}

function locationLabel(row: OperationLogRecord) {
  if (row.location) return row.location
  if (row.clientIp && isInternalIp(row.clientIp)) return '内网IP'
  return '-'
}

function isInternalIp(ip: string) {
  return (
    ip === '127.0.0.1' ||
    ip === '0:0:0:0:0:0:0:1' ||
    ip === '::1' ||
    ip.startsWith('10.') ||
    ip.startsWith('192.168.') ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(ip)
  )
}

function requestTimeLabel(value?: number | null) {
  if (value == null) return '-'
  return `${value}ms`
}

function openDetail(row: OperationLogRecord) {
  detailRow.value = row
  detailVisible.value = true
}

function detailJson(row: OperationLogRecord) {
  try {
    return JSON.stringify(JSON.parse(row.payloadJson || '{}'), null, 2)
  } catch {
    return row.payloadJson || '{}'
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