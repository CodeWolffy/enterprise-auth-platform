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
    meta: { title: 'Dashboard' },
  },
  users: {
    path: 'system/users',
    name: 'users',
    component: () => import('@/views/UsersView.vue'),
    meta: { title: 'Users' },
  },
  roles: {
    path: 'system/roles',
    name: 'roles',
    component: () => import('@/views/RolesView.vue'),
    meta: { title: 'Roles' },
  },
  depts: {
    path: 'system/depts',
    name: 'depts',
    component: () => import('@/views/DepartmentsView.vue'),
    meta: { title: 'Departments' },
  },
  tenants: {
    path: 'system/tenants',
    name: 'tenants',
    component: () => import('@/views/TenantsView.vue'),
    meta: { title: 'Tenants' },
  },
  audit: {
    path: 'system/audit',
    name: 'audit',
    component: () => import('@/views/AuditView.vue'),
    meta: { title: 'Audit' },
  },
  settings: {
    path: 'system/settings',
    name: 'settings',
    component: () => import('@/views/SystemManagementView.vue'),
    meta: { title: 'System Settings' },
  },
  'settings-dicts': {
    path: 'system/settings/dicts',
    name: 'settings-dicts',
    component: () => import('@/views/SystemDictsView.vue'),
    meta: { title: 'Dictionaries', hidden: true, requiresPermission: 'system:read' },
  },
  'settings-configs': {
    path: 'system/settings/configs',
    name: 'settings-configs',
    component: () => import('@/views/SystemConfigsView.vue'),
    meta: { title: 'Configurations', hidden: true, requiresPermission: 'system:read' },
  },
  'settings-notices': {
    path: 'system/settings/notices',
    name: 'settings-notices',
    component: () => import('@/views/SystemNoticesView.vue'),
    meta: { title: 'Notices', hidden: true, requiresPermission: 'system:read' },
  },
  'settings-categories': {
    path: 'system/settings/categories',
    name: 'settings-categories',
    component: () => import('@/views/SystemCategoriesView.vue'),
    meta: { title: 'Categories', hidden: true, requiresPermission: 'system:read' },
  },
  'tenant-catalog': {
    path: 'system/settings/tenant-catalog',
    name: 'tenant-catalog',
    component: () => import('@/views/TenantCatalogView.vue'),
    meta: { title: 'Tenant Catalog', hidden: true, requiresPermission: 'tenant:read' },
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

const FALLBACK_ROUTE: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/views/NotFoundView.vue'),
  meta: { title: '页面未找到' },
}

function isAllowedRoute(snapshot: PermissionSnapshot | null, path: string) {
  if (!snapshot) {
    return false
  }
  const allowedPaths = new Set(snapshot.menus.map((item) => item.path))
  if (allowedPaths.has(path)) {
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

function resolveFirstAllowedPath(snapshot: PermissionSnapshot | null) {
  const firstMenuPath = snapshot?.menus?.[0]?.path
  return firstMenuPath && firstMenuPath.startsWith('/') ? firstMenuPath : null
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
    ElMessage.error('Session bootstrap failed, please retry')
    return false
  }

  if (to.name === 'not-found') {
    return true
  }

  if (to.path !== '/' && !isAllowedRoute(authStore.snapshot, to.path)) {
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

function registerDynamicRoutes(_snapshot?: PermissionSnapshot | null) {
  // Routes are statically registered for stability.
}

function clearDynamicRoutes() {
  // Keep for backward compatibility with auth store API.
}

export { registerDynamicRoutes, clearDynamicRoutes }
export default router
