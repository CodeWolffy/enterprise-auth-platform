<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'

import { DArrowLeft, DArrowRight } from '@element-plus/icons-vue'

interface Props {
  collapse?: boolean
  collapseWidth?: number
  expandOnHover?: boolean
  show?: boolean
  theme?: 'dark' | 'light'
  width?: number
  zIndex?: number
}

const props = withDefaults(defineProps<Props>(), {
  collapse: false,
  collapseWidth: 52,
  expandOnHover: true,
  show: true,
  theme: 'dark',
  width: 220,
  zIndex: 100,
})

const emit = defineEmits<{
  'update:collapse': [boolean]
  'update:expandOnHover': [boolean]
  'hover-change': [boolean]
  leave: []
}>()

const hovering = ref(false)
const isExpanded = computed(() => props.expandOnHover && hovering.value && props.collapse)

const effectiveWidth = computed(() => {
  if (!props.show) return 0
  if (isExpanded.value) return props.width
  return props.collapse ? props.collapseWidth : props.width
})

const sidebarStyle = computed(() => ({
  width: `${effectiveWidth.value}px`,
  zIndex: props.zIndex,
}))

const contentStyle = computed(() => ({
  height: `calc(100% - 100px)`,
  paddingTop: '10px',
}))

const collapseStyle = computed(() => ({
  height: '44px',
}))

const showCollapseButton = computed(() => !isExpanded.value)

function handleMouseEnter() {
  if (props.expandOnHover && props.collapse) {
    hovering.value = true
    emit('hover-change', true)
  }
}

function handleMouseLeave() {
  if (hovering.value) {
    emit('hover-change', false)
  }
  hovering.value = false
  emit('leave')
}

function toggleCollapse() {
  emit('update:collapse', !props.collapse)
}

watchEffect(() => {
  if (!props.expandOnHover) {
    hovering.value = false
    emit('hover-change', false)
  }
})
</script>

<template>
  <aside
    v-if="show"
    class="layout-sidebar flex flex-col h-screen border-r border-border overflow-hidden shrink-0"
    :class="[
      theme === 'dark' ? 'bg-sidebar text-foreground' : 'bg-sidebar text-foreground',
    ]"
    :style="sidebarStyle"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <!-- Logo 区域 -->
    <div
      class="layout-sidebar__logo flex h-[56px] items-center border-b border-border overflow-hidden shrink-0"
      :class="{ 'justify-center': collapse && !isExpanded, 'justify-start': !collapse || isExpanded }"
    >
      <div class="layout-sidebar__brand flex items-center gap-2 min-w-0">
        <span class="layout-sidebar__mark">
          <span class="layout-sidebar__mark-text">企</span>
        </span>
        <span
          v-show="!collapse || isExpanded"
          class="layout-sidebar__title truncate font-semibold text-sm text-foreground"
        >
          企业认证平台
        </span>
      </div>
    </div>

    <!-- 菜单内容区 -->
    <div class="layout-sidebar__content flex-1 overflow-y-auto overflow-x-hidden relative" :style="contentStyle">
      <slot />
    </div>

    <!-- 底部操作区 -->
    <div :style="collapseStyle" class="layout-sidebar__footer shrink-0 border-t border-border">
      <div
        v-if="showCollapseButton"
        class="layout-sidebar__control"
        :title="collapse ? '展开侧栏' : '折叠侧栏'"
        @click="toggleCollapse"
      >
        <el-icon class="text-base">
          <DArrowRight v-if="collapse" />
          <DArrowLeft v-else />
        </el-icon>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.layout-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  background: hsl(var(--sidebar));
  box-shadow: none;
  scrollbar-width: thin;
  scrollbar-color: hsl(var(--muted-foreground) / 0.16) transparent;
  transition:
    width 0.22s cubic-bezier(0.2, 0, 0, 1),
    box-shadow 0.2s ease,
    transform 0.22s cubic-bezier(0.2, 0, 0, 1);
  will-change: width;
  border-right-color: hsl(var(--border) / 0.95);

  &::-webkit-scrollbar {
    width: 5px;
  }

  &::-webkit-scrollbar-thumb {
    background-color: hsl(var(--muted-foreground) / 0.2);
    border-radius: 999px;
  }

  &::-webkit-scrollbar-track {
    background-color: transparent;
  }
}

.layout-sidebar__logo {
  background: hsl(var(--sidebar));
  padding: 0 16px;
}

.layout-sidebar__brand {
  width: 100%;
}

.layout-sidebar__mark {
  display: inline-flex;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
  box-shadow: none;
}

.layout-sidebar__mark-text {
  font-size: 14px;
  font-weight: 800;
  line-height: 1;
}

.layout-sidebar__title {
  letter-spacing: 0;
  font-size: 15px;
  font-weight: 700;
}

.layout-sidebar__content {
  padding-bottom: 2px;
  background: hsl(var(--sidebar));
}

:deep(.vben-menu.is-collapse > .vben-menu-list) {
  padding-right: 8px;
  padding-left: 8px;
}

.layout-sidebar__footer {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 6px 10px 8px;
  background: hsl(var(--sidebar));
}

.layout-sidebar__control {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    color: hsl(var(--primary));
    background: hsl(var(--accent));
  }
}

.dark .layout-sidebar {
  box-shadow: none;
  scrollbar-color: hsl(var(--muted-foreground) / 0.25) transparent;

  &::-webkit-scrollbar-thumb {
    background-color: hsl(var(--muted-foreground) / 0.25);
  }
}
</style>
