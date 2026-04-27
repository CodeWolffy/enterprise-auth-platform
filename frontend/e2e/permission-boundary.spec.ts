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

    if (url.pathname === '/api/audit/events' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({
        total: 1,
        page: 1,
        size: 20,
        records: [{
          type: 'LOGIN_SUCCESS',
          operator: 'admin',
          tenantId: 'platform',
          requestId: 'req-permission',
          clientIp: '127.0.0.1',
          occurredAt: Date.now(),
          details: {},
        }],
      }))
      return
    }

    if (url.pathname === '/api/audit/exports/policy' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({ retentionDays: 7, maxTasks: 100 }))
      return
    }

    if (url.pathname === '/api/audit/exports' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({
        total: 1,
        page: 1,
        size: 10,
        records: [{
          id: 101,
          tenantId: 'platform',
          operator: 'admin',
          status: 'SUCCESS',
          archived: false,
          archivable: true,
          fileName: 'audit-101.xlsx',
          recordCount: 12,
          progressPercent: 100,
          progressStage: 'completed',
          retentionExpired: false,
          retentionSummary: '7 days',
          expiresAt: '2099-01-01T00:00:00',
          requestedAt: '2026-03-20T10:00:00',
          completedAt: '2026-03-20T10:02:00',
          errorMessage: null,
        }],
      }))
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
      grants: ['auth:read', 'user:read', 'role:read', 'dept:read', 'tenant:read', 'audit:read', 'system:read'],
    }
    await seedAuthSession(page, snapshot)
    await mockPermissionApis(page)

    await page.goto('/system/users')
    await expect(page.locator('[data-testid="users-create"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="users-edit"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="users-delete"]')).toHaveCount(0)

    await page.goto('/system/audit')
    await expect(page.locator('[data-testid="audit-export-current"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="audit-export-async"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="audit-task-archive"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="audit-task-delete"]')).toHaveCount(0)

    await page.goto('/system/resources')
    await expect(page.locator('[data-testid="resources-create-root"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="resources-edit"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="resources-delete"]')).toHaveCount(0)
  })
})
