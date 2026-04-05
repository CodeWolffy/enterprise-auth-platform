import type { Page, Route } from '@playwright/test'

export const AUTH_STORAGE_KEY = 'eap.frontend.auth'

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
  menus: Array<{
    id: number
    code: string
    title: string
    path: string
    component: string
    routeKey?: string
  }>
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
    grants: ['auth:read', 'user:read', 'role:read', 'dept:read', 'tenant:read', 'audit:read', 'system:read'],
    dataScopeType: 'ALL',
    customDeptIds: [],
    superAdmin: true,
    menus: [
      { id: 10, code: 'dashboard', title: '运行总览', path: '/dashboard', component: 'DashboardView', routeKey: 'dashboard' },
      { id: 21, code: 'users', title: '用户管理', path: '/system/users', component: 'UsersView', routeKey: 'users' },
      { id: 22, code: 'roles', title: '角色管理', path: '/system/roles', component: 'RolesView', routeKey: 'roles' },
      { id: 23, code: 'depts', title: '部门管理', path: '/system/depts', component: 'DepartmentsView', routeKey: 'depts' },
      { id: 24, code: 'tenants', title: '租户管理', path: '/system/tenants', component: 'TenantsView', routeKey: 'tenants' },
      { id: 25, code: 'audit', title: '安全审计', path: '/system/audit', component: 'AuditView', routeKey: 'audit' },
      { id: 26, code: 'settings', title: '系统管理', path: '/system/settings', component: 'SystemManagementView', routeKey: 'settings' },
    ],
  }
}

export async function seedAuthSession(page: Page, snapshot: MockPermissionSnapshot | null = defaultSnapshot()) {
  const now = Date.now()
  const resolvedTenantId = snapshot?.tenantId ?? 'platform'
  const payload = {
    authenticated: true,
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
