<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { useRoute } from 'vue-router'
import { Expand, Fold, RefreshRight } from '@element-plus/icons-vue'

interface Props {
  headerHeight?: number
  isMobile?: boolean
  show?: boolean
  sidebarCollapsed?: boolean
  sidebarWidth?: number
  theme?: string
  width?: string
  zIndex?: number
  menuAlign?: 'start' | 'center' | 'end'
}

const props = withDefaults(defineProps<Props>(), {
  headerHeight: 56,
  isMobile: false,
  show: true,
  sidebarCollapsed: false,
  sidebarWidth: 188,
  theme: 'light',
  width: '100%',
  zIndex: 100,
  menuAlign: 'start',
})

const emit = defineEmits<{
  'toggle-sidebar': []
  'refresh': []
}>()

const route = useRoute()
const slots = useSlots()

const headerStyle = computed(() => ({
  '--header-height': `${props.headerHeight}px`,
  height: `${props.headerHeight}px`,
  width: props.width,
  zIndex: props.zIndex,
}))

const canToggleSidebar = computed(() => props.isMobile || props.sidebarWidth > 0)

const pageTitle = computed(() => String(route.meta.title ?? '控制台'))
const breadcrumbRoot = computed(() => {
  if (route.path.startsWith('/platform')) return '平台管理'
  if (route.path.startsWith('/system')) return '系统管理'
  if (route.path.startsWith('/log')) return '日志审计'
  if (route.path.startsWith('/workflow')) return '流程中心'
  if (route.path.startsWith('/account')) return '个人中心'
  if (route.path.startsWith('/audit')) return '安全审计'
  return '控制台'
})
</script>

<template>
  <header
    v-if="show"
    :class="[theme, `menu-align-${menuAlign}`]"
    :style="headerStyle"
    class="layout-header"
  >
    <div v-if="slots.logo" class="layout-header__logo">
      <slot name="logo" />
    </div>

    <div class="layout-header__main">
      <div class="layout-header__left">
        <button
          v-if="canToggleSidebar"
          type="button"
          class="header-icon-btn"
          @click="emit('toggle-sidebar')"
        >
          <el-icon>
            <component :is="props.sidebarCollapsed ? Expand : Fold" />
          </el-icon>
        </button>

        <button type="button" class="header-icon-btn" @click="emit('refresh')">
          <el-icon>
            <RefreshRight />
          </el-icon>
        </button>

        <slot name="breadcrumb">
          <el-breadcrumb separator="/" class="layout-header__breadcrumb hidden lg:flex">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">
              {{ breadcrumbRoot }}
            </el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle && pageTitle !== '运行总览'">
              {{ pageTitle }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </slot>
      </div>

      <div class="layout-header__center" :class="`justify-${menuAlign}`">
        <slot name="header-menu" />
      </div>

      <div class="layout-header__right">
        <slot name="header-right" />
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
.layout-header {
  display: flex;
  align-items: center;
  width: 100%;
  border-bottom: 1px solid hsl(var(--border));
  background: hsl(var(--header));
  backdrop-filter: blur(8px);
  box-shadow: none;
}

.layout-header__logo {
  display: flex;
  height: 100%;
  align-items: center;
  overflow: hidden;
  padding: 0 12px 0 10px;
}

.layout-header__main {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 6px;
  padding: 0 12px 0 0;
}

.layout-header__left,
.layout-header__right {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}

.layout-header__left {
  max-width: 38%;
}

.layout-header__center {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  padding: 0 6px;

  &.justify-start {
    justify-content: flex-start;
  }

  &.justify-center {
    justify-content: center;
  }

  &.justify-end {
    justify-content: flex-end;
  }
}

.header-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font-size: 15px;
  color: hsl(var(--foreground) / 0.85);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    color: hsl(var(--primary));
    background-color: hsl(var(--accent));
  }
}

.layout-header__breadcrumb {
  height: 30px;
  align-items: center;
  padding: 0 1px 0 6px;
  border-radius: 0;
  background: transparent;
  transform: translateY(0.5px);
}

:deep(.el-breadcrumb__inner) {
  color: hsl(var(--muted-foreground));
  font-weight: 500;
  font-size: 14px;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: hsl(var(--foreground));
  font-weight: 700;
}

:deep(.el-breadcrumb__separator) {
  color: hsl(var(--border));
  margin: 0 6px;
}

@media (max-width: 1280px) {
  .layout-header__left {
    max-width: 46%;
  }
}

@media (max-width: 768px) {
  .layout-header__logo {
    min-width: auto !important;
    padding-right: 10px;
    padding-left: 8px;
    border-right: 0;
  }

  .layout-header__main {
    gap: 6px;
    padding: 0 8px 0 0;
  }

  .layout-header__left {
    max-width: none;
  }

  .layout-header__center {
    justify-content: flex-start;
  }
}
</style>
