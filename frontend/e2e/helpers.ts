import type { Page, Route } from '@playwright/test'

export const AUTH_STORAGE_KEY = 'eap.frontend.auth'

export interface MockMenuItem {
  id: number
  code: string
  title: string
  path: string
  component: string
  routeKey?: string
  icon?: string
  children?: MockMenuItem[]
}

export interface MockPermissionSnapshot {
  userId: number
  username: string
  tenantId: string
  operatorTenantId?: string
  roles: string[]
  grants: string[]
  dataScopeType: string
  customDeptIds: number[]
  superAdmin?: boolean
  menus: MockMenuItem[]
}

export function apiEnvelope<T>(data: T) {
  return {
    code: 'OK',
    success: true,
    data,
    message: 'ok',
  }
}

export function defaultSnapshot(): MockPermissionSnapshot {
  return {
    userId: 1,
    username: 'admin',
    tenantId: 'platform',
    operatorTenantId: 'platform',
    roles: ['ADMIN'],
    grants: ['auth:read', 'user:read', 'role:read', 'dept:read', 'tenant:read', 'audit:read', 'system:read', 'session:write'],
    dataScopeType: 'ALL',
    customDeptIds: [],
    superAdmin: true,
    menus: [
      { id: 10, code: 'dashboard', title: '运行总览', path: '/dashboard', component: 'DashboardView', routeKey: 'dashboard' },
      {
        id: 20,
        code: 'system',
        title: '系统管理',
        path: '',
        component: '',
        icon: 'Setting',
        children: [
          { id: 21, code: 'users', title: '用户管理', path: '/system/users', component: 'UsersView', routeKey: 'users' },
          { id: 22, code: 'roles', title: '角色管理', path: '/system/roles', component: 'RolesView', routeKey: 'roles' },
          { id: 23, code: 'depts', title: '部门管理', path: '/system/depts', component: 'DepartmentsView', routeKey: 'depts' },
          { id: 27, code: 'online-users', title: '在线用户', path: '/system/online-users', component: 'OnlineUsersView', routeKey: 'online-users' },
          { id: 28, code: 'resources', title: '菜单管理', path: '/system/resources', component: 'ResourceManagementView', routeKey: 'resources' },
        ],
      },
      {
        id: 30,
        code: 'platform-management',
        title: '平台管理',
        path: '',
        component: '',
        icon: 'Platform',
        children: [
          { id: 31, code: 'dicts', title: '字典管理', path: '/platform/dicts', component: 'SystemDictsView', routeKey: 'dicts' },
          { id: 24, code: 'tenants', title: '租户管理', path: '/platform/tenants', component: 'TenantsView', routeKey: 'tenants' },
          { id: 32, code: 'tenant-catalog', title: '租户套餐', path: '/platform/tenant-catalog', component: 'TenantCatalogView', routeKey: 'tenant-catalog' },
          { id: 33, code: 'configs', title: '参数管理', path: '/platform/configs', component: 'SystemConfigsView', routeKey: 'configs' },
          { id: 34, code: 'notices', title: '公告管理', path: '/platform/notices', component: 'SystemNoticesView', routeKey: 'notices' },
          { id: 35, code: 'categories', title: '分类配置', path: '/platform/categories', component: 'SystemCategoriesView', routeKey: 'categories' },
        ],
      },
      { id: 25, code: 'audit', title: '安全审计', path: '/system/audit', component: 'AuditView', routeKey: 'audit' },
    ],
  }
}

export async function seedAuthSession(page: Page, snapshot: MockPermissionSnapshot | null = defaultSnapshot()) {
  const now = Date.now()
  const resolvedTenantId = snapshot?.tenantId ?? 'platform'
  const payload = {
    authenticated: true,
    token: 'e2e-token',
    expiresAt: now + 60 * 60 * 1000,
    tenantId: resolvedTenantId,
    operatorTenantId: snapshot?.operatorTenantId ?? resolvedTenantId,
    snapshot,
  }
  await page.addInitScript(
    ({ key, value }) => {
      window.sessionStorage.setItem(key, JSON.stringify(value))
    },
    { key: AUTH_STORAGE_KEY, value: payload },
  )
}

export async function fulfillJson(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    headers: {
      'access-control-allow-origin': '*',
      'access-control-allow-headers': '*',
      'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
    },
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

export async function fulfillImage(route: Route, body: string, contentType: string, headers: Record<string, string> = {}) {
  await route.fulfill({
    status: 200,
    headers: {
      'access-control-allow-origin': '*',
      'access-control-allow-headers': '*',
      'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
      'content-type': contentType,
      ...headers,
    },
    body,
  })
}
