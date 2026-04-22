<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="admin-aside">
      <div class="logo-box">
        <span class="logo-icon">EA</span>
        <transition name="logo-fade">
          <h1 v-show="!isCollapse" class="logo-text">权限中台</h1>
        </transition>
      </div>
      <el-scrollbar class="aside-scrollbar">
        <AppNav :collapse="isCollapse" />
      </el-scrollbar>
    </el-aside>

    <el-container class="admin-container">
      <el-header class="admin-header" height="50px">
        <div class="header-left">
          <el-icon class="action-icon" @click="isCollapse = !isCollapse">
            <component :is="isCollapse ? Expand : Fold" />
          </el-icon>
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
              style="width: 140px; margin: 0 8px;"
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
            <el-icon class="action-icon" title="在线设备" @click="openSessions"><Monitor /></el-icon>
            
            <el-color-picker
              v-model="themeColor"
              :predefine="['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#6366f1', '#10b981', '#f43f5e']"
              size="small"
              @change="handleThemeChange"
            />
          </div>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="avatar-container">
              <el-avatar :size="28" class="user-avatar">{{ avatarName }}</el-avatar>
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
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
        <el-tag
          v-for="tag in visitedViews"
          :key="tag.path"
          :closable="tag.path !== '/dashboard'"
          :type="route.path === tag.path ? 'primary' : 'info'"
          :effect="route.path === tag.path ? 'dark' : 'plain'"
          class="tags-view-item"
          @click="router.push(tag.path)"
          @close="closeView(tag)"
        >
          {{ tag.title }}
        </el-tag>
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
import { Expand, Fold, Monitor, ArrowDown } from '@element-plus/icons-vue'
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

// tags view simplified
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

// tenant loading
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
    //
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
  background-color: var(--bg-shell, #f3f4f6);
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
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  overflow: hidden;
  border-bottom: 1px solid var(--line, #e5e7eb);
}

.logo-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 8px;
  background: var(--accent, #409eff);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 14px;
}

.logo-text {
  margin: 0 0 0 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main, #303133);
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
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
}

.header-left, .header-right {
  display: flex;
  align-items: center;
}

.action-icon {
  font-size: 20px;
  cursor: pointer;
  margin-right: 16px;
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

.tenant-selector {
  display: flex;
  align-items: center;
  font-size: 14px;
  color: #606266;
}

.avatar-container {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 8px;
}

.user-avatar {
  background: var(--accent, #409eff);
  margin-right: 4px;
}

.tags-view-container {
  height: 34px;
  width: 100%;
  background: #fff;
  border-bottom: 1px solid var(--line, #d8dce5);
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, .12), 0 0 3px 0 rgba(0, 0, 0, .04);
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
  cursor: pointer;
  border-radius: 2px;
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
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
}

/* Transitions */
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
