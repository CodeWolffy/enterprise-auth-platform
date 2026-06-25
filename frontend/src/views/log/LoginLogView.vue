<template>
  <div class="panel-stack login-log-page">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">记录</span>
        <strong>{{ page.total }}</strong>
        <span>当前筛选条件下的登录日志总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">成功</span>
        <strong>{{ successCount }}</strong>
        <span>当前页登录成功次数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">失败</span>
        <strong>{{ failedCount }}</strong>
        <span>当前页登录失败次数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">用户</span>
        <strong>{{ userCount }}</strong>
        <span>当前页涉及的用户数</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Login Log</span>
          <h3>登录日志</h3>
          <p class="muted-line">查询系统登录记录，用于安全审计与异常登录排查。</p>
        </div>
        <div class="panel-actions">
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <AdvancedSearch @search="doSearch" @reset="resetSearch">
        <el-form-item label="租户">
          <el-input v-model="query.tenantId" placeholder="按租户编码搜索" clearable />
        </el-form-item>
        <el-form-item label="登录用户">
          <el-input v-model="query.userName" placeholder="按登录用户搜索" clearable />
        </el-form-item>
        <el-form-item label="操作状态">
          <el-select v-model="query.status" placeholder="全部" clearable>
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="锁定" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录地址">
          <el-input v-model="query.clientIp" placeholder="按登录地址搜索" clearable />
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
        <el-table-column column-key="userName" prop="userName" label="登录用户" min-width="120" :width="tablePrefs.getColumnWidth('userName')" />
        <el-table-column column-key="ipAddr" prop="ipAddr" label="登录地址" min-width="140" :width="tablePrefs.getColumnWidth('ipAddr')" />
        <el-table-column column-key="location" prop="location" label="登录地点" min-width="140" :width="tablePrefs.getColumnWidth('location')" />
        <el-table-column column-key="createdAt" label="登录时间" min-width="180" :width="tablePrefs.getColumnWidth('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column column-key="browser" prop="browser" label="浏览器" min-width="140" :width="tablePrefs.getColumnWidth('browser')" />
        <el-table-column column-key="os" prop="os" label="操作系统" min-width="160" :width="tablePrefs.getColumnWidth('os')" />
        <el-table-column column-key="status" label="操作状态" min-width="100" :width="tablePrefs.getColumnWidth('status')">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column column-key="msg" label="操作描述" min-width="200" :width="tablePrefs.getColumnWidth('msg')">
          <template #default="{ row }">{{ loginMsg(row) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无登录日志" />
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
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import { queryLoginLogs } from '@/api/modules/log'
import type { LogPage, LoginLogRecord } from '@/types/log'
import { formatDateTime } from '@/utils/datetime'
import { useTablePreferences } from '@/composables/useTablePreferences'

const loading = ref(false)
const query = reactive({
  tenantId: '',
  userName: '',
  status: '',
  clientIp: '',
  page: 1,
  size: 20,
})
const dateRange = ref<[Date, Date] | null>(null)
const page = ref<LogPage<LoginLogRecord>>({ total: 0, page: 1, size: 20, records: [] })
const tablePrefs = useTablePreferences('eap.table.login-logs', [
  { key: 'userName', label: '登录用户', width: 120 },
  { key: 'ipAddr', label: '登录地址', width: 140 },
  { key: 'location', label: '登录地点', width: 140 },
  { key: 'createdAt', label: '登录时间', width: 180 },
  { key: 'browser', label: '浏览器', width: 140 },
  { key: 'os', label: '操作系统', width: 160 },
  { key: 'status', label: '操作状态', width: 100 },
  { key: 'msg', label: '操作描述', width: 200 },
])

const successCount = computed(() => page.value.records.filter((item) => item.status === 'SUCCESS').length)
const failedCount = computed(() => page.value.records.filter((item) => item.status === 'FAILED' || item.status === 'LOCKED').length)
const userCount = computed(() => new Set(page.value.records.map((item) => item.userName)).size)

void load()

async function load() {
  loading.value = true
  try {
    page.value = await queryLoginLogs({
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
  query.userName = ''
  query.status = ''
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
    userName: query.userName || undefined,
    status: query.status || undefined,
    clientIp: query.clientIp || undefined,
    fromEpochMs: dateRange.value?.[0]?.getTime(),
    toEpochMs: dateRange.value?.[1]?.getTime(),
  }
}

function statusLabel(status: string) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  if (status === 'LOCKED') return '锁定'
  return status
}

function statusTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'LOCKED') return 'warning'
  return 'info'
}

function loginMsg(row: LoginLogRecord) {
  if (row.userName && row.msg) {
    return `用户：${row.userName} ${row.msg}`
  }
  return row.msg || '-'
}

function onHeaderDragEnd(newWidth: number, _oldWidth: number, column: { columnKey?: string | null }) {
  if (column.columnKey) {
    tablePrefs.setColumnWidth(column.columnKey, newWidth)
  }
}
</script>

<style scoped lang="scss">
.json-pre {
  max-height: 420px;
  margin: 0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}
</style>