<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapse ? '64px' : '200px'" class="admin-aside">
      <div class="logo-box">
        <el-icon class="logo-icon-svg"><Platform /></el-icon>
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
          <el-icon class="action-icon action-icon--refresh" @click="reloadPage"><RefreshRight /></el-icon>
          <el-breadcrumb separator=">" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">{{ breadcrumbRoot }}</el-breadcrumb-item>
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
              style="width: 120px; margin: 0 8px;"
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

          <div class="search-capsule">
            <el-icon><Search /></el-icon>
            <span class="search-text">搜索</span>
            <kbd class="search-shortcut">Ctrl K</kbd>
          </div>

          <div class="header-actions">
            <el-icon class="action-icon" title="设置"><Setting /></el-icon>
            <el-icon class="action-icon" title="暗色模式" @click="toggleDark"><Moon /></el-icon>
            <el-icon class="action-icon" title="语言"><svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="1em" height="1em"><path fill="currentColor" d="M140 188h584v164h-76v-88H216v484h356v76H140z"/><path fill="currentColor" d="M400 340h-76C324 233.9 410 148 516 148c96 0 175.5 70.8 190.2 163.3l-75.1 11.3A116.2 116.2 0 0 0 516 224c-64.2 0-116 52-116 116m484 536H516V512h368z"/></svg></el-icon>
            <el-icon class="action-icon" title="全屏" @click="toggleFullScreen"><FullScreen /></el-icon>
          </div>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="avatar-container">
              <div class="avatar-wrapper">
                <el-avatar :size="28" class="user-avatar">{{ avatarName }}</el-avatar>
                <div class="status-dot"></div>
              </div>
              <el-icon class="el-icon--right" style="margin-left: 8px; color: #606266;"><ArrowDown /></el-icon>
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
        <button
          v-if="isTagsOverflowing"
          class="tags-scroll-btn tags-scroll-btn--left"
          type="button"
          :disabled="!canScrollTagsLeft"
          @click="scrollTagsBy(-360)"
        >
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div ref="tagsScrollRef" class="tags-scrollbar" @scroll="updateTagsScrollState" @wheel="handleTagsWheel">
          <div class="tags-view-wrapper">
            <button
              v-for="(tag, index) in visitedViews"
              :key="tag.path"
              class="tags-view-item"
              :class="{ active: route.path === tag.path }"
              type="button"
              :data-tag-path="tag.path"
              @click="router.push(tag.path)"
            >
              <span class="tags-view-item-inner">
                <el-icon class="tags-view-icon" v-if="tag.path === '/dashboard'"><HomeFilled /></el-icon>
                <el-icon class="tags-view-icon" v-else-if="isMenuTag(tag)">
                  <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" aria-hidden="true">
                    <path fill="currentColor" d="M192 256h640v72H192zm0 220h640v72H192zm0 220h640v72H192z" />
                  </svg>
                </el-icon>
                <el-icon class="tags-view-icon" v-else><component :is="resolveTagIcon(tag)" /></el-icon>
                <span class="tags-view-item-text">{{ tag.title }}</span>
                <el-icon v-if="tag.path === '/dashboard'" class="tags-view-pin"><svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="1em" height="1em"><path fill="currentColor" d="M640 192v256l128 192v64H544v256L512 992l-32-32V704H256v-64l128-192V192h-64v-64h448v64h-64zM384 192v271.04l-113.344 170.048L281.344 640h461.312l10.688-6.912L640 463.04V192H384z"/></svg></el-icon>
                <el-icon v-if="tag.path !== '/dashboard'" class="tags-view-close" @click.stop="closeView(tag)"><Close /></el-icon>
              </span>
              <span class="tags-view-divider" v-if="index !== visitedViews.length - 1 && route.path !== tag.path && route.path !== visitedViews[index + 1]?.path"></span>
            </button>
          </div>
        </div>
        <div class="tags-action-area">
          <button
            v-if="isTagsOverflowing"
            class="tags-scroll-btn tags-scroll-btn--right"
            type="button"
            :disabled="!canScrollTagsRight"
            @click="scrollTagsBy(360)"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>
          <el-dropdown trigger="click" @command="handleTagsCommand">
            <div class="tags-action-btn"><el-icon><ArrowDown /></el-icon></div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="refresh"><el-icon><Refresh /></el-icon> 重新加载</el-dropdown-item>
                <el-dropdown-item command="closeOther"><el-icon><Close /></el-icon> 关闭其他标签页</el-dropdown-item>
                <el-dropdown-item command="closeAll"><el-icon><Close /></el-icon> 关闭全部标签页</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <div class="tags-action-btn" @click="toggleFullScreen"><el-icon><FullScreen /></el-icon></div>
        </div>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Connection,
  Document,
  Expand,
  Flag,
  Fold,
  FullScreen,
  Histogram,
  HomeFilled,
  Moon,
  OfficeBuilding,
  Platform,
  Refresh,
  RefreshRight,
  Search,
  Setting,
  User,
  Close,
} from '@element-plus/icons-vue'
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
const tagsScrollRef = ref<HTMLDivElement | null>(null)
const isTagsOverflowing = ref(false)
const canScrollTagsLeft = ref(false)
const canScrollTagsRight = ref(false)

