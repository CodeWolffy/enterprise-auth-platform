import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import axios from 'axios'
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

const router = createRouter({
  history: createWebHistory(),
  routes: [...PUBLIC_ROUTES, SHELL_ROUTE],
  scrollBehavior() {
    return { top: 0, left: 0 }
  },
})

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
  } catch (error) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    if (status !== 401 && status !== 403) {
      ElMessage.error('会话加载失败，请稍后重试')
    }
    authStore.clearSession()
    return { name: 'login' }
  }

  if (to.path !== '/' && !isAllowedRoute(authStore.snapshot, to.path)) {
    const fallbackPath = authStore.snapshot?.menus[0]?.path
    if (to.path === '/dashboard' && fallbackPath && fallbackPath !== '/dashboard') {
      return { path: fallbackPath }
    }
    ElMessage.error('您没有权限访问该页面')
    return { path: fallbackPath || '/dashboard' }
  }

  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '控制台')} | 企业级权限管理平台`
})

function registerDynamicRoutes() {
  // Routes are now statically registered for stability.
}

function clearDynamicRoutes() {
  // Keep for backward compatibility with auth store API.
}

export { registerDynamicRoutes, clearDynamicRoutes }
export default router
