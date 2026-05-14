import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { PermissionSnapshot } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'
import { collectAllowedRouteKeys, isAllowedRoute, resolveFirstAllowedPath } from './route-access'

const PUBLIC_ROUTES: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true, title: '注册' },
  },
]

const DYNAMIC_ROUTE_DEFINITIONS: Record<string, RouteRecordRaw> = {
  dashboard: {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '运行总览', routeKey: 'dashboard' },
  },
  users: {
    path: 'system/users',
    name: 'users',
    component: () => import('@/views/platform/UsersView.vue'),
    meta: { title: '用户管理', routeKey: 'users', requiresGrant: 'user:read' },
  },
  roles: {
    path: 'system/roles',
    name: 'roles',
    component: () => import('@/views/platform/RolesView.vue'),
    meta: { title: '角色管理', routeKey: 'roles', requiresGrant: 'role:read' },
  },
  depts: {
    path: 'system/depts',
    name: 'depts',
    component: () => import('@/views/platform/DepartmentsView.vue'),
    meta: { title: '部门管理', routeKey: 'depts', requiresGrant: 'dept:read' },
  },
  tenants: {
    path: 'platform/tenants',
    name: 'tenants',
    component: () => import('@/views/platform/TenantsView.vue'),
    meta: { title: '租户管理', routeKey: 'tenants', requiresGrant: 'tenant:read' },
  },
  'online-users': {
    path: 'system/online-users',
    name: 'online-users',
    component: () => import('@/views/audit/OnlineUsersView.vue'),
    meta: { title: '在线用户', routeKey: 'online-users', requiresGrant: 'session:write' },
  },
  audit: {
    path: 'system/audit',
    name: 'audit',
    component: () => import('@/views/audit/AuditView.vue'),
    meta: { title: '安全审计', routeKey: 'audit', requiresGrant: 'audit:read' },
  },
  settings: {
    path: 'system/settings',
    name: 'settings',
    component: () => import('@/views/system/SystemManagementView.vue'),
    meta: { title: '系统设置', routeKey: 'settings', requiresGrant: 'system:read' },
  },
  'settings-dicts': {
    path: 'system/settings/dicts',
    name: 'settings-dicts',
    component: () => import('@/views/system/SystemDictsView.vue'),
    meta: { title: '字典管理', hidden: true, requiresGrant: 'system:read' },
  },
  dicts: {
    path: 'platform/dicts',
    name: 'dicts',
    component: () => import('@/views/system/SystemDictsView.vue'),
    meta: { title: '字典管理', routeKey: 'dicts', requiresGrant: 'system:read' },
  },
  'settings-configs': {
    path: 'system/settings/configs',
    name: 'settings-configs',
    component: () => import('@/views/system/SystemConfigsView.vue'),
    meta: { title: '参数配置', hidden: true, requiresGrant: 'system:read' },
  },
  configs: {
    path: 'platform/configs',
    name: 'configs',
    component: () => import('@/views/system/SystemConfigsView.vue'),
    meta: { title: '参数管理', routeKey: 'configs', requiresGrant: 'system:read' },
  },
  'settings-notices': {
    path: 'system/settings/notices',
    name: 'settings-notices',
    component: () => import('@/views/system/SystemNoticesView.vue'),
    meta: { title: '公告管理', hidden: true, requiresGrant: 'system:read' },
  },
  notices: {
    path: 'platform/notices',
    name: 'notices',
    component: () => import('@/views/system/SystemNoticesView.vue'),
    meta: { title: '公告管理', routeKey: 'notices', requiresGrant: 'system:read' },
  },
  'settings-categories': {
    path: 'system/settings/categories',
    name: 'settings-categories',
    component: () => import('@/views/system/SystemCategoriesView.vue'),
    meta: { title: '分类配置', hidden: true, requiresGrant: 'system:read' },
  },
  categories: {
    path: 'platform/categories',
    name: 'categories',
    component: () => import('@/views/system/SystemCategoriesView.vue'),
    meta: { title: '分类配置', routeKey: 'categories', requiresGrant: 'system:read' },
  },
  'tenant-catalog': {
    path: 'platform/tenant-catalog',
    name: 'tenant-catalog',
    component: () => import('@/views/platform/TenantCatalogView.vue'),
    meta: { title: '租户套餐', routeKey: 'tenant-catalog', requiresGrant: 'tenant:read' },
  },
  'settings-resources': {
    path: 'system/settings/resources',
    name: 'settings-resources',
    component: () => import('@/views/platform/ResourceManagementView.vue'),
    meta: { title: '菜单管理', hidden: true, requiresGrant: 'system:write' },
  },
  resources: {
    path: 'system/resources',
    name: 'resources',
    component: () => import('@/views/platform/ResourceManagementView.vue'),
    meta: { title: '菜单管理', routeKey: 'resources', requiresGrant: 'system:read' },
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
