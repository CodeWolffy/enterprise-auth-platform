import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { PermissionSnapshot } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'

const PUBLIC_ROUTES: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '统一登录' },
  },
  {
    path: '/auth/callback',
    name: 'callback',
    component: () => import('@/views/AuthCallbackView.vue'),
    meta: { public: true, title: '登录回调' },
  },
]

const SHELL_ROUTE: RouteRecordRaw = {
  path: '/',
  name: 'console-shell',
  component: () => import('@/layouts/ConsoleLayout.vue'),
  children: [],
}

const DYNAMIC_ROUTE_DEFINITIONS: Record<string, RouteRecordRaw> = {
  dashboard: {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '运行总览' },
  },
  'oauth-clients': {
    path: 'oauth-clients',
    name: 'oauth-clients',
    component: () => import('@/views/OAuthClientsView.vue'),
    meta: { title: 'OAuth2 客户端' },
  },
  'oauth-scopes': {
    path: 'oauth-scopes',
    name: 'oauth-scopes',
    component: () => import('@/views/OAuthScopesView.vue'),
    meta: { title: 'OAuth2 作用域', hidden: true, requiresPermission: 'auth:read' },
  },
  users: {
    path: 'system/users',
    name: 'users',
    component: () => import('@/views/UsersView.vue'),
    meta: { title: '用户管理' },
  },
  roles: {
    path: 'system/roles',
    name: 'roles',
    component: () => import('@/views/RolesView.vue'),
    meta: { title: '角色管理' },
  },
  permissions: {
    path: 'system/permissions',
    name: 'permissions',
    component: () => import('@/views/PermissionsView.vue'),
    meta: { title: '权限管理' },
  },
  depts: {
    path: 'system/depts',
    name: 'depts',
    component: () => import('@/views/DepartmentsView.vue'),
    meta: { title: '部门管理' },
  },
  tenants: {
    path: 'system/tenants',
    name: 'tenants',
    component: () => import('@/views/TenantsView.vue'),
    meta: { title: '租户管理' },
  },
  audit: {
    path: 'system/audit',
    name: 'audit',
    component: () => import('@/views/AuditView.vue'),
    meta: { title: '安全审计' },
  },
  settings: {
    path: 'system/settings',
    name: 'settings',
    component: () => import('@/views/SystemManagementView.vue'),
    meta: { title: '系统管理' },
  },
  consents: {
    path: 'system/consents',
    name: 'consents',
    component: () => import('@/views/ConsentsView.vue'),
    meta: { title: '授权记录', hidden: true, requiresPermission: 'auth:read' },
  },
  'settings-dicts': {
    path: 'system/settings/dicts',
    name: 'settings-dicts',
    component: () => import('@/views/SystemDictsView.vue'),
    meta: { title: '字典管理', hidden: true, requiresPermission: 'system:read' },
  },
  'settings-configs': {
    path: 'system/settings/configs',
    name: 'settings-configs',
    component: () => import('@/views/SystemConfigsView.vue'),
    meta: { title: '参数管理', hidden: true, requiresPermission: 'system:read' },
  },
  'settings-notices': {
    path: 'system/settings/notices',
    name: 'settings-notices',
    component: () => import('@/views/SystemNoticesView.vue'),
    meta: { title: '公告管理', hidden: true, requiresPermission: 'system:read' },
  },
  'settings-categories': {
    path: 'system/settings/categories',
    name: 'settings-categories',
    component: () => import('@/views/SystemCategoriesView.vue'),
    meta: { title: '分类配置', hidden: true, requiresPermission: 'system:read' },
  },
  'tenant-catalog': {
    path: 'system/settings/tenant-catalog',
    name: 'tenant-catalog',
    component: () => import('@/views/TenantCatalogView.vue'),
    meta: { title: '租户套餐与能力', hidden: true, requiresPermission: 'tenant:read' },
  },
}

const dynamicRouteNames = new Set<string>()

function normalizeMenuPath(path: string | undefined) {
  if (!path) {
    return ''
  }
  return path.replace(/^\/+/, '')
}

function resolveRouteByMenu(menu: { code: string; path: string }) {
  const byCode = DYNAMIC_ROUTE_DEFINITIONS[menu.code]
  if (byCode) {
    return byCode
  }
  const normalizedPath = normalizeMenuPath(menu.path)
  return Object.values(DYNAMIC_ROUTE_DEFINITIONS).find((route) => normalizeMenuPath(String(route.path)) === normalizedPath)
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...PUBLIC_ROUTES, SHELL_ROUTE],
})

function clearDynamicRoutes() {
  for (const name of dynamicRouteNames) {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  }
  dynamicRouteNames.clear()
}

function registerDynamicRoutes(snapshot: PermissionSnapshot | null) {
  clearDynamicRoutes()
  if (!snapshot) {
    return
  }

  const menuCodes = new Set(snapshot.menus.map((item) => item.code))
  const menuPaths = new Set(snapshot.menus.map((item) => normalizeMenuPath(item.path)))
  for (const menu of snapshot.menus) {
    const route = resolveRouteByMenu(menu)
    if (!route) {
      continue
    }
    if (router.hasRoute(String(route.name))) {
      continue
    }
    router.addRoute('console-shell', route)
    dynamicRouteNames.add(String(route.name))
  }

  for (const [code, route] of Object.entries(DYNAMIC_ROUTE_DEFINITIONS)) {
    if (menuCodes.has(code) || menuPaths.has(normalizeMenuPath(String(route.path)))) {
      continue
    }
    const requiredPermission = route.meta?.requiresPermission as string | undefined
    if (requiredPermission && snapshot.permissions.includes(requiredPermission)) {
      if (router.hasRoute(String(route.name))) {
        continue
      }
      router.addRoute('console-shell', route)
      dynamicRouteNames.add(String(route.name))
    }
  }

  if (!router.hasRoute('console-home')) {
    router.addRoute('console-shell', {
      path: '',
      name: 'console-home',
      redirect: snapshot.menus[0]?.path || '/dashboard',
      meta: { hidden: true },
    })
    dynamicRouteNames.add('console-home')
  }
}

function isAllowedRoute(snapshot: PermissionSnapshot | null, path: string) {
  if (!snapshot) {
    return false
  }
  const allowedPaths = new Set(snapshot.menus.map((item) => item.path))
  if (allowedPaths.has(path)) {
    return true
  }
  if (snapshot.permissions.includes('auth:read') && ['/system/consents', '/oauth-scopes'].includes(path)) {
    return true
  }
  if (snapshot.permissions.includes('system:read') && path.startsWith('/system/settings/')) {
    return true
  }
  if (snapshot.permissions.includes('tenant:read') && path === '/system/settings/tenant-catalog') {
    return true
  }
  return false
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    return true
  }

  if (!authStore.accessToken) {
    return { name: 'login' }
  }

  try {
    if (!authStore.snapshot) {
      await authStore.bootstrapSnapshot()
    } else if (authStore.shouldRefreshToken()) {
      await authStore.refreshTokens()
    }
  } catch {
    ElMessage.error('登录态已失效，请重新登录')
    authStore.clearSession()
    clearDynamicRoutes()
    return { name: 'login' }
  }

  registerDynamicRoutes(authStore.snapshot)

  if (to.path !== '/' && !isAllowedRoute(authStore.snapshot, to.path)) {
    ElMessage.error('您没有权限访问该页面')
    return { path: authStore.snapshot?.menus[0]?.path || '/dashboard' }
  }

  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '控制台')} | 企业级权限管理平台`
})

export { registerDynamicRoutes, clearDynamicRoutes }
export default router
