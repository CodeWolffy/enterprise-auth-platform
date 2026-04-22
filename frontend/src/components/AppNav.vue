<template>
  <el-menu
    :default-active="activePath"
    class="sidebar-menu"
    :collapse="collapse"
    :router="true"
    background-color="#001529"
    text-color="#a6bdbd"
    active-text-color="#fff"
  >
    <el-menu-item v-for="item in visibleLinks" :key="item.to" :index="item.to">
      <el-icon><component :is="item.icon" /></el-icon>
      <template #title>{{ item.label }}</template>
    </el-menu-item>
  </el-menu>
</template>

<script setup lang="ts">
import {
  Avatar,
  Connection,
  Flag,
  Histogram,
  Monitor,
  OfficeBuilding,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { MenuItem } from '@/types/auth'

defineProps<{
  collapse: boolean
}>()

const route = useRoute()
const authStore = useAuthStore()

const activePath = computed(() => route.path)

const ROUTE_KEY_PATH_MAP: Record<string, string> = {
  dashboard: '/dashboard',
  users: '/system/users',
  roles: '/system/roles',
  depts: '/system/depts',
  tenants: '/system/tenants',
  audit: '/system/audit',
  settings: '/system/settings',
}

const iconMap: Record<string, any> = {
  dashboard: Monitor,
  users: Avatar,
  roles: Connection,
  depts: OfficeBuilding,
  tenants: Flag,
  audit: Histogram,
  settings: Setting,
}

const titleMap: Record<string, string> = {
  dashboard: '运行总览',
  users: '用户管理',
  roles: '角色管理',
  depts: '部门管理',
  tenants: '租户管理',
  audit: '安全审计',
  settings: '系统管理',
}

const visibleLinks = computed(() => {
  const flattened = flattenMenus(authStore.snapshot?.menus ?? [])
  const links = flattened
    .map((menu) => {
      const routeKey = menu.routeKey?.trim()
      if (!routeKey) {
        return null
      }
      const path = ROUTE_KEY_PATH_MAP[routeKey]
      if (!path) {
        return null
      }
      return {
        to: path,
        label: titleMap[routeKey] || menu.title,
        icon: iconMap[routeKey] || Tickets,
      }
    })
    .filter((item): item is { to: string; label: string; icon: any } => Boolean(item))

  const deduplicated = new Map<string, { to: string; label: string; icon: any }>()
  for (const item of links) {
    if (!deduplicated.has(item.to)) {
      deduplicated.set(item.to, item)
    }
  }
  return Array.from(deduplicated.values())
})

function flattenMenus(nodes: MenuItem[]): MenuItem[] {
  const result: MenuItem[] = []
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      result.push(item)
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(nodes)
  return result
}
</script>

<style scoped lang="scss">
.sidebar-menu {
  border-right: none;
  &:not(.el-menu--collapse) {
    width: 219px;
  }
}
.sidebar-menu :deep(.el-menu-item) {
  &.is-active {
    background-color: var(--accent, #409eff) !important;
  }
  &:hover {
    background-color: #263445 !important;
  }
}
</style>
