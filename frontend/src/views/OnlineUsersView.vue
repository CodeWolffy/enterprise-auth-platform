<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Sessions</span>
        <strong>{{ sessions.length }}</strong>
        <span>当前可见会话总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Online</span>
        <strong>{{ activeCount }}</strong>
        <span>仍处于在线状态的会话</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Tenants</span>
        <strong>{{ tenantCount }}</strong>
        <span>会话覆盖的租户数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Devices</span>
        <strong>{{ deviceCount }}</strong>
        <span>识别到的设备类型数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Online Users</span>
          <h3>在线用户</h3>
        </div>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>

      <div class="session-toolbar">
        <el-input v-model.trim="keyword" placeholder="搜索用户、租户、IP 或设备" clearable />
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button v-for="item in statusOptions" :key="item.value" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <el-result v-if="loadError" icon="error" title="加载失败" :sub-title="loadError" class="panel-result">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <el-table v-else v-loading="loading" :data="filteredSessions" stripe>
        <el-table-column prop="username" label="用户" min-width="130" />
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
            <el-button link type="danger" :disabled="!row.active" @click="kickSession(row.sessionId)">
              强制下线
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无在线用户数据" />
        </template>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { forceOffline, querySessions } from '@/api/auth'
import type { UserSessionView } from '@/types/auth'
import { formatDateTime } from '@/utils/datetime'

const loading = ref(false)
const loadError = ref('')
const sessions = ref<UserSessionView[]>([])
const keyword = ref('')
const statusFilter = ref<'all' | 'active' | 'inactive'>('active')
const statusOptions = [
  { label: '在线', value: 'active' },
  { label: '全部', value: 'all' },
  { label: '已下线', value: 'inactive' },
]

const activeCount = computed(() => sessions.value.filter((item) => item.active).length)
const tenantCount = computed(() => new Set(sessions.value.map((item) => item.tenantId).filter(Boolean)).size)
const deviceCount = computed(() => new Set(sessions.value.map((item) => formatDevice(item.device))).size)
const filteredSessions = computed(() => {
  const search = keyword.value.toLowerCase()
  return sessions.value.filter((item) => {
    if (statusFilter.value === 'active' && !item.active) {
      return false
    }
    if (statusFilter.value === 'inactive' && item.active) {
      return false
    }
    if (!search) {
      return true
    }
    return [
      item.username,
      item.tenantId,
      item.clientIp,
      item.device,
      formatDevice(item.device),
    ].some((value) => String(value ?? '').toLowerCase().includes(search))
  })
})

void load()

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    sessions.value = await querySessions()
  } catch {
    sessions.value = []
    loadError.value = '在线用户数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
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

@media (max-width: 760px) {
  .session-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
