import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { PermissionSnapshot } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'
import { redirectToAuthorizationPage } from '@/utils/authRedirect'

const PUBLIC_ROUTES: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: 'Sign In' },
  },
  {
    path: '/auth/callback',
    name: 'callback',
    component: () => import('@/views/AuthCallbackView.vue'),
    meta: { public: true, title: 'Authorization Callback' },
  },
  {
    path: '/auth/consent',
    name: 'consent-ui',
    component: () => import('@/views/AuthConsentView.vue'),
    meta: { public: true, title: 'Authorization Consent' },
  },
]

const DYNAMIC_ROUTE_DEFINITIONS: Record<string, RouteRecordRaw> = {
  dashboard: {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: 'Dashboard' },
  },
  'oauth-clients': {
    path: 'oauth-clients',
    name: 'oauth-clients',
    component: () => import('@/views/OAuthClientsView.vue'),
    meta: { title: 'OAuth2 Clients' },
  },
  'oauth-scopes': {
    path: 'oauth-scopes',
    name: 'oauth-scopes',
    component: () => import('@/views/OAuthScopesView.vue'),
    meta: { title: 'OAuth2 Scopes', hidden: true, requiresPermission: 'auth:read' },
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
  permissions: {
    path: 'system/permissions',
    name: 'permissions',
    component: () => import('@/views/PermissionsView.vue'),
    meta: { title: 'Permissions' },
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
  consents: {
    path: 'system/consents',
    name: 'consents',
    component: () => import('@/views/ConsentsView.vue'),
    meta: { title: 'Authorization Records', hidden: true, requiresPermission: 'auth:read' },
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
  const tenantId = authStore.operatorTenantId || authStore.tenantId || 'platform'

  if (to.meta.public) {
    return true
  }

  if (!authStore.accessToken) {
    await redirectToAuthorizationPage(tenantId)
    return false
  }

  try {
    if (!authStore.snapshot) {
      await authStore.bootstrapSnapshot()
    } else if (authStore.shouldRefreshToken()) {
      await authStore.refreshTokens()
    }
  } catch (error) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    const code = axios.isAxiosError(error) ? error.response?.data?.code : undefined
    const shouldClearSession = status === 401
      || (status === 403 && [
        'SESSION_EXPIRED',
        'SESSION_NOT_FOUND',
        'INVALID_TOKEN',
        'TOKEN_VERSION_MISMATCH',
        'TENANT_MISMATCH',
        'USER_DISABLED',
        'SESSION_SUBJECT_MISMATCH',
        'ACCESS_TOKEN_TYPE_INVALID',
      ].includes(String(code ?? '')))
    if (!shouldClearSession) {
      ElMessage.error('Session bootstrap failed, please retry')
      return false
    }
    authStore.clearSession()
    await redirectToAuthorizationPage(tenantId)
    return false
  }

  if (to.path !== '/' && !isAllowedRoute(authStore.snapshot, to.path)) {
    authStore.clearSession()
    await redirectToAuthorizationPage(tenantId)
    return false
  }

  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? 'Console')} | Enterprise Auth Platform`
})

function registerDynamicRoutes() {
  // Routes are statically registered for stability.
}

function clearDynamicRoutes() {
  // Keep for backward compatibility with auth store API.
}

export { registerDynamicRoutes, clearDynamicRoutes }
export default router
