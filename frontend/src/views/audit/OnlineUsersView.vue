<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">会话</span>
        <strong>{{ totalSessions }}</strong>
        <span>索引中在线会话总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">在线</span>
        <strong>{{ activeCount }}</strong>
        <span>当前页仍处于在线状态的会话</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">租户</span>
        <strong>{{ tenantCount }}</strong>
        <span>当前页会话覆盖的租户数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">设备</span>
        <strong>{{ deviceCount }}</strong>
        <span>当前页识别到的设备类型数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">在线用户</span>
          <h3>在线用户</h3>
        </div>
        <el-button type="primary" :loading="loading" data-testid="online-users-refresh" @click="load">刷新</el-button>
      </div>

      <div class="session-toolbar">
        <el-input v-model.trim="keyword" placeholder="搜索用户、租户、IP 或设备" clearable @clear="load" @keyup.enter="doSearch" />
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button v-for="item in statusOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <el-result v-if="loadError" icon="error" title="加载失败" :sub-title="loadError" class="panel-result">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <el-table v-else v-loading="loading" :data="pageRecords" stripe data-testid="online-users-table">
        <el-table-column prop="username" label="用户" min-width="130" />
        <el-table-column label="标记" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.currentSession" type="success" effect="dark" size="small">当前会话</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tenantId" label="租户" min-width="130" />
        <el-table-column prop="clientIp" label="登录 IP" min-width="130" />
        <el-table-column label="设备" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatDevice(row.device) }}
          </template>
        </el-table-column>
        <el-table-column label="登录时间" width="180">
          <template #default="{ row }">
            {{ formatSessionTime(row.issuedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="最后访问" width="180">
          <template #default="{ row }">
            {{ formatSessionTime(row.lastAccessAt) }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="180">
          <template #default="{ row }">
            {{ formatSessionTime(row.expiresAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" effect="plain">
              {{ row.active ? '在线' : '已下线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'session:write'" link type="danger" data-testid="online-users-force-offline" :disabled="!row.active || row.currentSession" @click="kickSession(row.sessionId)">强制下线</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无在线用户数据" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="totalSessions"
          @size-change="onPageSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { forceOffline, querySessions } from '@/api/auth'
import type { SessionPageResult, UserSessionView } from '@/types/auth'
import { formatDateTime } from '@/utils/datetime'

const loading = ref(false)
const loadError = ref('')
const pageData = ref<SessionPageResult>({ total: 0, page: 1, size: 10, records: [] })
const keyword = ref('')
const statusFilter = ref<'all' | 'active' | 'inactive'>('active')
const statusOptions = [
  { label: '在线', value: 'active' },
  { label: '全部', value: 'all' },
  { label: '已下线', value: 'inactive' },
]

const queryParams = reactive({ page: 1, size: 10 })

const totalSessions = computed(() => pageData.value.total)
const pageRecords = computed(() => {
  const search = keyword.value.toLowerCase()
  return pageData.value.records.filter((item) => {
    if (statusFilter.value === 'active' && !item.active) return false
    if (statusFilter.value === 'inactive' && item.active) return false
    if (!search) return true
    return [
      item.username,
      item.tenantId,
      item.clientIp,
      item.device,
      formatDevice(item.device),
    ].some((value) => String(value ?? '').toLowerCase().includes(search))
  })
})
const activeCount = computed(() => pageData.value.records.filter((item) => item.active).length)
const tenantCount = computed(() => new Set(pageData.value.records.map((item) => item.tenantId).filter(Boolean)).size)
const deviceCount = computed(() => new Set(pageData.value.records.map((item) => formatDevice(item.device))).size)

void load()

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await querySessions('all', { page: queryParams.page, size: queryParams.size }) as SessionPageResult
    pageData.value = result
  } catch {
    pageData.value = { total: 0, page: 1, size: queryParams.size, records: [] }
    loadError.value = '在线用户数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function doSearch() {
  queryParams.page = 1
  void load()
}

function onPageSizeChange() {
  queryParams.page = 1
  void load()
}

function onPageChange() {
  void load()
}

async function kickSession(sessionId: string) {
  try {
    await ElMessageBox.confirm('强制下线后该会话将立即失去访问权限，是否继续？', '下线确认', { type: 'warning' })
    await forceOffline(sessionId)
    ElMessage.success('会话已下线')
    await load()
  } catch {
    // Cancelled by user or handled by the HTTP interceptor.
  }
}

function formatSessionTime(epochMs?: number | null) {
  return formatDateTime(epochMs)
}

function formatDevice(raw?: string | null) {
  if (!raw) return 'Unknown'
  const ua = raw.toLowerCase()
  if (ua.includes('edg/')) return 'Microsoft Edge'
  if (ua.includes('chrome/')) return 'Google Chrome'
  if (ua.includes('firefox/')) return 'Mozilla Firefox'
  if (ua.includes('safari/') && !ua.includes('chrome/')) return 'Safari'
  if (ua.includes('java-http-client')) return 'Browser Session'
  return raw
}
</script>

<style scoped lang="scss">
.session-toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 360px) auto;
  gap: 12px;
  align-items: center;
  margin: -4px 0 12px;
}

.panel-result {
  margin: 12px 0 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 760px) {
  .session-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
