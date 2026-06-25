<template>
  <el-menu
    :default-active="activePath"
    class="sidebar-menu"
    :collapse="collapse"
    :router="true"
    :default-openeds="defaultOpeneds"
    background-color="#ffffff"
    text-color="#606266"
    active-text-color="#409eff"
  >
    <template v-for="item in visibleLinks" :key="item.id">
      <AppNavItem :item="item" />
    </template>
  </el-menu>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { resolveAppIcon, resolveMenuPresentation } from '@/app/registry/module-manifest'
import AppNavItem from './AppNavItem.vue'
import { useAuthStore } from '@/stores/auth'
import { isAllowedMenuPath } from '@/router/route-access'
import type { MenuItem } from '@/types/auth-models'

defineProps<{
  collapse: boolean
}>()

const route = useRoute()
const authStore = useAuthStore()

const activePath = computed(() => route.path)

interface NavLink {
  id: string
  to: string
  label: string
  icon: Component
  children: NavLink[]
}

const visibleLinks = computed(() => {
  return buildLinks(authStore.snapshot?.menus ?? [])
})
const defaultOpeneds = computed(() => {
  const openeds: string[] = []
  const walk = (items: NavLink[]) => {
    for (const item of items) {
      if (item.children.some((child) => child.to === activePath.value)) {
        openeds.push(item.id)
      }
      walk(item.children)
    }
  }
  walk(visibleLinks.value)
  return openeds
})

function buildLinks(nodes: MenuItem[]): NavLink[] {
  const links: NavLink[] = []
  const usedPaths = new Set<string>()
  const snapshot = authStore.snapshot

  for (const node of nodes) {
    const rawPath = normalizeRoutePath(node.path)
    const path = rawPath && isAllowedMenuPath(snapshot, node) ? rawPath : ''
    const children = buildLinks(node.children ?? [])
    const fallbackPath = children[0]?.to ?? ''
    if (!path && !fallbackPath) {
      continue
    }
    const to = path || fallbackPath
    if (path && usedPaths.has(path)) {
      continue
    }
    if (path) {
      usedPaths.add(path)
    }
    const presentation = resolveMenuPresentation({
      code: node.code,
      routeKey: node.component ?? node.permission ?? node.code,
      title: node.name ?? node.title,
      icon: node.icon,
    })
    links.push({
      id: path || `menu-${node.id}`,
      to,
      label: presentation.title,
      icon: resolveAppIcon(presentation.icon),
      children,
    })
  }
  return links
}

function normalizeRoutePath(path?: string | null) {
  const normalized = path?.trim()
  if (!normalized) {
    return ''
  }
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}
</script>

<style scoped lang="scss">
.sidebar-menu {
  border-right: none;
  &:not(.el-menu--collapse) {
    width: 199px;
  }
}
.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 36px;
  line-height: 36px;
  margin: 3px 6px;
  padding: 0 12px !important;
  border-radius: 4px;
  font-size: 13px;

  .el-icon {
    margin-right: 7px;
    font-size: 17px;
  }

  &.is-active {
    background-color: #e6f2ff !important;
    color: #409eff !important;
  }
  &:hover:not(.is-active) {
    background-color: #f5f7fa !important;
  }
}

// 多级菜单按真实层级缩进：一级保持紧凑，二级开始逐级右移。
$child-level-padding: 38px;
$child-level-step: 20px;

@for $depth from 1 through 5 {
  $selector-prefix: '.sidebar-menu:not(.el-menu--collapse) :deep(';
  @for $i from 1 through $depth {
    $selector-prefix: $selector-prefix + '.el-sub-menu > .el-menu > ';
  }
  $padding: $child-level-padding + ($depth - 1) * $child-level-step;

  #{$selector-prefix + '.el-menu-item)'},
  #{$selector-prefix + '.el-sub-menu > .el-sub-menu__title)'} {
    padding-left: $padding !important;
  }
}

.sidebar-menu :deep(.el-sub-menu__icon-arrow) {
  right: 10px;
  font-size: 12px;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item) {
  margin: 3px 6px;
  padding: 0 !important;
  justify-content: center;

  .el-icon {
    margin-right: 0;
  }
}
</style>
