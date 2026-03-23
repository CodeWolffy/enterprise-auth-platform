import type { Page, Route } from '@playwright/test'

export const AUTH_STORAGE_KEY = 'eap.frontend.auth'

export interface MockPermissionSnapshot {
  userId: number
  username: string
  tenantId: string
  roles: string[]
  permissions: string[]
  dataScopeType: string
  customDeptIds: number[]
  menus: Array<{
    code: string
    title: string
    path: string
    component: string
  }>
}

export function apiEnvelope<T>(data: T) {
  return {
    code: '200',
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
    roles: ['ADMIN'],
    permissions: ['auth:read', 'tenant:read', 'system:read'],
    dataScopeType: 'ALL',
    customDeptIds: [],
    menus: [
      { code: 'dashboard', title: '运行总览', path: '/dashboard', component: 'DashboardView' },
      { code: 'oauth-clients', title: 'OAuth2 客户端', path: '/oauth-clients', component: 'OAuthClientsView' },
      { code: 'users', title: '用户管理', path: '/system/users', component: 'UsersView' },
      { code: 'roles', title: '角色管理', path: '/system/roles', component: 'RolesView' },
      { code: 'permissions', title: '权限管理', path: '/system/permissions', component: 'PermissionsView' },
      { code: 'depts', title: '部门管理', path: '/system/depts', component: 'DepartmentsView' },
      { code: 'tenants', title: '租户管理', path: '/system/tenants', component: 'TenantsView' },
      { code: 'audit', title: '安全审计', path: '/system/audit', component: 'AuditView' },
      { code: 'settings', title: '系统管理', path: '/system/settings', component: 'SystemManagementView' },
    ],
  }
}

export async function seedAuthSession(page: Page, snapshot: MockPermissionSnapshot = defaultSnapshot()) {
  const now = Date.now()
  const payload = {
    accessToken: 'mock-access-token',
    refreshToken: 'mock-refresh-token',
    expiresAt: now + 60 * 60 * 1000,
    tenantId: snapshot.tenantId,
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
