<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapse ? '64px' : '200px'" class="admin-aside">
      <div class="logo-box">
        <el-icon class="logo-icon"><Box /></el-icon>
        <transition name="logo-fade">
          <h1 v-show="!isCollapse" class="logo-text">系统租户</h1>
        </transition>
      </div>
      <el-scrollbar class="aside-scrollbar">
        <AppNav :collapse="isCollapse" />
      </el-scrollbar>
    </el-aside>

    <el-container class="admin-container">
      <el-header class="admin-header" height="48px">
        <div class="header-left">
          <el-icon class="action-icon" @click="isCollapse = !isCollapse">
            <component :is="isCollapse ? Expand : Fold" />
          </el-icon>
          <el-icon class="action-icon"><Refresh /></el-icon>
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle && pageTitle !== '运行总览'">{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <div class="tenant-selector">
            <span class="tenant-label">当前租户:</span>
            <el-select
              v-if="canLoadTenants"
              :model-value="authStore.tenantId"
              placeholder="切换租户"
              size="small"
              filterable
              class="borderless-select"
              @change="handleTenantChange"
            >
              <el-option
                v-for="tenant in tenantOptions"
                :key="tenant.tenantId"
                :label="`${tenant.name} (${tenant.tenantId})`"
                :value="tenant.tenantId"
              />
            </el-select>
            <span v-else class="tenant-value">{{ authStore.tenantId }}</span>
          </div>

          <div class="header-actions">
            <div class="search-input">
              <el-icon class="search-icon"><Search /></el-icon>
              <span class="search-shortcut">Ctrl K</span>
            </div>
            <el-icon class="action-icon-small"><Setting /></el-icon>
            <el-icon class="action-icon-small"><Moon /></el-icon>
            <el-icon class="action-icon-small"><FullScreen /></el-icon>
          </div>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="avatar-container">
              <el-avatar :size="28" class="user-avatar">{{ avatarName }}</el-avatar>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <div class="tags-view-container">
        <span
          v-for="tag in visitedViews"
          :key="tag.path"
          class="tags-view-item"
          :class="{ active: route.path === tag.path }"
          @click="router.push(tag.path)"
        >
          {{ tag.title }}
          <el-icon v-if="tag.path !== '/dashboard'" class="tag-close" @click.stop="closeView(tag)"><Close /></el-icon>
        </span>
      </div>

      <el-main class="admin-main">
        <transition name="fade-transform" mode="out-in">
          <div class="main-card">
            <RouterView />
          </div>
        </transition>
      </el-main>
    </el-container>

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
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Expand, Fold, Monitor, ArrowDown, Box, Search, Setting, Moon, FullScreen, Refresh, Close } from '@element-plus/icons-vue'
import AppNav from '@/components/AppNav.vue'
import { useAuthStore } from '@/stores/auth'
import { querySessions, forceOffline } from '@/api/auth'
import { queryTenants } from '@/api/platform'
import { useTheme } from '@/composables/useTheme'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { setBrandColor, currentBrandColor } = useTheme()

const isCollapse = ref(false)
const themeColor = ref(currentBrandColor.value || '#409eff')
const sessionsVisible = ref(false)
const sessionsList = ref<any[]>([])

