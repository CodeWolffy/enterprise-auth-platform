import { expect, test, type Page, type Route } from '@playwright/test'
import { apiEnvelope, defaultSnapshot, fulfillJson, seedAuthSession } from './helpers'

const snapshot = defaultSnapshot()

const navCases = [
  { path: '/system/users', name: 'menu-users' },
  { path: '/system/roles', name: 'menu-roles' },
  { path: '/system/depts', name: 'menu-departments' },
]

const systemCases = [
  { path: '/system/settings', name: 'system-workbench' },
  { path: '/system/settings/dicts', name: 'system-dicts' },
  { path: '/system/settings/configs', name: 'system-configs' },
  { path: '/system/settings/notices', name: 'system-notices' },
  { path: '/system/settings/categories', name: 'system-categories' },
]

function noAnimationCss() {
  return `
    *, *::before, *::after {
      transition-duration: 0s !important;
      animation-duration: 0s !important;
      animation-delay: 0s !important;
      caret-color: transparent !important;
    }
  `
}

async function mockApis(page: Page) {
  await page.route('**/*', async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()

    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }

    if (url.pathname === '/api/auth/me' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope(snapshot))
      return
    }

    if (url.pathname === '/api/system/features' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope({
          gatewayEnabled: true,
          nacosEnabled: true,
          mqEnabled: false,
          seataEnabled: false,
          jobEnabled: true,
          lokiEnabled: true,
        }),
      )
      return
    }

    if (url.pathname === '/api/system/categories' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope({
          dict: [{ code: 'auth', name: 'Auth', matchers: ['auth.*'] }],
          config: [{ code: 'security', name: 'Security', matchers: ['security.*'] }],
        }),
      )
      return
    }

    if (url.pathname === '/api/system/categories/dict' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope([{ code: 'auth', name: 'Auth', matchers: ['auth.*', 'oauth.*'] }]))
      return
    }

    if (url.pathname === '/api/system/categories/config' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope([{ code: 'security', name: 'Security', matchers: ['security.*'] }]))
      return
    }

    if (/^\/api\/system\/categories\/(dict|config)\/[^/]+\/analysis$/.test(url.pathname) && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope({
          code: 'auth',
          name: 'Auth',
          targetType: 'dict',
          matchers: ['auth.*', 'oauth.*'],
          referenceCount: 4,
          sampleReferences: ['oauth.login.mode', 'auth.password.policy'],
          recentAudits: [
            {
              eventType: 'CATEGORY_UPDATED',
              operator: 'admin',
              occurredAt: 1774087200000,
              payloadJson: '{"field":"matchers"}',
            },
          ],
          trend: [
            { date: '2026-03-15', count: 1 },
            { date: '2026-03-16', count: 2 },
            { date: '2026-03-17', count: 2 },
            { date: '2026-03-18', count: 3 },
            { date: '2026-03-19', count: 4 },
            { date: '2026-03-20', count: 4 },
            { date: '2026-03-21', count: 4 },
          ],
        }),
      )
      return
    }

    if (url.pathname === '/api/system/dicts' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope({
          total: 2,
          page: 1,
          size: 10,
          records: [
            { id: 1, category: 'auth', dictType: 'login_mode', dictCode: 'password', dictValue: 'password', createdBy: 'admin' },
            { id: 2, category: 'auth', dictType: 'login_mode', dictCode: 'sms', dictValue: 'sms', createdBy: 'admin' },
          ],
        }),
      )
      return
    }

    if (url.pathname === '/api/system/configs' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope({
          total: 2,
          page: 1,
          size: 10,
          records: [
            { id: 1, category: 'security', configKey: 'session.timeout', configName: 'timeout', configValue: '30', createdBy: 'admin' },
            { id: 2, category: 'security', configKey: 'pwd.retry', configName: 'retry', configValue: '5', createdBy: 'admin' },
          ],
        }),
      )
      return
    }

    if (url.pathname === '/api/system/notices' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope({
          total: 2,
          page: 1,
          size: 10,
          records: [
            {
              id: 1,
              noticeTitle: 'maintenance',
              noticeContent: 'night maintenance',
              published: true,
              publishTime: 1773997200000,
              workflowStatus: 'PUBLISHED',
              createdBy: 'admin',
            },
            {
              id: 2,
              noticeTitle: 'release',
              noticeContent: 'new version',
              published: false,
              publishTime: null,
              workflowStatus: 'DRAFT',
              createdBy: 'admin',
            },
          ],
        }),
      )
      return
    }

    if (url.pathname === '/api/users' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope({
          total: 2,
          page: 1,
          size: 10,
          records: [
            {
              id: 1,
              tenantId: 'platform',
              username: 'admin',
              displayName: 'Admin',
              mobile: '13800000000',
              email: 'admin@example.com',
              deptId: 1,
              enabled: true,
              roles: ['ADMIN'],
              permissions: ['*'],
              dataScopeType: 'ALL',
            },
            {
              id: 2,
              tenantId: 'platform',
              username: 'operator',
              displayName: 'Ops',
              mobile: '13900000000',
              email: 'ops@example.com',
              deptId: 2,
              enabled: true,
              roles: ['OPS'],
              permissions: ['audit:read'],
              dataScopeType: 'DEPT',
            },
          ],
        }),
      )
      return
    }

    if (url.pathname === '/api/roles' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope([
          { id: 1, code: 'ADMIN', name: 'Admin', description: 'Admin role', dataScopeType: 'ALL', customDeptIds: [] },
          { id: 2, code: 'OPS', name: 'Ops', description: 'Ops role', dataScopeType: 'DEPT', customDeptIds: [] },
        ]),
      )
      return
    }

    if (url.pathname === '/api/permissions' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope([
          {
            id: 1,
            permissionName: 'Read User',
            permissionCode: 'user:read',
            resourceCode: 'user',
            actionCode: 'read',
            scopeCode: 'platform',
          },
          {
            id: 2,
            permissionName: 'Write User',
            permissionCode: 'user:write',
            resourceCode: 'user',
            actionCode: 'write',
            scopeCode: 'platform',
          },
        ]),
      )
      return
    }

    if (url.pathname === '/api/depts' && method === 'GET') {
      await fulfillJson(
        route,
        200,
        apiEnvelope([
          { id: 1, code: 'HQ', name: 'HQ', parentId: null, leaderUserId: 1 },
          { id: 2, code: 'OPS', name: 'OPS', parentId: 1, leaderUserId: 2 },
        ]),
      )
      return
    }

    await fulfillJson(route, 200, apiEnvelope({}))
  })
}

async function captureCases(page: Page, cases: Array<{ path: string; name: string }>, suffix: string) {
  for (const item of cases) {
    await page.goto(item.path)
    await expect(page.locator('.console-content .panel-stack').first()).toBeVisible()
    await expect(page.locator('.panel-head h3').first()).toBeVisible()
    await expect(page).toHaveScreenshot(`${item.name}-${suffix}.png`, { fullPage: true, maxDiffPixelRatio: 0.02 })
  }
}

test.describe('visual-baseline', () => {
  test.beforeEach(async ({ page }) => {
    await seedAuthSession(page)
    await mockApis(page)
    await page.addStyleTag({ content: noAnimationCss() })
  })

  test('desktop-nav-and-pages', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 })
    await captureCases(page, navCases, 'desktop')
    await captureCases(page, systemCases, 'desktop')
  })

  test('mobile-nav-and-pages', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await captureCases(page, navCases, 'mobile')
    await captureCases(page, systemCases, 'mobile')
  })
})
