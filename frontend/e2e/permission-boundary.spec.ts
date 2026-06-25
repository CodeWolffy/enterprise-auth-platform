import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { apiEnvelope, defaultSnapshot, fulfillJson, seedAuthSession } from './helpers'

async function mockPermissionApis(page: Page) {
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()

    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }

    if (url.pathname === '/api/users' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({
        total: 1,
        page: 1,
        size: 20,
        records: [{
          id: 11,
          tenantId: 'platform',
          username: 'readonly_user',
          displayName: 'Readonly User',
          enabled: true,
          roles: [],
        }],
      }))
      return
    }

    if (url.pathname === '/api/roles' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope([]))
      return
    }

    if (url.pathname === '/api/resources/tree' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope([
        {
          id: 1,
          resourceType: 'CATALOG',
          resourceKey: 'system',
          resourceName: 'System',
          orderNo: 10,
          visible: true,
          enabled: true,
          system: false,
          children: [],
        },
      ]))
      return
    }


    if (url.pathname === '/api/auth/logout' && method === 'POST') {
      await fulfillJson(route, 200, apiEnvelope({}))
      return
    }

    await fulfillJson(route, 200, apiEnvelope({}))
  })
}

test.describe('permission boundaries', () => {
  test('read-only accounts do not see write and export actions', async ({ page }) => {
    const snapshot = {
      ...defaultSnapshot(),
      superAdmin: false,
      roles: ['VIEWER'],
      grants: ['upms:sysuser:page', 'upms:sysrole:page', 'upms:sysdept:page', 'upms:systenant:page', 'upms:operationlog:page', 'upms:system:page'],
    }
    await seedAuthSession(page, snapshot)
    await mockPermissionApis(page)

    await page.goto('/system/users')
    await expect(page.locator('[data-testid="users-create"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="users-edit"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="users-delete"]')).toHaveCount(0)

    await page.goto('/system/resources')
    await expect(page.locator('[data-testid="resources-create-root"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="resources-edit"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="resources-delete"]')).toHaveCount(0)
  })
})