const pageTitle = computed(() => String(route.meta.title ?? '控制台'))
const avatarName = computed(() => {
  const name = authStore.snapshot?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const visitedViews = ref<{ path: string; title: string }[]>([])

watch(() => route.path, () => {
  addVisitedView()
}, { immediate: true })

function addVisitedView() {
  if (!route.path || route.name === 'login') return
  const exist = visitedViews.value.find(v => v.path === route.path)
  if (!exist) {
    visitedViews.value.push({
      path: route.path,
      title: pageTitle.value
    })
  }
}

function closeView(tag: any) {
  const index = visitedViews.value.indexOf(tag)
  visitedViews.value.splice(index, 1)
  if (route.path === tag.path) {
    const nextTag = visitedViews.value[index - 1] || visitedViews.value[index]
    if (nextTag) {
      router.push(nextTag.path)
    } else {
      router.push('/dashboard')
    }
  }
}

const tenantOptions = ref<Array<{ tenantId: string; name: string }>>([])
const canLoadTenants = computed(() => {
  return authStore.canSwitchTenant && Boolean(authStore.snapshot?.grants.includes('tenant:read'))
})

onMounted(() => {
  if (canLoadTenants.value) {
    loadTenantOptions()
  }
})

async function loadTenantOptions() {
  try {
    const page = await queryTenants({ page: 1, size: 200 }, { silentAuthFailure: true, suppressErrorMessage: true })
    tenantOptions.value = page.list
  } catch {
  }
}

async function handleTenantChange(newTenantId: string) {
  if (newTenantId === authStore.tenantId) return
  authStore.switchTenant(newTenantId)
  ElMessage.success(`已切换到租户 ${newTenantId}`)
  setTimeout(() => window.location.reload(), 300)
}

function handleCommand(command: string) {
  if (command === 'logout') {
    handleLogout()
  }
}

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
  }
}

function handleThemeChange(color: string | null) {
  setBrandColor(color)
}

function formatSessionTime(epochMs?: number | null) {
  return formatDateTime(epochMs)
}

function formatDevice(raw?: string) {
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
.admin-layout {
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-shell, #f0f2f5);
}

.admin-aside {
  background-color: #fff;
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line, #e5e7eb);
  box-shadow: 2px 0 8px 0 rgba(29,35,41,.05);
  z-index: 10;
}

.logo-box {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  overflow: hidden;
  background: #fff;
}

.logo-icon {
  font-size: 24px;
  color: #303133;
}

.logo-text {
  margin: 0 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.aside-scrollbar {
  flex: 1;
}

.admin-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}

.header-left, .header-right {
  display: flex;
  align-items: center;
}

.action-icon {
  font-size: 18px;
  cursor: pointer;
  margin-right: 16px;
  color: #5a5e66;
  &:hover {
    color: var(--accent, #409eff);
  }
}

.action-icon-small {
  font-size: 16px;
  cursor: pointer;
  color: #5a5e66;
  &:hover {
    color: var(--accent, #409eff);
  }
}

.header-actions {
  display: flex;
  align-items: center;
  margin: 0 16px;
  gap: 16px;
}

.search-input {
  display: flex;
  align-items: center;
  background: #f4f4f5;
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 12px;
  color: #909399;
  cursor: text;

  .search-icon {
    margin-right: 6px;
  }
  .search-shortcut {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    padding: 0 4px;
    background: #fff;
    margin-left: 8px;
    transform: scale(0.9);
  }
}

.tenant-selector {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #606266;
  margin-right: 12px;
}
.tenant-selector :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 4px;
  width: 100px;
}

.avatar-container {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 8px;
}

.user-avatar {
  background: var(--accent, #409eff);
  font-size: 14px;
  font-weight: 500;
}

.tags-view-container {
  height: 36px;
  width: 100%;
  background: #fff;
  border-bottom: 1px solid var(--line, #e5e7eb);
  padding: 4px 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  &::-webkit-scrollbar {
    display: none;
  }
}

.tags-view-item {
  display: inline-flex;
  align-items: center;
  height: 26px;
  line-height: 26px;
  padding: 0 10px;
  font-size: 12px;
  color: #606266;
  background: transparent;
  border: 1px solid #dcdfe6;
  border-radius: 2px;
  cursor: pointer;
  transition: all 0.2s;

  .tag-close {
    font-size: 12px;
    margin-left: 4px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    &:hover {
      background-color: #c0c4cc;
      color: #fff;
    }
  }

  &.active {
    background: #e6f2ff;
    color: #409eff;
    border-color: #e6f2ff;
  }
}

.admin-main {
  background: var(--bg-shell, #f0f2f5);
  padding: 16px;
}

.main-card {
  background: #fff;
  min-height: calc(100vh - 120px);
  border-radius: 4px;
  padding: 20px;
}

.logo-fade-enter-active,
.logo-fade-leave-active {
  transition: opacity 0.2s;
}
.logo-fade-enter-from,
.logo-fade-leave-to {
  opacity: 0;
}

.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all .3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>