const pageTitle = computed(() => String(route.meta.title ?? '控制台'))
const breadcrumbRoot = computed(() => {
  if (route.path.startsWith('/platform')) {
    return '平台管理'
  }
  if (route.path.startsWith('/system')) {
    return '系统管理'
  }
  return '控制台'
})
const avatarName = computed(() => {
  const name = authStore.snapshot?.username || 'U'
  return name.charAt(0).toUpperCase()
})

// tags view simplified
const CACHED_VIEWS_KEY = 'ea_visited_views_cache'
const cachedViews = sessionStorage.getItem(CACHED_VIEWS_KEY)
const visitedViews = ref<{ path: string; title: string }[]>(
  cachedViews ? JSON.parse(cachedViews) : []
)

watch(visitedViews, (newVal) => {
  sessionStorage.setItem(CACHED_VIEWS_KEY, JSON.stringify(newVal))
  nextTick(updateTagsScrollState)
}, { deep: true })

watch(() => route.path, async () => {
  addVisitedView()
  await nextTick()
  scrollActiveTagIntoView()
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

function reloadPage() {
  window.location.reload()
}

function toggleFullScreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen()
    }
  }
}

function toggleDark() {
  const html = document.documentElement
  if (html.classList.contains('dark')) {
    html.classList.remove('dark')
  } else {
    html.classList.add('dark')
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
  nextTick(updateTagsScrollState)
}

function handleTagsCommand(command: string) {
  if (command === 'refresh') {
    reloadPage()
  } else if (command === 'closeOther') {
    const activeRoute = visitedViews.value.find(v => v.path === route.path)
    if (activeRoute) {
      visitedViews.value = visitedViews.value.filter(v => v.path === '/dashboard' || v.path === activeRoute.path)
    }
  } else if (command === 'closeAll') {
    visitedViews.value = visitedViews.value.filter(v => v.path === '/dashboard')
    router.push('/dashboard')
  }
  nextTick(updateTagsScrollState)
}

function updateTagsScrollState() {
  const scroller = tagsScrollRef.value
  if (!scroller) return
  const maxScrollLeft = scroller.scrollWidth - scroller.clientWidth
  isTagsOverflowing.value = maxScrollLeft > 1
  canScrollTagsLeft.value = scroller.scrollLeft > 1
  canScrollTagsRight.value = scroller.scrollLeft < maxScrollLeft - 1
}

function scrollTagsBy(distance: number) {
  tagsScrollRef.value?.scrollBy({ left: distance, behavior: 'smooth' })
  window.setTimeout(updateTagsScrollState, 220)
}

function handleTagsWheel(event: WheelEvent) {
  const scroller = tagsScrollRef.value
  if (!scroller || !isTagsOverflowing.value) return

  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  if (!delta) return

  event.preventDefault()
  scroller.scrollLeft += delta
  updateTagsScrollState()
}

function scrollActiveTagIntoView() {
  const scroller = tagsScrollRef.value
  if (!scroller) return
  const activeTag = Array.from(scroller.querySelectorAll<HTMLElement>('.tags-view-item')).find((item) => {
    return item.dataset.tagPath === route.path
  })
  activeTag?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  updateTagsScrollState()
}

function isMenuTag(tag: { path: string; title: string }) {
  return tag.title.includes('菜单') || tag.path.includes('resources')
}

function resolveTagIcon(tag: { path: string; title: string }) {
  if (tag.title.includes('用户')) return User
  if (tag.title.includes('角色')) return Connection
  if (tag.title.includes('部门')) return OfficeBuilding
  if (tag.title.includes('租户')) return Flag
  if (tag.title.includes('审计') || tag.title.includes('分析')) return Histogram
  if (tag.title.includes('设置') || tag.title.includes('配置')) return Setting
  return Document
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
  window.addEventListener('resize', updateTagsScrollState)
  nextTick(() => {
    scrollActiveTagIntoView()
    updateTagsScrollState()
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTagsScrollState)
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
  background-color: #f5f6f8;
  color: #1f2937;
}

.admin-aside {
  background-color: #fff;
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #edf0f5;
  box-shadow: none;
  z-index: 10;
  overflow: hidden;
}

.logo-box {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  overflow: hidden;
  border-bottom: 1px solid #edf0f5;
  background: #fff;
}

.logo-icon-svg {
  font-size: 23px;
  color: #303133;
}

.logo-text {
  margin: 0 0 0 7px;
  font-size: 14px;
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
  padding: 0 14px;
  border-bottom: 1px solid #edf0f5;
  box-shadow: none;
}

.header-left, .header-right {
  display: flex;
  align-items: center;
  min-width: 0;
}

.header-left {
  gap: 4px;
}

.header-right {
  gap: 9px;
  flex-shrink: 0;
}

.action-icon {
  font-size: 16px;
  cursor: pointer;
  width: 28px;
  height: 28px;
  margin-right: 0;
  color: #30343b;
  border-radius: 6px;
  transition: background-color 0.2s ease, color 0.2s ease;
  &:hover {
    color: #1677ff;
    background: #f3f7ff;
  }
}

.action-icon--refresh {
  margin-right: 2px;
}

.breadcrumb {
  min-width: 0;
  font-size: 13px;
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  color: #8b95a5;
  font-weight: 500;
}

.breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #1f2937;
  font-weight: 650;
}

.breadcrumb :deep(.el-breadcrumb__separator) {
  margin: 0 6px;
  color: #b7bfcc;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.search-capsule {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  min-width: 138px;
  background-color: #f5f7fb;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 0 9px;
  font-size: 13px;
  color: #7f8897;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease;

  .el-icon {
    font-size: 16px;
    color: #606b7a;
  }

  .search-text {
    line-height: 1;
  }

  .search-shortcut {
    display: inline-flex;
    align-items: center;
    height: 20px;
    margin-left: auto;
    padding: 0 6px;
    border: 0;
    border-radius: 999px;
    background: #fff;
    box-shadow: 0 0 0 1px #e6eaf0 inset;
    color: #7b8494;
    font-family: inherit;
    font-size: 12px;
    line-height: 1;
  }
  
  &:hover {
    background-color: #eef3fa;
    border-color: #e4e9f2;
  }
}

.borderless-select :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background-color: transparent !important;
  padding: 0 4px;
  min-height: 28px;
}
.borderless-select :deep(.el-input__inner) {
  font-weight: 650;
  color: #1f2937;
}

