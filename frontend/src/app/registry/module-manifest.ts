import {
  Avatar,
  Connection,
  Document,
  Flag,
  FolderOpened,
  Histogram,
  Message,
  Monitor,
  OfficeBuilding,
  Platform,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { PERMISSIONS, type PermissionCode } from './permissions'

const DEFAULT_ICON_NAME = 'Tickets'

export interface AppRouteManifest {
  routeKey?: string
  name: string
  path: string
  title: string
  component: RouteRecordRaw['component']
  requiredGrant?: PermissionCode
  hidden?: boolean
  icon?: string
}

interface AppNavManifest {
  code: string
  title: string
  icon?: string
}

interface MenuPresentationInput {
  code?: string | null
  routeKey?: string | null
  title?: string | null
  icon?: string | null
}

const APP_NAV_MANIFESTS = [
  {
    code: 'system',
    title: '系统管理',
    icon: 'Setting',
  },
  {
    code: 'platform-management',
    title: '平台管理',
    icon: 'Platform',
  },
] satisfies AppNavManifest[]

export const APP_ROUTE_MANIFESTS = [
  {
    routeKey: 'dashboard',
    path: 'dashboard',
    name: 'dashboard',
    title: '运行总览',
    icon: 'Monitor',
    requiredGrant: PERMISSIONS.DASHBOARD_READ,
    component: () => import('@/views/dashboard/DashboardView.vue'),
  },
  {
    routeKey: 'users',
    path: 'system/users',
    name: 'users',
    title: '用户管理',
    icon: 'Avatar',
    requiredGrant: PERMISSIONS.USER_READ,
    component: () => import('@/views/platform/UsersView.vue'),
  },
  {
    routeKey: 'roles',
    path: 'system/roles',
    name: 'roles',
    title: '角色管理',
    icon: 'Connection',
    requiredGrant: PERMISSIONS.ROLE_READ,
    component: () => import('@/views/platform/RolesView.vue'),
  },
  {
    routeKey: 'depts',
    path: 'system/depts',
    name: 'depts',
    title: '部门管理',
    icon: 'OfficeBuilding',
    requiredGrant: PERMISSIONS.DEPT_READ,
    component: () => import('@/views/platform/DepartmentsView.vue'),
  },
  {
    routeKey: 'online-users',
    path: 'system/online-users',
    name: 'online-users',
    title: '在线用户',
    icon: 'Monitor',
    requiredGrant: PERMISSIONS.SESSION_WRITE,
    component: () => import('@/views/audit/OnlineUsersView.vue'),
  },
  {
    routeKey: 'resources',
    path: 'system/resources',
    name: 'resources',
    title: '菜单管理',
    icon: 'Tickets',
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/platform/ResourceManagementView.vue'),
  },
  {
    routeKey: 'audit',
    path: 'system/audit',
    name: 'audit',
    title: '安全审计',
    icon: 'Histogram',
    requiredGrant: PERMISSIONS.AUDIT_READ,
    component: () => import('@/views/audit/AuditView.vue'),
  },
  {
    routeKey: 'operation-logs',
    path: 'system/operation-logs',
    name: 'operation-logs',
    title: '操作日志',
    icon: 'Document',
    requiredGrant: PERMISSIONS.OPERATION_LOG_READ,
    component: () => import('@/views/audit/OperationLogView.vue'),
  },
  {
    routeKey: 'tenants',
    path: 'platform/tenants',
    name: 'tenants',
    title: '租户管理',
    icon: 'Flag',
    requiredGrant: PERMISSIONS.TENANT_READ,
    component: () => import('@/views/platform/TenantsView.vue'),
  },
  {
    routeKey: 'tenant-catalog',
    path: 'platform/tenant-catalog',
    name: 'tenant-catalog',
    title: '租户套餐',
    icon: 'Tickets',
    requiredGrant: PERMISSIONS.TENANT_READ,
    component: () => import('@/views/platform/TenantCatalogView.vue'),
  },
  {
    routeKey: 'files',
    path: 'platform/files',
    name: 'files',
    title: '文件管理',
    icon: 'FolderOpened',
    requiredGrant: PERMISSIONS.FILE_READ,
    component: () => import('@/views/platform/FileManagementView.vue'),
  },
  {
    routeKey: 'dicts',
    path: 'platform/dicts',
    name: 'dicts',
    title: '字典管理',
    icon: 'Tickets',
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemDictsView.vue'),
  },
  {
    routeKey: 'configs',
    path: 'platform/configs',
    name: 'configs',
    title: '参数管理',
    icon: 'Setting',
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemConfigsView.vue'),
  },
  {
    routeKey: 'mail-channel',
    path: 'platform/mail-channel',
    name: 'mail-channel',
    title: '邮件配置',
    icon: 'Message',
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/MailChannelView.vue'),
  },
  {
    routeKey: 'notices',
    path: 'platform/notices',
    name: 'notices',
    title: '公告管理',
    icon: 'Tickets',
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemNoticesView.vue'),
  },
  {
    routeKey: 'categories',
    path: 'platform/categories',
    name: 'categories',
    title: '分类配置',
    icon: 'Tickets',
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemCategoriesView.vue'),
  },
  {
    routeKey: 'settings',
    path: 'system/settings',
    name: 'settings',
    title: '系统设置',
    icon: 'Setting',
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemManagementView.vue'),
  },
  {
    path: 'system/settings/dicts',
    name: 'settings-dicts',
    title: '字典管理',
    hidden: true,
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemDictsView.vue'),
  },
  {
    path: 'system/settings/configs',
    name: 'settings-configs',
    title: '参数配置',
    hidden: true,
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemConfigsView.vue'),
  },
  {
    path: 'system/settings/mail-channel',
    name: 'settings-mail-channel',
    title: '邮件配置',
    hidden: true,
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/MailChannelView.vue'),
  },
  {
    path: 'system/settings/notices',
    name: 'settings-notices',
    title: '公告管理',
    hidden: true,
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemNoticesView.vue'),
  },
  {
    path: 'system/settings/categories',
    name: 'settings-categories',
    title: '分类配置',
    hidden: true,
    requiredGrant: PERMISSIONS.SYSTEM_READ,
    component: () => import('@/views/system/SystemCategoriesView.vue'),
  },
  {
    path: 'system/settings/resources',
    name: 'settings-resources',
    title: '菜单管理',
    hidden: true,
    requiredGrant: PERMISSIONS.SYSTEM_WRITE,
    component: () => import('@/views/platform/ResourceManagementView.vue'),
  },
] satisfies AppRouteManifest[]

const APP_ROUTE_KEY_MANIFESTS = APP_ROUTE_MANIFESTS.filter(
  (manifest): manifest is AppRouteManifest & { routeKey: string } => Boolean(manifest.routeKey),
)

const ROUTE_KEY_MANIFEST_MAP: Record<string, AppRouteManifest & { routeKey: string }> = Object.fromEntries(
  APP_ROUTE_KEY_MANIFESTS.map((manifest) => [manifest.routeKey, manifest]),
)

const NAV_CODE_MANIFEST_MAP: Record<string, AppNavManifest> = Object.fromEntries(
  APP_NAV_MANIFESTS.map((manifest) => [manifest.code, manifest]),
)

const APP_ICON_COMPONENTS: Record<string, Component> = {
  Avatar,
  Connection,
  Document,
  Flag,
  FolderOpened,
  Histogram,
  Message,
  Monitor,
  OfficeBuilding,
  Platform,
  Setting,
  Tickets,
}

export const APP_ROUTE_DEFINITIONS: Record<string, RouteRecordRaw> = Object.fromEntries(
  APP_ROUTE_MANIFESTS.map((manifest) => [
    manifest.name,
    {
      path: manifest.path,
      name: manifest.name,
      component: manifest.component,
      meta: {
        title: manifest.title,
        routeKey: manifest.routeKey,
        requiresGrant: manifest.requiredGrant,
        hidden: manifest.hidden,
        icon: manifest.icon,
      },
    },
  ]),
)

export const ROUTE_KEY_PATH_MAP: Record<string, string> = Object.fromEntries(
  APP_ROUTE_KEY_MANIFESTS.map((manifest) => [manifest.routeKey, `/${manifest.path}`]),
)

export function resolveRouteManifest(routeKey?: string | null) {
  const normalizedRouteKey = normalizeValue(routeKey)
  return normalizedRouteKey ? ROUTE_KEY_MANIFEST_MAP[normalizedRouteKey] : undefined
}

export function resolveMenuPresentation(menu: MenuPresentationInput) {
  const routeManifest = resolveRouteManifest(menu.routeKey)
  if (routeManifest) {
    return {
      title: routeManifest.title,
      icon: routeManifest.icon ?? normalizeValue(menu.icon) ?? DEFAULT_ICON_NAME,
    }
  }

  const normalizedCode = normalizeValue(menu.code)
  const navManifest = normalizedCode ? NAV_CODE_MANIFEST_MAP[normalizedCode] : undefined

  return {
    title: navManifest?.title ?? normalizeValue(menu.title) ?? '',
    icon: navManifest?.icon ?? normalizeValue(menu.icon) ?? DEFAULT_ICON_NAME,
  }
}

export function resolveAppIcon(icon?: string | null) {
  const normalizedIcon = normalizeValue(icon) ?? DEFAULT_ICON_NAME
  return APP_ICON_COMPONENTS[normalizedIcon] ?? APP_ICON_COMPONENTS[DEFAULT_ICON_NAME]
}

function normalizeValue(value?: string | null) {
  const normalized = value?.trim()
  return normalized ? normalized : undefined
}
