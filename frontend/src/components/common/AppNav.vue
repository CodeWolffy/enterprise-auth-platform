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
      <el-sub-menu v-if="item.children.length" :index="item.id">
        <template #title>
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </template>
        <el-menu-item v-for="child in item.children" :key="child.id" :index="child.to">
          <el-icon><component :is="child.icon" /></el-icon>
          <template #title>{{ child.label }}</template>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-else :index="item.to">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.label }}</template>
      </el-menu-item>
    </template>
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
  Platform,
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
  'online-users': '/system/online-users',
  tenants: '/platform/tenants',
  audit: '/system/audit',
  settings: '/system/settings',
  dicts: '/platform/dicts',
  configs: '/platform/configs',
  notices: '/platform/notices',
  categories: '/platform/categories',
  'tenant-catalog': '/platform/tenant-catalog',
  resources: '/system/resources',
}

const iconMap: Record<string, any> = {
  dashboard: Monitor,
  system: Setting,
  'platform-management': Platform,
  users: Avatar,
  roles: Connection,
  depts: OfficeBuilding,
  'online-users': Monitor,
  tenants: Flag,
  audit: Histogram,
  settings: Setting,
  dicts: Tickets,
  configs: Setting,
  notices: Tickets,
  categories: Tickets,
  'tenant-catalog': Tickets,
  resources: Tickets,
}

const titleMap: Record<string, string> = {
  dashboard: '运行总览',
  system: '系统管理',
  'platform-management': '平台管理',
  users: '用户管理',
  roles: '角色管理',
  depts: '部门管理',
  'online-users': '在线用户',
  tenants: '租户管理',
  audit: '安全审计',
  settings: '系统管理',
  dicts: '字典管理',
  configs: '参数管理',
  notices: '公告管理',
  categories: '分类配置',
  'tenant-catalog': '租户套餐',
  resources: '菜单管理',
}

interface NavLink {
  id: string
  to: string
  label: string
  icon: any
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

  for (const node of nodes) {
    const routeKey = node.routeKey?.trim() || node.code?.trim() || ''
    const path = routeKey ? ROUTE_KEY_PATH_MAP[routeKey] : ''
    const children = buildLinks(node.children ?? [])
    if (!path && !children.length) {
      continue
    }
    if (path && usedPaths.has(path)) {
      continue
    }
    if (path) {
      usedPaths.add(path)
    }
    links.push({
      id: path || `menu-${node.id}`,
      to: path,
      label: titleMap[routeKey] || node.title,
      icon: iconMap[routeKey] || Tickets,
      children,
    })
  }
  return links
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

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  padding-left: 38px !important;
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
