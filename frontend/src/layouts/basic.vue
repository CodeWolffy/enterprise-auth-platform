<template>
  <div
    class="relative flex min-h-full w-full bg-background-deep text-foreground"
    :class="{ 'is-page-fullscreen': isPageFullscreen }"
  >
    <!-- 侧边栏 -->
    <Transition name="drawer">
      <div
        v-if="!isMobile || mobileSidebarOpen"
        class="layout-sidebar-drawer"
        :class="{ 'is-mobile': isMobile }"
      >
        <LayoutSidebar
          v-model:collapse="sidebarCollapsed"
          v-model:expand-on-hover="preferences.sidebarExpandOnHover"
          :show="sidebarEnable"
          :theme="sidebarTheme"
          :width="sidebarRenderWidth"
          :collapse-width="compactSidebarCollapseWidth"
          :z-index="isMobile ? 100 : 100"
          @hover-change="handleSidebarHoverChange"
          @leave="handleSidebarLeave"
        >
          <template v-if="preferences.layout === 'sidebar-mixed-nav'">
            <LayoutMixedMenu
              :menus="allNavLinks"
              :active-path="route.path"
              :theme="sidebarTheme"
              variant="rail"
              :collapse="sidebarCollapsed"
              @select="handleMenuSelect"
            />
          </template>
          <template v-else-if="preferences.layout === 'mixed-nav'">
            <LayoutMixedMenu
              :menus="allNavLinks"
              :active-path="route.path"
              :theme="sidebarTheme"
              variant="panel"
              @select="handleMenuSelect"
            />
          </template>
          <template v-else>
            <AppNav :collapse="sidebarCollapsed" />
          </template>
        </LayoutSidebar>
      </div>
    </Transition>

    <!-- 右侧内容区 -->
    <div
      class="layout-main-shell flex flex-1 flex-col overflow-hidden transition-all duration-300 ease-in"
      :style="contentShellStyle"
    >
      <!-- 头部 + 标签页容器 -->
      <div
        v-if="showHeader"
        class="flex flex-col transition-all duration-200"
        :class="{ 'fixed top-0 right-0 z-50': preferences.headerFixed && !isPageFullscreen }"
        :style="headerWrapperStyle"
      >
        <LayoutHeader
          :sidebar-collapsed="sidebarCollapsed"
          :header-height="compactHeaderHeight"
          :is-mobile="isMobile"
          :show="!isFullContent && !isPageFullscreen"
          :sidebar-width="effectiveSidebarWidth"
          :theme="sidebarTheme"
          :width="headerWidth"
          :z-index="headerZIndex"
          :menu-align="headerMenuAlign"
          @toggle-sidebar="toggleSidebar"
          @refresh="reloadPage"
        >
          <template v-if="isMobile" #logo>
            <div class="flex items-center gap-2">
              <span
                class="inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary"
              >
                企
              </span>
              <span class="truncate font-semibold text-sm">企业认证平台</span>
            </div>
          </template>

          <!-- 头部水平菜单（header-nav / mixed-nav 模式） -->
          <template v-if="showHeaderMenu" #header-menu>
            <AppNav
              :collapse="false"
              mode="horizontal"
              :rounded="preferences.rounded"
              @select="handleMenuSelect"
              @open="handleMenuOpen"
            />
          </template>

          <!-- 右侧工具栏 -->
          <template #header-right>
            <LayoutToolbar
              :tenant-options="toolbarTenantOptions"
              :current-tenant-id="authStore.tenantId"
              :operator-tenant-id="authStore.operatorTenantId"
              :tenant-loading="tenantSwitching"
              :can-switch-tenant="authStore.snapshot?.superAdmin === true || authStore.operatorTenantId === 'platform'"
              :user-avatar-url="authStore.snapshot?.avatarUrl"
              :user-name="authStore.snapshot?.username"
              :is-dark="isDark"
              @tenant-change="handleTenantChange"
              @open-sessions="openSessions"
              @open-preferences="preferencesVisible = true"
              @toggle-theme="toggleDark"
              @toggle-language="toggleLanguage"
              @toggle-fullscreen="toggleFullScreen"
              @open-search="handleOpenSearch"
              @user-command="handleUserCommand"
            >
              <template #notification>
                <NotificationBell v-if="!authStore.passwordChangeRequired" ref="notificationBellRef" />
              </template>
            </LayoutToolbar>
          </template>
        </LayoutHeader>

        <LayoutTabbar
          v-if="showTabbar"
          :tabbar-enable="preferences.tabbarEnable"
          :tabbar-show-icon="preferences.tabbarShowIcon"
          :tabbar-style-type="preferences.tabbarStyleType"
          :height="compactTabbarHeight"
          :is-fullscreen="isPageFullscreen"
          @toggle-fullscreen="togglePageFullscreen"
          @refresh="reloadPage"
        />
      </div>

      <!-- 主内容 -->
      <main
        v-loading="tenantSwitching"
        element-loading-text="租户切换中"
        class="layout-main-content flex-1 overflow-auto"
        :class="{ 'p-4': !isFullContent, 'p-0': isFullContent }"
        :style="mainStyle"
      >
        <RouterView v-slot="{ Component }">
          <transition :name="pageTransitionName" mode="out-in" appear>
            <div v-if="Component" :key="routerViewKey" class="main-card console-content--management">
              <component :is="Component" />
            </div>
          </transition>
        </RouterView>
      </main>
    </div>

    <!-- 移动端侧边栏遮罩 -->
    <Transition name="fade">
      <div
        v-if="isMobile && !sidebarCollapsed"
        class="fixed inset-0 bg-black/40 z-[90]"
        @click="sidebarCollapsed = true"
      />
    </Transition>

    <!-- 在线设备管理弹窗 -->
    <el-dialog v-model="sessionsVisible" title="在线设备管理" width="860px">
      <el-alert
        title="可查看近期登录设备与会话状态，并可将不再使用的设备强制下线。"
        type="info"
        show-icon
        style="margin-bottom: 16px"
        :closable="false"
      />
      <div class="session-dialog-toolbar">
        <span>共 {{ sessionsList.length }} 个在线会话</span>
        <el-button size="small" :loading="sessionsLoading" data-testid="session-dialog-refresh" @click="loadSessions">刷新</el-button>
      </div>
      <el-table v-loading="sessionsLoading" :data="sessionsList" stripe data-testid="session-dialog-table">
        <el-table-column label="标记" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.currentSession" type="success" effect="dark" size="small">当前</el-tag>
          </template>
        </el-table-column>
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
        <el-table-column label="最后访问时间" width="190">
          <template #default="{ row }">
            {{ formatSessionTime(row.lastAccessAt) }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="190">
          <template #default="{ row }">
            {{ formatSessionTime(row.expiresAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" effect="plain">{{ row.active ? '在线' : '已下线' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" data-testid="session-dialog-force-offline" :disabled="!row.active || row.currentSession" @click="kickSession(row.sessionId)">强制下线</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无在线设备" />
        </template>
      </el-table>
    </el-dialog>

    <!-- 偏好设置抽屉 -->
    <PreferencesDrawer v-model="preferencesVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppNav from '@/layouts/navigation/AppNav.vue'
import LayoutHeader from '@/layouts/components/LayoutHeader.vue'
import LayoutSidebar from '@/layouts/components/LayoutSidebar.vue'
import LayoutTabbar from '@/layouts/components/LayoutTabbar.vue'
import LayoutToolbar from '@/layouts/components/LayoutToolbar.vue'
import LayoutMixedMenu from '@/layouts/navigation/LayoutMixedMenu.vue'
import NotificationBell from '@/components/notification/NotificationBell.vue'
import PreferencesDrawer from '@/layouts/components/PreferencesDrawer.vue'
import { resolveAppIcon, resolveMenuPresentation } from '@/app/registry/module-manifest'
import { useAuthStore } from '@/stores/auth'
import { usePreferences } from '@/composables/usePreferences'
import { useTheme } from '@/composables/useTheme'
import { forceOffline, querySessions, queryTenants } from '@/api/modules'
import { isAllowedMenuPath, isAllowedRoute, resolveFirstAllowedPath } from '@/router/route-access'
import { formatDateTime } from '@/utils/datetime'
import type { MenuItem } from '@/types/auth-models'
import type { NavLink } from '@/layouts/navigation/menu-context'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { preferences } = usePreferences()
const { isDark, toggleDark, setDarkMode, initTheme } = useTheme()

const notificationBellRef = ref<InstanceType<typeof NotificationBell> | null>(null)
const sessionsVisible = ref(false)
const sessionsList = ref<any[]>([])
const sessionsLoading = ref(false)
const tenantSwitching = computed(() => authStore.tenantSwitching)
const routerViewRefreshKey = ref(0)
const preferencesVisible = ref(false)
const isMobile = ref(false)
const mobileSidebarOpen = ref(false)
const sidebarHoverExpanded = ref(false)

const sidebarCollapsed = computed({
  get: () => preferences.sidebarCollapsed || (isMobile.value && !mobileSidebarOpen.value),
  set: (value) => {
    preferences.sidebarCollapsed = value
    if (isMobile.value) {
      mobileSidebarOpen.value = !value
    }
  },
})

const sidebarTheme = computed(() => (isDark.value ? 'dark' : 'light'))

const sidebarEnable = computed(() => {
  return preferences.layout !== 'header-nav' && preferences.layout !== 'full-content'
})

const isHeaderNav = computed(() => preferences.layout === 'header-nav')
const isMixedNav = computed(() => preferences.layout === 'mixed-nav' || preferences.layout === 'header-mixed-nav' || preferences.layout === 'sidebar-mixed-nav')
const isFullContent = computed(() => preferences.layout === 'full-content')
const showHeader = computed(() => !isFullContent.value)
const showTabbar = computed(() => preferences.tabbarEnable && !isFullContent.value)
const showHeaderMenu = computed(() => {
  return preferences.layout === 'header-nav' || preferences.layout === 'mixed-nav' || preferences.layout === 'header-mixed-nav'
})
const allNavLinks = computed(() => buildNavLinks(authStore.snapshot?.menus ?? []))

const compactHeaderHeight = computed(() => Math.max(56, preferences.headerHeight))
const compactTabbarHeight = computed(() => (preferences.tabbarEnable ? 46 : 0))
const compactSidebarWidth = computed(() => Math.min(preferences.sidebarWidth, 252))
const compactSidebarCollapseWidth = computed(() => Math.max(56, preferences.sidebarCollapseWidth))

const sidebarRenderWidth = computed(() => {
  if (preferences.layout === 'sidebar-mixed-nav') {
    return Math.max(compactSidebarWidth.value + 44, 232)
  }
  return compactSidebarWidth.value
})

function buildNavLinks(nodes: MenuItem[]): NavLink[] {
  const snapshot = authStore.snapshot
  return nodes
    .map((node) => {
      const path = normalizeRoutePath(node.path)
      const children = buildNavLinks(node.children ?? [])
      const fallbackPath = children[0]?.to ?? ''
      const to = path && isAllowedMenuPath(snapshot, node) ? path : fallbackPath
      if (!to && children.length === 0) return null
      const presentation = resolveMenuPresentation({
        code: node.code,
        routeKey: node.component ?? node.permission ?? node.code,
        title: node.name ?? node.title,
        icon: node.icon,
      })
      return {
        id: to || `menu-${node.id}`,
        to,
        label: presentation.title,
        icon: resolveAppIcon(presentation.icon),
        children,
      } satisfies NavLink
    })
    .filter((item): item is NavLink => Boolean(item))
}

function normalizeRoutePath(path?: string | null) {
  const normalized = path?.trim()
  if (!normalized) return ''
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}

const routerViewKey = computed(() => `${route.fullPath}:${authStore.tenantId}:${routerViewRefreshKey.value}`)

const isPageFullscreen = ref(false)

const headerZIndex = computed(() => {
  const base = preferences.zIndex || 200
  return isHeaderNav.value ? base + 1 : base
})

const headerWidth = computed(() => {
  if (!preferences.headerFixed || isPageFullscreen.value) return '100%'
  if (isHeaderNav.value || isFullContent.value) return '100%'
  return `calc(100% - ${effectiveSidebarWidth.value}px)`
})

const headerWrapperStyle = computed(() => {
  const height = compactHeaderHeight.value + compactTabbarHeight.value
  const fixed = preferences.headerFixed && !isPageFullscreen.value
  return {
    height: `${height}px`,
    width: headerWidth.value,
    left: fixed && !isHeaderNav.value && !isFullContent.value ? `${effectiveSidebarWidth.value}px` : '0',
    transition: 'left 220ms cubic-bezier(0.2, 0, 0, 1), width 220ms cubic-bezier(0.2, 0, 0, 1)',
  }
})

const headerMenuAlign = computed(() => {
  if (isHeaderNav.value || isMixedNav.value) return 'center'
  return 'start'
})

const pageTransitionName = computed(() => {
  if (!preferences.transitionEnable || !preferences.transitionName) {
    return ''
  }
  return preferences.transitionName
})

const sidebarWidth = computed(() => {
  if (!sidebarEnable.value) return 0
  if (isMobile.value) return 0
  if (sidebarCollapsed.value) return compactSidebarCollapseWidth.value
  return sidebarRenderWidth.value
})

const effectiveSidebarWidth = computed(() => {
  if (!sidebarEnable.value || isMobile.value) return 0
  if (sidebarCollapsed.value && preferences.sidebarExpandOnHover && sidebarHoverExpanded.value) {
    return sidebarRenderWidth.value
  }
  return sidebarWidth.value
})

const contentShellStyle = computed(() => ({
  marginLeft: `${effectiveSidebarWidth.value}px`,
}))

const mainStyle = computed(() => {
  if (isFullContent.value) {
    return {}
  }
  const headerHeight = compactHeaderHeight.value + compactTabbarHeight.value
  return {
    paddingTop: preferences.headerFixed && !isPageFullscreen.value ? `${headerHeight}px` : '0px',
  }
})

function handleSidebarHoverChange(expanded: boolean) {
  sidebarHoverExpanded.value = expanded
}

function handleSidebarLeave() {
  sidebarHoverExpanded.value = false
}

function toggleSidebar() {
  if (isMobile.value) {
    mobileSidebarOpen.value = !mobileSidebarOpen.value
  } else {
    sidebarHoverExpanded.value = false
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}

function reloadPage() {
  window.location.reload()
}

function togglePageFullscreen() {
  isPageFullscreen.value = !isPageFullscreen.value
}

function toggleFullScreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen?.()
  }
}

function toggleLanguage() {
  ElMessage.info('当前仅支持中文')
}

function handleOpenSearch() {
  ElMessage.info('搜索功能开发中')
}

function handleMenuSelect(path: string) {
  if (path && route.path !== path) {
    void router.push(path)
  }
}

function handleMenuOpen() {
  // 水平菜单展开事件，暂不需要额外处理
}

// 租户加载
const tenantOptionsStorageKey = 'tenant-switch-options'
const tenantOptions = ref<Array<{ tenantId: string; name: string }>>(restoreTenantOptions())
const toolbarTenantOptions = computed(() => withSessionTenantOptions(tenantOptions.value))
const canLoadTenants = computed(() => {
  return authStore.operatorTenantId === 'platform' || authStore.snapshot?.superAdmin === true
})

onMounted(() => {
  initTheme()
  checkMobile()
  window.addEventListener('resize', checkMobile)

  if (authStore.passwordChangeRequired) {
    return
  }
  notificationBellRef.value?.loadUnreadNotificationCount()
  if (canLoadTenants.value) {
    loadTenantOptions()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})

function checkMobile() {
  isMobile.value = window.matchMedia('(max-width: 768px)').matches
  if (isMobile.value) {
    sidebarHoverExpanded.value = false
  }
}

async function loadTenantOptions() {
  try {
    const page = await queryTenants({ page: 1, size: 200 }, { silentAuthFailure: true, suppressErrorMessage: true })
    const records = page.records ?? []
    if (records.length > 0) {
      tenantOptions.value = records
      sessionStorage.setItem(tenantOptionsStorageKey, JSON.stringify(records))
    } else {
      tenantOptions.value = withSessionTenantOptions([])
    }
  } catch {
    tenantOptions.value = withSessionTenantOptions(tenantOptions.value)
  }
}

function restoreTenantOptions() {
  const raw = sessionStorage.getItem(tenantOptionsStorageKey)
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as Array<{ tenantId: string; name: string }>
    return Array.isArray(parsed) ? parsed.filter((tenant) => tenant?.tenantId && tenant?.name) : []
  } catch {
    sessionStorage.removeItem(tenantOptionsStorageKey)
    return []
  }
}

function withSessionTenantOptions(options: Array<{ tenantId: string; name: string }>) {
  const items = [...options]
  const appendIfMissing = (tenantId: string | undefined, name: string) => {
    const normalizedTenantId = tenantId?.trim()
    if (!normalizedTenantId || items.some((tenant) => tenant.tenantId === normalizedTenantId)) {
      return
    }
    items.unshift({ tenantId: normalizedTenantId, name })
  }
  appendIfMissing(authStore.operatorTenantId, authStore.operatorTenantId === 'platform' ? '平台租户' : '登录租户')
  appendIfMissing(authStore.tenantId, '当前租户')
  return items
}

async function handleTenantChange(newTenantId: string) {
  const targetTenantId = newTenantId.trim()
  if (!targetTenantId || targetTenantId === authStore.tenantId || tenantSwitching.value) return
  const targetTenant = tenantOptions.value.find((tenant) => tenant.tenantId === targetTenantId)
  const targetName = targetTenant ? `${targetTenant.name}（${targetTenant.tenantId}）` : targetTenantId
  try {
    await ElMessageBox.confirm(
      `切换后菜单、权限和当前页面数据会按 ${targetName} 重新加载，是否继续？`,
      '切换租户确认',
      {
        type: 'warning',
        confirmButtonText: '切换',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  try {
    await authStore.switchTenant(targetTenantId)
    if (canLoadTenants.value) {
      await loadTenantOptions()
    }
    await notificationBellRef.value?.loadUnreadNotificationCount()
    notificationBellRef.value?.startSseSubscription()
    const redirected = await settleRouteAfterTenantSwitch()
    if (!redirected) {
      ElMessage.success(`已切换到租户 ${authStore.tenantId}`)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '租户切换失败，请稍后重试')
  }
}

async function settleRouteAfterTenantSwitch() {
  const snapshot = authStore.snapshot
  if (isAllowedRoute(snapshot, route)) {
    routerViewRefreshKey.value += 1
    return false
  }

  const fallbackPath = resolveFirstAllowedPath(snapshot) || '/dashboard'
  if (fallbackPath !== route.path) {
    await router.replace(fallbackPath)
    ElMessage.warning('当前页面在新租户下不可用，已跳转')
    return true
  }
  routerViewRefreshKey.value += 1
  return false
}

function handleUserCommand(command: string) {
  if (command === 'profile') {
    void router.push('/account/profile')
    return
  }
  if (command === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}

async function openSessions() {
  if (authStore.passwordChangeRequired) {
    return
  }
  sessionsVisible.value = true
  await loadSessions()
}

async function loadSessions() {
  if (authStore.passwordChangeRequired) {
    sessionsList.value = []
    return
  }
  sessionsLoading.value = true
  try {
    const sessions = await querySessions('own')
    sessionsList.value = Array.isArray(sessions) ? sessions : sessions.records
  } finally {
    sessionsLoading.value = false
  }
}

async function kickSession(sessionId: string) {
  try {
    await ElMessageBox.confirm('强制下线后该设备将立即失去访问权限，是否继续？', '下线确认', { type: 'warning' })
    await forceOffline(sessionId)
    ElMessage.success('设备已下线')
    await loadSessions()
  } catch {
    // 用户取消了确认对话框。
  }
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

// 主题偏好同步
watch(
  () => preferences.theme,
  (theme) => {
    if (theme === 'auto') {
      setDarkMode(window.matchMedia('(prefers-color-scheme: dark)').matches)
    } else {
      setDarkMode(theme === 'dark')
    }
  },
  { immediate: true },
)

// 移动端路由变化时关闭侧边栏抽屉
watch(
  () => route.path,
  () => {
    if (isMobile.value && mobileSidebarOpen.value) {
      mobileSidebarOpen.value = false
    }
  },
)
</script>

<style scoped lang="scss">
.main-card {
  background: transparent;
  min-height: calc(100vh - var(--header-height, 50px) - 38px);
  border-radius: 0;
  padding: 0;
}

.layout-main-shell {
  min-height: 100vh;
}

.layout-main-content {
  background: hsl(var(--background-deep));
}

.is-page-fullscreen {
  :deep(.layout-sidebar) {
    flex-basis: 0 !important;
    width: 0 !important;
    min-width: 0 !important;
    opacity: 0;
    transform: translate3d(-16px, 0, 0);
    border-right-color: transparent;
    pointer-events: none;
  }

  :deep(.layout-header) {
    height: 0 !important;
    min-height: 0;
    opacity: 0;
    transform: translate3d(0, -16px, 0);
    border-bottom-color: transparent;
    pointer-events: none;
  }

  :deep(.layout-tabbar) {
    height: 0 !important;
    min-height: 0;
    opacity: 0;
    pointer-events: none;
  }
}

.session-dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -4px 0 12px;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}

.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

// 移动端侧边栏抽屉过渡
.layout-sidebar-drawer {
  position: static;
  transition: transform 0.3s ease-in-out;
}

.layout-sidebar-drawer.is-mobile {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  z-index: 100;
  transform: translateX(-100%);
}

.layout-sidebar-drawer.is-mobile:has(.layout-sidebar:not([style*="width: 0px"])) {
  transform: translateX(0);
}

.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.3s ease-in-out;
}

.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(-100%);
}

.drawer-enter-to,
.drawer-leave-from {
  transform: translateX(0);
}
</style>
