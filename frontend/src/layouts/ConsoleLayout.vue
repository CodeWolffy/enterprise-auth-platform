<template>
  <div class="console-shell">
    <AppNav />
    <main class="console-main">
      <header class="console-header">
        <div class="console-header__title">
          <span class="eyebrow">Session Cookie + Redis + RBAC</span>
          <h2>{{ pageTitle }}</h2>
        </div>
        <div class="console-header__actions">
          <el-color-picker
            v-model="themeColor"
            show-alpha
            :predefine="['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#6366f1', '#10b981', '#f43f5e']"
            size="small"
            style="margin-right: 12px"
            @change="handleThemeChange"
          />
          <div class="identity">
            <strong>{{ authStore.snapshot?.username }}</strong>
            <span>{{ authStore.snapshot?.roles?.join(' / ') || 'No Roles' }}</span>
          </div>
          <el-button @click="openSessions">在线设备</el-button>
          <el-button type="primary" plain data-testid="logout-button" @click="handleLogout">退出当前会话</el-button>
        </div>
      </header>

      <section class="console-content" :class="isDashboard ? 'console-content--dashboard' : 'console-content--management'">
        <RouterView />
      </section>
    </main>

    <el-dialog v-model="sessionsVisible" title="在线设备管理" width="760px">
      <el-alert
        title="可查看近期登录设备与会话状态，并可将不再使用的设备强制下线。"
        type="info"
        show-icon
        style="margin-bottom: 16px"
        :closable="false"
      />
      <el-table :data="sessionsList" stripe>
        <el-table-column label="设备标识" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatDevice(row.device) }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="登录 IP" width="130" />
        <el-table-column label="首次登录时间" width="190">
          <template #default="{ row }">
            {{ formatSessionTime(row.issuedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" effect="plain">{{ row.active ? '在线' : '已下线' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="!row.active" @click="kickSession(row.sessionId)">强制下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppNav from '@/components/AppNav.vue'
import { useAuthStore } from '@/stores/auth'
import { querySessions, forceOffline } from '@/api/auth'
import { useTheme } from '@/composables/useTheme'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { setBrandColor, currentBrandColor } = useTheme()

const themeColor = ref(currentBrandColor.value || '#409eff')
const sessionsVisible = ref(false)
const sessionsList = ref<any[]>([])

const pageTitle = computed(() => String(route.meta.title ?? '控制台'))
const isDashboard = computed(() => route.name === 'dashboard')

async function handleLogout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}

async function openSessions() {
  sessionsVisible.value = true
  await loadSessions()
}

async function loadSessions() {
  sessionsList.value = await querySessions()
}

async function kickSession(sessionId: string) {
  try {
    await ElMessageBox.confirm('强制下线后该设备将立即失去访问权限，是否继续？', '下线确认', { type: 'warning' })
    await forceOffline(sessionId)
    ElMessage.success('设备已下线')
    await loadSessions()
  } catch {
    // cancelled
  }
}

function handleThemeChange(color: string | null) {
  setBrandColor(color)
}

function formatSessionTime(epochMs?: number | null) {
  return formatDateTime(epochMs)
}

function formatDevice(raw?: string) {
  if (!raw) {
    return 'Unknown'
  }
  const ua = raw.toLowerCase()
  if (ua.includes('edg/')) {
    return 'Microsoft Edge'
  }
  if (ua.includes('chrome/')) {
    return 'Google Chrome'
  }
  if (ua.includes('firefox/')) {
    return 'Mozilla Firefox'
  }
  if (ua.includes('safari/') && !ua.includes('chrome/')) {
    return 'Safari'
  }
  if (ua.includes('java-http-client')) {
    return 'Browser Session'
  }
  return raw
}
</script>
