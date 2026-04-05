import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { MenuItem, PermissionSnapshot } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'

const PUBLIC_ROUTES: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, title: '注册' },
  },
]

const DYNAMIC_ROUTE_DEFINITIONS: Record<string, RouteRecordRaw> = {
  dashboard: {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '运行总览', routeKey: 'dashboard' },
  },
  users: {
    path: 'system/users',
    name: 'users',
    component: () => import('@/views/UsersView.vue'),
    meta: { title: '用户管理', routeKey: 'users' },
  },
  roles: {
    path: 'system/roles',
    name: 'roles',
    component: () => import('@/views/RolesView.vue'),
    meta: { title: '角色管理', routeKey: 'roles' },
  },
  depts: {
    path: 'system/depts',
    name: 'depts',
    component: () => import('@/views/DepartmentsView.vue'),
    meta: { title: '部门管理', routeKey: 'depts' },
  },
  tenants: {
    path: 'system/tenants',
    name: 'tenants',
    component: () => import('@/views/TenantsView.vue'),
    meta: { title: '租户管理', routeKey: 'tenants' },
  },
  audit: {
    path: 'system/audit',
    name: 'audit',
    component: () => import('@/views/AuditView.vue'),
    meta: { title: '安全审计', routeKey: 'audit' },
  },
  settings: {
    path: 'system/settings',
    name: 'settings',
    component: () => import('@/views/SystemManagementView.vue'),
    meta: { title: '系统设置', routeKey: 'settings' },
  },
  'settings-dicts': {
    path: 'system/settings/dicts',
    name: 'settings-dicts',
    component: () => import('@/views/SystemDictsView.vue'),
    meta: { title: '字典管理', hidden: true, requiresGrant: 'system:read' },
  },
  'settings-configs': {
    path: 'system/settings/configs',
    name: 'settings-configs',
    component: () => import('@/views/SystemConfigsView.vue'),
    meta: { title: '参数配置', hidden: true, requiresGrant: 'system:read' },
  },
  'settings-notices': {
    path: 'system/settings/notices',
    name: 'settings-notices',
    component: () => import('@/views/SystemNoticesView.vue'),
    meta: { title: '公告管理', hidden: true, requiresGrant: 'system:read' },
  },
  'settings-categories': {
    path: 'system/settings/categories',
    name: 'settings-categories',
    component: () => import('@/views/SystemCategoriesView.vue'),
    meta: { title: '分类配置', hidden: true, requiresGrant: 'system:read' },
  },
  'tenant-catalog': {
    path: 'system/settings/tenant-catalog',
    name: 'tenant-catalog',
    component: () => import('@/views/TenantCatalogView.vue'),
    meta: { title: '租户目录', hidden: true, requiresGrant: 'tenant:read' },
  },
  'settings-resources': {
    path: 'system/settings/resources',
    name: 'settings-resources',
    component: () => import('@/views/ResourceManagementView.vue'),
    meta: { title: '菜单管理', hidden: true, requiresGrant: 'system:write' },
  },
}

const ROUTE_KEY_PATH_MAP: Record<string, string> = {
  dashboard: '/dashboard',
  users: '/system/users',
  roles: '/system/roles',
  depts: '/system/depts',
  tenants: '/system/tenants',
  audit: '/system/audit',
  settings: '/system/settings',
}

const SHELL_ROUTE: RouteRecordRaw = {
  path: '/',
  name: 'console-shell',
  component: () => import('@/layouts/ConsoleLayout.vue'),
  children: [
    {
      path: '',
      name: 'console-home',
      redirect: '/dashboard',
      meta: { hidden: true },
    },
    ...Object.values(DYNAMIC_ROUTE_DEFINITIONS),
  ],
}

const FALLBACK_ROUTE: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/views/NotFoundView.vue'),
  meta: { title: '页面未找到' },
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...PUBLIC_ROUTES, SHELL_ROUTE, FALLBACK_ROUTE],
  scrollBehavior() {
    return { top: 0, left: 0 }
  },
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    return true
  }

  if (!authStore.authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  try {
    if (!authStore.snapshot) {
      await authStore.bootstrapSnapshot()
    }
  } catch (error) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    if (status === 401) {
      authStore.clearSession()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    ElMessage.error('会话引导失败，请重试')
    return false
  }

  if (to.name === 'not-found') {
    return true
  }

  if (!isAllowedRoute(authStore.snapshot, to)) {
    const fallbackPath = resolveFirstAllowedPath(authStore.snapshot)
    if (fallbackPath && fallbackPath !== to.path) {
      return fallbackPath
    }
    ElMessage.error('当前账号暂无该页面访问权限')
    return false
  }

  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? 'Console')} | Enterprise Auth Platform`
})

function isAllowedRoute(snapshot: PermissionSnapshot | null, to: RouteLocationNormalized) {
  if (!snapshot) {
    return false
  }

  const grantSet = new Set(snapshot.grants ?? [])
  const requiredGrant = String(to.meta.requiresGrant ?? '').trim()
  if (requiredGrant && !grantSet.has(requiredGrant)) {
    return false
  }

  const routeKey = String(to.meta.routeKey ?? '').trim()
  if (!routeKey) {
    return true
  }

  const routeKeys = collectAllowedRouteKeys(snapshot.menus ?? [])
  return routeKeys.has(routeKey)
}

function resolveFirstAllowedPath(snapshot: PermissionSnapshot | null) {
  if (!snapshot) {
    return null
  }
  const routeKeys = collectAllowedRouteKeys(snapshot.menus ?? [])
  const menuList = flattenMenuTree(snapshot.menus ?? [])
  for (const menu of menuList) {
    const routeKey = menu.routeKey?.trim()
    if (!routeKey || !routeKeys.has(routeKey)) {
      continue
    }
    const path = ROUTE_KEY_PATH_MAP[routeKey]
    if (path) {
      return path
    }
  }
  return routeKeys.has('dashboard') ? '/dashboard' : null
}

function collectAllowedRouteKeys(menus: MenuItem[]) {
  const routeKeys = new Set<string>()
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      const routeKey = item.routeKey?.trim()
      if (routeKey) {
        if (Object.hasOwn(DYNAMIC_ROUTE_DEFINITIONS, routeKey)) {
          routeKeys.add(routeKey)
        } else {
          console.warn('[auth] 后端菜单快照中存在未知的路由键:', routeKey)
        }
      }
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(menus)
  return routeKeys
}

function flattenMenuTree(menus: MenuItem[]) {
  const result: MenuItem[] = []
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      result.push(item)
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(menus)
  return result
}

function registerDynamicRoutes(snapshot?: PermissionSnapshot | null) {
  if (!snapshot) {
    return
  }
  collectAllowedRouteKeys(snapshot.menus ?? [])
}

function clearDynamicRoutes() {
  // 静态路由注册，此处留空
}

export { registerDynamicRoutes, clearDynamicRoutes }
export default router