.tenant-selector {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #8b95a5;
  white-space: nowrap;
}

.tenant-label {
  color: #8b95a5;
}

.tenant-value {
  color: #1f2937;
  font-weight: 650;
}

.avatar-container {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 48px;
  cursor: pointer;
  padding-left: 2px;
}

.avatar-wrapper {
  position: relative;
  display: flex;
}

.status-dot {
  position: absolute;
  bottom: 1px;
  right: 1px;
  width: 9px;
  height: 9px;
  background-color: #22c55e;
  border: 2px solid #fff;
  border-radius: 50%;
  z-index: 2;
}

.user-avatar {
  background: linear-gradient(135deg, #fb2b3d 0%, #ff6a4a 55%, #16c784 56%, #16c784 100%);
  box-shadow: 0 0 0 2px #fff, 0 0 0 3px #f0f2f6;
  color: #fff;
  font-weight: 650;
}

.tags-view-container {
  height: 36px;
  width: 100%;
  background: #fff;
  padding: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  box-sizing: border-box;
  border-bottom: 1px solid #edf0f5;
}

.tags-scroll-btn {
  flex: 0 0 30px;
  width: 30px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 0;
  background: #fff;
  color: #687386;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, opacity 0.2s ease;

  .el-icon {
    font-size: 15px;
  }

  &:hover:not(:disabled) {
    color: #1677ff;
    background: #f3f7ff;
  }

  &:disabled {
    cursor: default;
    opacity: 0.36;
  }
}

.tags-scroll-btn--left {
  border-right: 1px solid #edf0f5;
}

.tags-scroll-btn--right {
  height: 30px;
  border-radius: 6px;
}

.tags-scrollbar {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  white-space: nowrap;
  height: 100%;
  min-width: 0;
  padding-left: 12px;
  overscroll-behavior: contain;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.tags-scrollbar::-webkit-scrollbar {
  display: none;
}

.tags-scroll-btn + .tags-scrollbar {
  padding-left: 0;
}

.tags-view-wrapper {
  display: flex;
  align-items: flex-end;
  height: 100%;
  width: max-content;
  min-width: 100%;
  flex-wrap: nowrap;
}

.tags-view-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  height: 32px;
  max-width: 156px;
  padding: 0 10px;
  appearance: none;
  font-family: inherit;
  font-size: 13px;
  color: #2f3440;
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: 8px 8px 0 0;
  transition: background-color 0.2s, color 0.2s;
  white-space: nowrap;

  .tags-view-item-inner {
    display: inline-flex;
    align-items: center;
    min-width: 0;
    gap: 5px;
  }

  .tags-view-icon {
    flex: 0 0 auto;
    font-size: 16px;
    color: currentColor;
  }

  .tags-view-item-text {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .tags-view-pin {
    flex: 0 0 auto;
    font-size: 12px;
    margin-left: 1px;
    transform: rotate(45deg);
    color: #6b7280;
  }

  &:hover {
    color: #1677ff;
    background: #f7faff;
  }

  &.active {
    color: #1677ff;
    background-color: #e8f3ff;
    font-weight: 650;
  }

  .tags-view-close {
    flex: 0 0 auto;
    width: 15px;
    height: 15px;
    font-size: 12px;
    border-radius: 50%;
    transition: all .2s;
    
    &:hover {
      background-color: rgba(31, 41, 55, 0.12);
      color: #1f2937;
    }
  }

  .tags-view-divider {
    position: absolute;
    right: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 1px;
    height: 14px;
    background-color: #e1e5ec;
  }
}

.tags-action-area {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 4px;
  border-left: 1px solid #edf0f5;
  flex-shrink: 0;

  .tags-action-btn {
    width: 30px;
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #4b5563;
    border-radius: 6px;
    transition: all 0.2s;
    background: transparent;

    &:hover {
      color: #1677ff;
      background: #f3f7ff;
    }

    .el-icon {
      font-size: 15px;
      outline: none;
    }
  }
}

.admin-main {
  background: #f5f6f8;
  padding: 10px 12px;
  overflow: auto;
}

.main-card {
  background: #fff;
  min-height: calc(100vh - 114px);
  border-radius: 4px;
  padding: 16px;
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

@media (max-width: 1180px) {
  .tenant-selector,
  .search-capsule {
    display: none;
  }

  .admin-header {
    padding: 0 10px;
  }

  .header-right {
    gap: 10px;
  }
}

@media (max-width: 860px) {
  .admin-aside {
    width: 64px !important;
  }

  .admin-aside :deep(.el-menu-item) {
    justify-content: center;
    padding: 0 !important;
  }

  .admin-aside :deep(.el-menu-item span) {
    display: none;
  }

  .logo-text,
  .breadcrumb {
    display: none;
  }

  .admin-header {
    height: 46px;
    padding: 0 6px;
  }

  .avatar-container {
    height: 46px;
  }

  .tags-view-container {
    height: 34px;
    padding-left: 0;
  }

  .tags-scrollbar {
    padding-left: 4px;
  }

  .tags-scroll-btn {
    flex-basis: 28px;
    width: 28px;
    height: 34px;
  }

  .tags-scroll-btn--right {
    height: 28px;
  }

  .tags-view-item {
    height: 30px;
    max-width: 124px;
    padding: 0 8px;
    font-size: 13px;
  }

  .tags-action-area {
    height: 34px;
    padding: 0 2px;
  }

  .tags-action-area .tags-action-btn {
    width: 28px;
    height: 28px;
  }

  .admin-main {
    padding: 6px;
  }

  .main-card {
    min-height: calc(100vh - 92px);
    padding: 10px;
  }
}
</style>
